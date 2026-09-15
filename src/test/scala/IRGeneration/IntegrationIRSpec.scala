package IRGeneration

import Translator.FrontendPipeline
import org.antlr.v4.runtime.CharStreams
import org.scalatest.funsuite.AnyFunSuite

import java.io.File
import java.nio.file.{Files, Paths, Path}

/**
 * Integration suite for testing TVL to IR translation.
 * It reads all `.tvl` files in the `examples/` directory, compiles them,
 * and matches the generated IR against `.tvir` text files in `src/test/resources/tvir/`.
 */
class IntegrationIRSpec extends AnyFunSuite {

  def listTvlFiles(dir: File): Array[File] = {
    val these = dir.listFiles
    if (these == null) Array.empty
    else these.filter(_.isFile).filter(_.getName.endsWith(".tvl")) ++ these.filter(_.isDirectory).flatMap(listTvlFiles)
  }

  val examplesDir = new File("examples")
  val tvlFiles = listTvlFiles(examplesDir)

  tvlFiles.foreach { tvlFile =>
    test(s"Integration Test: ${tvlFile.getName} generates correct IR") {
      val relPath = examplesDir.toPath.relativize(tvlFile.toPath).toString
      val irFileName = relPath.stripSuffix(".tvl") + ".tvir"
      val irFilePath = Paths.get("src", "test", "resources", "tvir", irFileName)

      val cs = CharStreams.fromFileName(tvlFile.getPath)
      val res = FrontendPipeline.run(cs, debug = false)

      // Convert IR to string format
      val irString = res.ir.toList.sortBy(_._1).map { case (actor, instrs) =>
        s"Actor: $actor\n" + instrs.toList.sortBy(_._1).map { case (id, instr) =>
          s"  $id: $instr"
        }.mkString("\n")
      }.mkString("\n\n") + "\n"

      if (Files.exists(irFilePath)) {
        val expectedIrString = Files.readString(irFilePath)
        assert(irString == expectedIrString, s"Generated IR for ${tvlFile.getName} does not match expected output in $irFilePath")
      } else {
        if (sys.env.contains("UPDATE_GOLDEN_FILES")) {
          // Generate the .tvir file if it doesn't exist
          Files.createDirectories(irFilePath.getParent)
          Files.writeString(irFilePath, irString)
          println(s"Generated $irFilePath")
        } else {
          fail(s"Expected IR file does not exist: $irFilePath. Set UPDATE_GOLDEN_FILES=1 to generate it.")
        }
      }
    }
  }
}
