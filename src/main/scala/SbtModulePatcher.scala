import sbt._
import sbt.Keys._
import scala.util.Using
import scala.util.control.NonFatal
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import java.util.concurrent.Executors
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.jar._
import scala.collection.JavaConverters._
import java.security.MessageDigest
import java.nio.file.StandardOpenOption.{CREATE, WRITE}
import java.util.concurrent.locks.ReentrantLock
import scala.concurrent.Await

object SbtModulePatcher extends AutoPlugin {
  private val SBT_MODULE_PATCHER_VERSION_KEY = "Sbt-Module-Patcher-Version"
  private val SBT_MODULE_PATCHER_VERSION_VALUE = "2"
  private val BACKUP_SUFFIX = ".backup"
  private val jarLocks = new java.util.concurrent.ConcurrentHashMap[String, ReentrantLock]()

  // Thread pool for parallel processing
  private implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(
    Executors.newFixedThreadPool(
      Runtime.getRuntime.availableProcessors()
    )
  )

  object autoImport {
    val patchDependencies = taskKey[Unit]("Patch compile dependencies")
    val modulePatcherVerifyChecksums = settingKey[Boolean]("Verify checksums")
    val modulePatcherParallel = settingKey[Boolean]("Enable parallel processing")
    val modulePatcherTimeout = settingKey[Duration]("Timeout for parallel processing")

    implicit class ProjectOps(p: Project) {
      def doPatchDependencies(): Project = {
        p.settings(
          inConfig(Compile)(patchDependenciesSettings)
        )
      }

      private def patchDependenciesSettings: Seq[Setting[_]] = Seq(
        modulePatcherParallel := true,
        modulePatcherTimeout := 5.minutes,
        patchDependencies := {
          val log = streams.value.log
          val report = update.value
          val jarFiles = report.allFiles.filter(_.getName.endsWith(".jar"))
          val parallel = modulePatcherParallel.value
          val timeout = modulePatcherTimeout.value

          if (parallel) {
            val futures = jarFiles.map(f => Future(processJar(f, log)))
            try {
              Await.result(Future.sequence(futures), timeout)
            } catch {
              case e: Exception =>
                log.error(s"Failed to process JARs: ${e.getMessage}")
                throw e
            }
          } else {
            jarFiles.foreach(f => processJar(f, log))
          }
        },
        compile := (compile dependsOn patchDependencies).value
      )
    }
  }

  private def processJar(jarFile: File, log: Logger): Unit = {
    val lock = jarLocks.computeIfAbsent(jarFile.getAbsolutePath, _ => new ReentrantLock())
    if (lock.tryLock()) {
      try {
        if (!isModule(jarFile, log)) {
          createBackup(jarFile)
          try {
            modifyJar(jarFile, log)
            updateChecksums(jarFile, log)
            if (modulePatcherVerifyChecksums.value && !verifyChecksums(jarFile, log)) {
              restoreFromBackup(jarFile)
              throw new Exception(s"Checksum verification failed for ${jarFile.getName}")
            }
          } catch {
            case NonFatal(e) =>
              log.error(s"Failed to patch ${jarFile.getName}: ${e.getMessage}")
              restoreFromBackup(jarFile)
              throw e
          } finally {
            cleanupBackup(jarFile)
          }
        }
      } finally {
        lock.unlock()
      }
    } else {
      log.warn(s"Skipping ${jarFile.getName} - already being processed")
    }
  }

  private def createBackup(file: File): Unit = {
    Files.copy(
      file.toPath,
      file.toPath.resolveSibling(file.getName + BACKUP_SUFFIX),
      StandardCopyOption.REPLACE_EXISTING
    )
  }

  private def restoreFromBackup(file: File): Unit = {
    val backup = file.toPath.resolveSibling(file.getName + BACKUP_SUFFIX)
    if (Files.exists(backup)) {
      Files.move(backup, file.toPath, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private def cleanupBackup(file: File): Unit = {
    val backup = file.toPath.resolveSibling(file.getName + BACKUP_SUFFIX)
    Files.deleteIfExists(backup)
  }

  private def isValidModuleName(name: String): Boolean = {
    // Based on JPMS naming rules
    name.matches("^[a-zA-Z][a-zA-Z0-9]*(?:\\.[a-zA-Z][a-zA-Z0-9]*)*$") &&
    !name.contains("..") &&
    !name.endsWith(".")
  }

  private def generateModuleName(jarFile: File): String = {
    val name = jarFile.getName
    val scalaVersionIndex = Seq("_2.12", "_2.13").flatMap(v => Option(name.indexOf(v))).find(_ >= 0).getOrElse(-1)
    if (scalaVersionIndex == -1) {
      throw new IllegalArgumentException(s"Cannot determine Scala version for ${jarFile.getName}")
    }

    val baseName = name.substring(0, scalaVersionIndex)
    val moduleName = baseName.replaceAll("[\\-_]", ".")

    if (!isValidModuleName(moduleName)) {
      throw new IllegalArgumentException(s"Generated invalid module name: $moduleName")
    }
    moduleName
  }

  private def modifyJar(jarFile: File, log: Logger): Unit = {
    val moduleName = generateModuleName(jarFile)

    Using.resources(
      new JarFile(jarFile),
      Files.newOutputStream(jarFile.toPath)
    ) { (jar, out) =>
      val jos = new JarOutputStream(out)
      try {
        // Write manifest first
        val manifest = new Manifest(jar.getManifest)
        val attrs = manifest.getMainAttributes
        attrs.putValue("Automatic-Module-Name", moduleName)
        attrs.putValue(SBT_MODULE_PATCHER_VERSION_KEY, SBT_MODULE_PATCHER_VERSION_VALUE)

        jos.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"))
        manifest.write(jos)
        jos.closeEntry()

        // Copy all other entries
        jar.entries().asScala
          .filterNot(_.getName == "META-INF/MANIFEST.MF")
          .foreach { entry =>
            jos.putNextEntry(new JarEntry(entry.getName))
            Using(jar.getInputStream(entry)) { in =>
              in.transferTo(jos)
            }
            jos.closeEntry()
          }
      } finally {
        jos.close()
      }
    }.get // propagate exceptions

    log.info(s"Modified JAR ${jarFile.getName} with module name: $moduleName")
  }

  private def updateChecksums(jarFile: File, log: Logger): Unit = {
    val algorithms = Seq("SHA-1", "MD5")
    algorithms.foreach { algorithm =>
      val checksum = calculateChecksum(jarFile, algorithm)
      val checksumFile = new File(jarFile.getAbsolutePath + "." + algorithm.toLowerCase.replace("-", ""))
      Files.write(checksumFile.toPath, checksum.getBytes, CREATE, WRITE)
      log.info(s"Updated checksum for ${jarFile.getName}: $algorithm = $checksum")
    }
  }

  private def calculateChecksum(file: File, algorithm: String): String = {
    val buffer = new Array[Byte](8192)
    val messageDigest = MessageDigest.getInstance(algorithm)
    val inputStream = new FileInputStream(file)
    var read = inputStream.read(buffer)
    while (read != -1) {
      messageDigest.update(buffer, 0, read)
      read = inputStream.read(buffer)
    }
    inputStream.close()
    messageDigest.digest.map("%02x".format(_)).mkString
  }

  private def verifyChecksums(jarFile: File, log: Logger): Boolean = {
    val jar = new JarFile(jarFile)
    val manifest = jar.getManifest

    if (manifest == null) return true

    val attrs = manifest.getMainAttributes
    val algorithms = Seq("SHA-1", "MD5")

    algorithms.forall { algo =>
      val originalChecksum = Option(attrs.getValue(s"Original-$algo-Checksum"))
      val checksumFile = new File(jarFile.getAbsolutePath + "." + algo.toLowerCase.replace("-", ""))

      if (checksumFile.exists && originalChecksum.isDefined) {
        val currentChecksum = Files.readString(checksumFile.toPath).trim
        if (currentChecksum != originalChecksum.get) {
          log.warn(s"Checksum mismatch detected for ${jarFile.getName} ($algo)")
          log.warn(s"Original: ${originalChecksum.get}")
          log.warn(s"Current:  $currentChecksum")
          false
        } else true
      } else true
    }
  }

  // Custom cache logger that patches JARs right after download
  private class PatchingCacheLogger(sbtLog: Logger) extends CacheLogger {
    override def downloadedArtifact(url: String, success: Boolean): Unit = {
      if (success && url.endsWith(".jar")) {
        val jarFile = new File(url.stripPrefix("file:"))
        if (!isModule(jarFile, sbtLog)) {
          modifyJar(jarFile, sbtLog)
          updateChecksums(jarFile, sbtLog)
        }
      }
    }

    override def downloadingArtifact(url: String): Unit = ()
    override def downloadProgress(url: String, downloaded: Long): Unit = ()
  }

}
