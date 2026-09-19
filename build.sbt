import scala.collection.Seq

ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.7"

lazy val UnitTest = config("unit") extend(Test)
lazy val CorrectnessTest = config("correctness") extend(Test)
lazy val TargetTest = config("target") extend(Test)

lazy val root = (project in file("."))
  .configs(UnitTest, CorrectnessTest, TargetTest)
  .settings(
    name := "TVL",
    libraryDependencies ++= Seq(
      "org.antlr" % "antlr4" % "4.13.2",
      "org.antlr" % "antlr4-runtime" % "4.13.2",
      "org.scalatest" %% "scalatest" % "3.2.17" % Test
    ),
    inConfig(UnitTest)(Defaults.testTasks),
    inConfig(CorrectnessTest)(Defaults.testTasks),
    inConfig(TargetTest)(Defaults.testTasks),

    UnitTest / testOptions := Seq(Tests.Filter(s => s.startsWith("Unit."))),
    CorrectnessTest / testOptions := Seq(Tests.Filter(s => s.startsWith("Correctness."))),
    TargetTest / testOptions := Seq(Tests.Filter(s => s.startsWith("Target.")))
  )
