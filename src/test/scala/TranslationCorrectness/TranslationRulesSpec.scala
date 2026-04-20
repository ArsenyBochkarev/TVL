package TranslationCorrectness

import Translator.FrontendPipeline
import Translator.Target.*
import org.antlr.v4.runtime.CharStreams
import org.scalatest.funsuite.AnyFunSuite

import scala.util.matching.Regex

class TranslationRulesSpec extends AnyFunSuite {

  TestData.rules.foreach { tc =>
    tc.expectations.foreach { exp =>

      test(s"Instruction '${tc.constructName}' translates correctly to ${exp.targetName}") {
        val cs = CharStreams.fromString(tc.tvlCode)
        val res = FrontendPipeline.run(cs, debug = false)
        val translator: TargetTranslator = exp.targetName match {
          case "spin" => new Promela()
          case "tla" => new PlusCal()
          case _ => fail(s"Target '${exp.targetName}' is not supported for tests.")
        }
        val code = translator.translate(res.ir)
        exp.expectedPatterns.foreach { patternStr =>
          val regex = new Regex(patternStr.replaceAll("\\s+", "\\\\s+"))

          assert(
            regex.findFirstIn(code).isDefined,
            s"""|FAILED: Pattern not found in ${exp.targetName} output.
                |
                |Search Pattern: $patternStr
                |
                |Generated Code:
                |$code""".stripMargin
          )
        }
      }
    }
  }
}