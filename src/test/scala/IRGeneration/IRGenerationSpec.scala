package IRGeneration

import Translator.FrontendPipeline
import org.antlr.v4.runtime.CharStreams
import org.scalatest.funsuite.AnyFunSuite

import scala.util.matching.Regex

class IRGenerationSpec extends AnyFunSuite {

  IRGenerationTestData.rules.foreach { tc =>
    test(s"Instruction '${tc.constructName}' generates correct IR") {
      val cs = CharStreams.fromString(tc.tvlCode)
      val res = FrontendPipeline.run(cs, debug = false)

      // Convert IR to a string format for easy regex matching
      val irString = res.ir.toList.sortBy(_._1).map { case (actor, instrs) =>
        s"Actor: $actor\n" + instrs.toList.sortBy(_._1).map { case (id, instr) =>
          s"  $id: $instr"
        }.mkString("\n")
      }.mkString("\n\n")

      tc.expectedPatterns.foreach { patternStr =>
        val regex = new Regex(patternStr)

        assert(
          regex.findFirstIn(irString).isDefined,
          s"""|FAILED: Pattern not found in IR output.
              |
              |Search Pattern: $patternStr
              |
              |Generated IR:
              |$irString""".stripMargin
        )
      }
    }
  }
}
