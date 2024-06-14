sbtPlugin := true

moduleName := "sbt-module-patcher"

homepage := Some(url("https://www.raw-labs.com/"))

organization := "com.raw-labs.sbt"

organizationName := "RAW Labs SA"

organizationHomepage := Some(url("https://www.raw-labs.com/"))

version := "0.0.1"

scalaVersion := "2.12.18"

libraryDependencies += "io.get-coursier" %% "coursier" % "2.1.10"

publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)
