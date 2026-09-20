package Unit

import Translator.Frontend.{TVIRParseException, TVIRReader}
import Translator.Frontend.UserSpec
import Translator.IR.*
import Translator.IR.Lib.QueueCondition
import org.scalatest.funsuite.AnyFunSuite

/**
 * Unit tests for TVIRReader — the .tvir parser (inverse of FrontendResult.toTVIRString).
 * Covers instruction shapes absent from the golden files (Some(...), ctl specs, parallel blocks)
 * and the malformed-input error paths.
 */
class UnitTVIRReaderSpec extends AnyFunSuite {

  test("full round-trip: every instruction kind and all sections") {
    val tvir =
      """Actor: A
        |  1: IRQueuePush(1,4,(-1,-1),2,Q[B][A],Msg)
        |  2: IRParallelExec(2,5,(1,1),List(3, 5),6)
        |  3: IRQueuePush(3,6,(1,1),4,Q[B][A],Msg)
        |  4: IRParallelEnd(4,7,(1,1),6)
        |  5: IRSkip(5,8,(1,1),6)
        |  6: IRJumpGuard(6,9,(-1,-1),8,guard_A_6,7,2)
        |  7: IRQueuePush(7,10,(-1,-1),8,Q[B][A],Msg)
        |  8: IREnd(8,11,(-1,-1))
        |
        |Actor: B
        |  9: IRQueuePop(9,12,(-1,-1),10,Q[B][A],Msg)
        |  10: IRChoice(10,13,(-1,-1),List(11, 13))
        |  11: IRBranch(11,14,(-1,-1),List(QueueCondition(Q[B][A],Msg,12)),Some(14))
        |  12: IRJump(12,15,(-1,-1),14)
        |  13: IRSkip(13,16,(-1,-1),14)
        |  14: IREnd(14,17,(-1,-1))
        |
        |Template specs:
        |  FinishingProperty
        |
        |User specs:
        |  ctl check: EF (B.done)
        |  ltl safe: [] (A.start -> <> B.done)
        |
        |Labels:
        |  A.start: 1
        |  B.done: 9""".stripMargin + "\n"

    val res = TVIRReader.fromTVIRString(tvir)

    // Round-trip must be byte-identical
    assert(res.toTVIRString == tvir)

    // Structural checks
    assert(res.templateSpecs == List("FinishingProperty"))
    assert(res.userSpecs == List(
      UserSpec("ctl", "check", "EF (B.done)"),
      UserSpec("ltl", "safe", "[] (A.start -> <> B.done)")))
    assert(res.labels == Map("A" -> Map("start" -> 1), "B" -> Map("done" -> 9)))
    assert(res.ir("A")(2) match { case IRParallelExec(_, _, _, branches, _) => branches == List(3, 5); case _ => false })
    assert(res.ir("A")(6) match { case IRJumpGuard(_, _, _, _, guardVar, _, _) => guardVar == "guard_A_6"; case _ => false })
    assert(res.ir("B")(11) match {
      case IRBranch(_, _, _, cases, otherwise) =>
        cases == List(QueueCondition("Q[B][A]", "Msg", 12)) && otherwise == Some(14)
      case _ => false })
  }

  test("empty actor block is legal") {
    // The frontend never emits one (even `actor B {}` gets an IREnd), but the
    // serializer's shape for it — block ends right after the header — must read back
    val dump = "Actor: A\n  1: IREnd(1,2,(-1,-1))\n\nActor: B\n\n"

    val res = TVIRReader.fromTVIRString(dump)
    assert(res.ir("B").isEmpty)
    assert(res.ir("A").contains(1))
    assert(res.toTVIRString == dump)
  }

  private def bad(name: String, content: String, expectedMsg: String): Unit =
    test(s"malformed: $name") {
      val e = intercept[TVIRParseException](TVIRReader.fromTVIRString(content))
      assert(e.getMessage.contains(expectedMsg),
        s"expected message to contain \"$expectedMsg\", got: ${e.getMessage}")
    }

  bad("unknown instruction",
    "Actor: A\n  1: IRBogus(1,2,(-1,-1),3)\n",
    "unknown instruction \"IRBogus\"")
  bad("wrong arg count",
    "Actor: A\n  1: IRJump(1,2,(-1,-1),3,4)\n",
    "expects 4 args, got 5")
  bad("non-integer arg",
    "Actor: A\n  1: IRJump(x,2,(-1,-1),2)\n",
    "expected integer, got \"x\"")
  bad("line id does not match constructor id",
    "Actor: A\n  5: IRJump(6,2,(-1,-1),8)\n",
    "does not match constructor id")
  bad("duplicate instruction id",
    "Actor: A\n  1: IREnd(1,2,(-1,-1))\n  1: IREnd(1,2,(-1,-1))\n",
    "duplicate instruction id 1")
  bad("duplicate actor",
    "Actor: A\n\nActor: A\n",
    "duplicate actor \"A\"")
  bad("instruction outside actor block",
    "  1: IREnd(1,2,(-1,-1))\n",
    "outside any actor block")
  bad("unrecognized line",
    "hello\n",
    "unrecognized line \"hello\"")
  bad("section appears twice",
    "Actor: A\n  1: IREnd(1,2,(-1,-1))\n\nLabels:\n  A.x: 1\n\nLabels:\n  A.y: 1\n",
    "out of order or twice")
  bad("actor block after sections",
    "Actor: A\n  1: IREnd(1,2,(-1,-1))\n\nLabels:\n  A.x: 1\n\nActor: B\n",
    "actor block after sections began")
  bad("dangling successor",
    "Actor: A\n  1: IRJump(1,2,(-1,-1),99)\n",
    "successor 99 does not exist")
  bad("label references missing instruction",
    "Actor: A\n  1: IREnd(1,2,(-1,-1))\n\nLabels:\n  A.x: 5\n",
    "references missing instruction 5")
  bad("label references unknown actor",
    "Actor: A\n  1: IREnd(1,2,(-1,-1))\n\nLabels:\n  Z.x: 1\n",
    "unknown actor \"Z\"")
  bad("malformed scheduler tuple",
    "Actor: A\n  1: IRJump(1,2,(1),3)\n",
    "malformed scheduler tuple")
  bad("malformed Option",
    "Actor: A\n  1: IRBranch(1,2,(-1,-1),List(),Maybe(3))\n",
    "malformed Option \"Maybe(3)\"")
  bad("user spec without formula separator",
    "Actor: A\n  1: IREnd(1,2,(-1,-1))\n\nUser specs:\n  ltl noname\n",
    "malformed user spec")
  bad("no actor blocks",
    "Template specs:\n  FinishingProperty\n",
    "no actor blocks found")
}
