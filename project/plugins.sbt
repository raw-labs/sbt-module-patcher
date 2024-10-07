addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")

credentials += Credentials(
  "GitHub Package Registry",
  "maven.pkg.github.com",
  "raw-labs",
  sys.env.getOrElse("GITHUB_TOKEN", "")
)

resolvers += "RAW Labs GitHub Packages" at "https://maven.pkg.github.com/raw-labs/_"

addSbtPlugin("com.raw-labs" % "sbt-versioner" % "0.1.0")
