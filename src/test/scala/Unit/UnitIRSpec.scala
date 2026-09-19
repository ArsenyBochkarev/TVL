package Unit

import Translator.FrontendPipeline
import org.antlr.v4.runtime.CharStreams
import org.scalatest.funsuite.AnyFunSuite

import scala.util.matching.Regex

/**
 * Unit tests suite for verifying IR translation of specific TVL language constructs.
 * Validates that elements like actors, loops, and parallel blocks produce the correct
 * IR case classes (e.g. `IRQueuePush`, `IRJump`, etc.).
 */
class UnitIRSpec extends AnyFunSuite {

  UnitTestData.rules.foreach { tc =>
    test(s"Instruction '${tc.constructName}' generates correct IR") {
      val cs = CharStreams.fromString(tc.tvlCode)
      val res = FrontendPipeline.run(cs, debug = false)

      // Convert IR to a string format for easy regex matching
      val irString = res.toActorsTvirString

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
