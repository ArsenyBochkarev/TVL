import scala.collection.Seq

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.7"

lazy val root = (project in file("."))
  .settings(
    name := "TVL"
  )

libraryDependencies ++= Seq(
  "org.antlr" % "antlr4" % "4.13.2",
  "org.antlr" % "antlr4-runtime" % "4.13.2"
)