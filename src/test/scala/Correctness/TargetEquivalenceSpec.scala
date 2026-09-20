package Correctness

import Translator.FrontendPipeline
import Translator.Frontend.TVIRReader
import Translator.Target.*
import org.antlr.v4.runtime.CharStreams
import org.scalatest.funsuite.AnyFunSuite

import java.io.File
import java.nio.file.{Files, Path, Paths}

/**
 * Equivalence suite: translating a model from its golden .tvir dump must produce
 * byte-identical target code to translating from the original .tvl source, for
 * every target and every example. Guards the frontend/backend split: after the
 * TVIRReader stage the pipeline is shared, so any divergence means the reader
 * lost or altered information the targets depend on.
 */
class TargetEquivalenceSpec extends AnyFunSuite {

  def listTvlFiles(dir: File): Array[File] = {
    val these = dir.listFiles
    if (these == null) Array.empty
    else these.filter(_.isFile).filter(_.getName.endsWith(".tvl")) ++ these.filter(_.isDirectory).flatMap(listTvlFiles)
  }

  val examplesDir = new File("examples")
  val tvlFiles = listTvlFiles(examplesDir)

  // PlusCal also writes a .cfg next to the output file; keep side effects out of the repo
  val scratchDir: Path = Files.createTempDirectory("tvl-target-equivalence")

  def generate(res: Translator.Frontend.FrontendResult, targetName: String, outputName: String): String = {
    val ext = if targetName == "tla" then "tla" else "pml"
    val translator: TargetTranslator = targetName match {
      case "spin" => new Promela()
      case "tla" => new PlusCal()
    }
    translator.setOutputFile(scratchDir.resolve(s"$outputName.$ext").toString)
    translator.setEnabledProperties(res.templateSpecs)
    translator.setUserLabels(res.labels)
    val code = translator.translate(res.ir)
    code + translator.generateTemplateSpecs + translator.generateUserSpecs(res.userSpecs, targetName)
  }

  tvlFiles.foreach { tvlFile =>
    val relPath = examplesDir.toPath.relativize(tvlFile.toPath).toString
    test(s"Target equivalence: $relPath (.tvl vs .tvir)") {
      val goldenPath = Paths.get("src", "test", "tvir", "examples", relPath.stripSuffix(".tvl") + ".tvir")
      assume(Files.exists(goldenPath),
        s"golden .tvir missing for $relPath (run UPDATE_GOLDEN_FILES=1 sbt correctness:test); IntegrationIRSpec enforces it")

      val fromTvl = FrontendPipeline.run(CharStreams.fromFileName(tvlFile.getPath), debug = false)
      val fromTvir = TVIRReader.fromTVIRString(Files.readString(goldenPath))

      for (targetName <- Seq("tla", "spin")) {
        val outTvl = generate(fromTvl, targetName, s"${relPath.replace('/', '_')}-fromTvl")
        val outTvir = generate(fromTvir, targetName, s"${relPath.replace('/', '_')}-fromTvir")
        assert(outTvl == outTvir,
          s"$targetName output for $relPath differs between .tvl and .tvir inputs")
      }
    }
  }
}
