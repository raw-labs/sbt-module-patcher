import sbt._
import sbt.Keys._
import java.io._
import java.nio.file._
import java.util.jar._
import scala.collection.JavaConverters._
import java.security.MessageDigest
import java.nio.file.{Files, Paths}
import java.nio.file.StandardOpenOption.{CREATE, WRITE}

object SbtModulePatcher extends AutoPlugin {

  private val SBT_MODULE_PATCHER_VERSION_KEY = "Sbt-Module-Patcher-Version"
  private val SBT_MODULE_PATCHER_VERSION_VALUE = "1"

  override def trigger = allRequirements

  override def requires = plugins.JvmPlugin

  override lazy val projectSettings = Seq(
    update := {
      val log = streams.value.log
      val result = update.value
      modifyDownloadedJars(result, log)
      result
    }
  )

  private def modifyDownloadedJars(updateReport: UpdateReport, log: Logger): Unit = {
    val jarFiles = updateReport.allFiles.filter(_.getName.endsWith(".jar"))
    jarFiles.foreach { jarFile =>
      if (!isModule(jarFile, log)) {
        modifyJar(jarFile, log)
        updateChecksums(jarFile, log)
      }
    }
  }

  private def isModule(jarFile: File, log: Logger): Boolean = {
    if (!jarFile.getName.contains("_2.12")) {
      return true
    }

    val jar = new JarFile(jarFile)
    val entries = jar.entries().asScala

    // Check if module-info.class exists
    if (entries.exists(_.getName == "module-info.class")) {
      jar.close()
      log.info(s"JAR file ${jarFile.getName} is already a module (module-info.class found)")
      return true
    }

    // Check if Automatic-Module-Name exists in the manifest
    val manifest = jar.getManifest
    if (manifest != null && manifest.getMainAttributes.getValue("Automatic-Module-Name") != null) {
      jar.close()
      log.debug(s"JAR file ${jarFile.getName} is already a module (Automatic-Module-Name found in manifest)")
      return true
    }

    // Check if SBT_MODULE_PATCHER_VERSION_KEY exists in the manifest and matches the current version
    if (manifest != null && manifest.getMainAttributes.getValue(SBT_MODULE_PATCHER_VERSION_KEY) == SBT_MODULE_PATCHER_VERSION_VALUE) {
      jar.close()
      log.debug(s"JAR file ${jarFile.getName} was already patched by SbtModulePatcher version $SBT_MODULE_PATCHER_VERSION_VALUE")
      return true
    }

    jar.close()
    false
  }

  private def modifyJar(jarFile: File, log: Logger): Unit = {
    val tempFile = File.createTempFile("temp", ".jar")
    Files.copy(jarFile.toPath, tempFile.toPath, StandardCopyOption.REPLACE_EXISTING)

    val jar = new JarFile(tempFile)
    val entries = jar.entries().asScala
    val tempJar = File.createTempFile("temp-modified", ".jar")
    val jos = new JarOutputStream(new FileOutputStream(tempJar))

    val manifest = jar.getManifest
    val manifestOut = if (manifest != null) {
      manifest
    } else {
      new Manifest()
    }
    val moduleName = jarFile.getName.substring(0, jarFile.getName.indexOf("_2.12")).replaceAll("-", ".").replaceAll("_", ".")
    val attrs = manifestOut.getMainAttributes
    attrs.putValue("Automatic-Module-Name", moduleName)
    // Handy if we need to "migrate and re-generate" in the future.
    attrs.putValue(SBT_MODULE_PATCHER_VERSION_KEY, SBT_MODULE_PATCHER_VERSION_VALUE)
    jos.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"))
    manifestOut.write(jos)
    jos.closeEntry()

    entries.foreach { entry =>
      if (entry.getName != "META-INF/MANIFEST.MF") {
        jos.putNextEntry(new JarEntry(entry.getName))
        val inputStream = jar.getInputStream(entry)
        jos.write(inputStream.readAllBytes())
        jos.closeEntry()
      }
    }

    jos.close()
    jar.close()

    // Replace original JAR with modified JAR
    Files.move(tempJar.toPath, jarFile.toPath, StandardCopyOption.REPLACE_EXISTING)
    log.info(s"Modified JAR file ${jarFile.getName} by adding Automatic-Module-Name: $moduleName")
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

}
