ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.4"
Compile / run / fork := true
lazy val root = (project in file("."))
  .settings(
    name := "fs2-queues-demo-app"
  )

val circeVersion = "0.14.10"
libraryDependencies += "com.commercetools" %% "fs2-queues-core" % "0.6.0"
libraryDependencies += "com.commercetools" %% "fs2-queues-aws-sqs" % "0.6.0"
libraryDependencies += "org.typelevel" %% "cats-effect" % "3.5.2"
libraryDependencies += "com.commercetools" %% "fs2-queues-circe" % "0.6.0"
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.4.7",
  "org.typelevel" %% "log4cats-slf4j" % "2.7.0"
)

libraryDependencies += "com.github.pureconfig" %% "pureconfig-core" % "0.17.6"
