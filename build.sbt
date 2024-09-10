sbtPlugin := true

moduleName := "sbt-module-patcher"

homepage := Some(url("https://www.raw-labs.com/"))

organization := "com.raw-labs.sbt"

organizationName := "RAW Labs SA"

organizationHomepage := Some(url("https://www.raw-labs.com/"))

scalaVersion := "2.12.18"

publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)

publishTo := Some("GitHub raw-labs Apache Maven Packages" at "https://maven.pkg.github.com/raw-labs/sbt-module-patcher")
