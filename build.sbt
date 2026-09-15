import scala.collection.Seq

ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.7"

lazy val IRTest = config("ir") extend(Test)
lazy val CorrectnessTest = config("correctness") extend(Test)

lazy val root = (project in file("."))
  .configs(IRTest, CorrectnessTest)
  .settings(
    name := "TVL",
    libraryDependencies ++= Seq(
      "org.antlr" % "antlr4" % "4.13.2",
      "org.antlr" % "antlr4-runtime" % "4.13.2",
      "org.scalatest" %% "scalatest" % "3.2.17" % Test
    ),
    inConfig(IRTest)(Defaults.testTasks),
    inConfig(CorrectnessTest)(Defaults.testTasks),

    IRTest / testOptions := Seq(Tests.Filter(s => s.startsWith("IRGeneration."))),
    CorrectnessTest / testOptions := Seq(Tests.Filter(s => s.startsWith("TranslationCorrectness.")))
  )
