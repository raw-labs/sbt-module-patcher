sbtPlugin := true
versionScheme := Some("early-semver")

organization := "com.raw-labs"
organizationName := "RAW Labs SA"
organizationHomepage := Some(url("https://www.raw-labs.com/"))

name := "sbt-module-patcher"
description := "An SBT plugin for patching JARs with proper module names"
scalaVersion := "2.12.18"

val githubOrg = "raw-labs"
val githubRepo = settingKey[String]("GitHub repository name")
githubRepo := s"$githubOrg/${name.value}"

homepage := Some(url(s"https://github.com/${githubRepo.value}"))
scmInfo := Some(
  ScmInfo(
    url(s"https://github.com/${githubRepo.value}"),
    s"scm:git@github.com:${githubRepo.value}.git"
  )
)
developers := List(
  Developer(
    id = "raw-labs",
    name = organizationName.value,
    email = "engineering@raw-labs.com",
    url = url("https://www.raw-labs.com")
  )
)
licenses += ("MIT", url("https://opensource.org/licenses/MIT"))

publishMavenStyle := true
credentials += Credentials(
  "GitHub Package Registry",
  "maven.pkg.github.com",
  "raw-labs",
  sys.env.getOrElse("GITHUB_TOKEN", "")
)
publishTo := Some("GitHub Package Registry" at s"https://maven.pkg.github.com/${githubRepo.value}")
publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)

libraryDependencies += "io.get-coursier" %% "coursier-cache" % "2.1.8"

modulePatcherVerifyChecksums := true
modulePatcherParallel := true
modulePatcherTimeout := 5.minutes
