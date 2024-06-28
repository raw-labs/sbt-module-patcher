Sure, here's the updated and completed README.md for the `sbt-module-patcher` plugin:

# sbt-module-patcher

This sbt plugin tries to alleviate some of the pain associated with creating JPMS-friendly JARs in the Scala world.

Specifically, when publishing a JAR, the Scala compiler version number is appended to the JAR name. For instance, a typical JAR name may be `foo_2.12-1.0.0.jar`.

In the JPMS world, a non-modularized JAR becomes an automatic module where the name is derived from the JAR name. In the generation of the automatic module name, underscores are replaced with dots. In the example above, the module name would therefore be `foo.2.12`. This, however, is not a valid module name; if you try to use it in a requires statement in a `module-info.java`, you will get an error.

If the JAR is something you control, this is not an issue: just fix the JAR name in your process, e.g., in sbt:

```scala
Compile / packageBin / packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "foo")
```

But what if this is a dependency you have no control over? That's where this plugin comes in.

It will patch the JAR name in the JAR file itself, so that the automatic module name is correct.

## Usage

This plugin currently supports only Scala 2.12.

Add the plugin to your `project/plugins.sbt` file:

```scala
addSbtPlugin("com.raw-labs.sbt" % "sbt-module-patcher" % "0.0.1")
```

Then, in your `build.sbt` file, apply the plugin to your project:

```scala
lazy val root = (project in file("."))
  .doPatchDependencies()
```

## How it works

This plugin performs the following steps to patch the JARs:

1. **Identifies JARs to Patch:** It scans the project's classpath for JAR files that are not already modularized.
2. **Modifies the Manifest:** For each JAR that needs patching, it updates the manifest to include a valid `Automatic-Module-Name`.
3. **Updates Checksums:** After modifying the JAR, it recalculates and updates the checksums (SHA-1, MD5) to ensure integrity.

## Tasks

The plugin provides the following tasks:

- `patchDependencies`: This task patches compile dependencies in the project classpath. It ensures that the JAR files have a valid `Automatic-Module-Name`.

### Example

Here's a detailed example of how you might configure your project to use this plugin:

```scala
// project/plugins.sbt
addSbtPlugin("com.raw-labs.sbt" % "sbt-module-patcher" % "0.0.1")

// build.sbt
lazy val root = (project in file("."))
  .settings(
    name := "MyProject",
    version := "1.0.0",
    scalaVersion := "2.12.12"
  )
  .doPatchDependencies()
```

## Behind the Scenes

### Source Code

The plugin's core functionality is implemented in the `SbtModulePatcher` object. Here's a breakdown of what it does:

1. **Task Definition**: Defines the `patchDependencies` task which scans the project classpath for JAR files.
2. **Patch Dependencies**: Checks each JAR to see if it's already a module. If not, it modifies the JAR manifest to add a valid `Automatic-Module-Name`.
3. **Modify JAR**: Creates a temporary JAR file with the updated manifest and replaces the original JAR with this new file.
4. **Update Checksums**: Calculates and updates the checksums for the modified JAR file to ensure that the JAR file remains valid.

For more details, refer to the source code in the `SbtModulePatcher` object.

## Summary

The `sbt-module-patcher` plugin simplifies the process of making Scala JARs JPMS-friendly by automating the patching of JAR manifests to include a valid `Automatic-Module-Name`. This can be especially useful when dealing with dependencies that you cannot control.

By incorporating this plugin into your sbt build process, you can ensure that your JARs are compatible with JPMS without manual intervention.

(Special thanks to ChatGPT for helping with this README!)

---

Feel free to [reach out](mailto:miguel@raw-labs.com) if you encounter any issues or have any questions regarding the `sbt-module-patcher` plugin.
