
name := "scalar"

version := "1.0"

scalaVersion := "2.11.5"

lazy val root = (project in file("."))

resolvers += "spray repo" at "http://repo.spray.io"

Revolver.settings

mainClass := Some("com.songo.scalar.ScalarServer")

val sprayVersion = "1.3.2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.9",
  "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.9",
  "io.spray" %% "spray-can" % sprayVersion,
  "io.spray" %% "spray-routing" % sprayVersion,
  "io.spray" %%  "spray-json" % "1.3.1",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "com.geteventstore" %% "akka-persistence-eventstore" % "2.0.0"
)

fork := true
javaOptions += "-Dakka.persistence.journal.leveldb.dir=/uploads"
javaOptions += "-Dakka.persistence.journal.leveldb.native=off" 