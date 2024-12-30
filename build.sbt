sbtPlugin := true

credentials += Credentials(
  "GitHub Package Registry",
  "maven.pkg.github.com",
  "raw-labs",
  sys.env.getOrElse("GITHUB_TOKEN", "")
)

versionScheme := Some("early-semver")

val githubOrg = "raw-labs"
name := "sbt-module-patcher"
val githubRepo = s"$githubOrg/${name.value}"
homepage := Some(url(s"https://github.com/$githubRepo"))
organization := "com.raw-labs"
organizationName := "RAW Labs SA"
organizationHomepage := Some(url("https://www.raw-labs.com/"))
scmInfo := Some(
  ScmInfo(
    url(s"https://github.com/$githubRepo"),
    s"scm:git@github.com:$githubRepo.git"
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

scalaVersion := "2.12.18"

publishMavenStyle := true
publishTo := Some("GitHub Package Registry" at s"https://maven.pkg.github.com/$githubRepo")

publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)
