package Target

import Translator.Abstraction.*
import Translator.Frontend.{FrontendResult, TVIRReader}
import Translator.FrontendPipeline
import Translator.IR.*
import org.antlr.v4.runtime.CharStreams
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

class AbstractorSuite extends AnyFunSuite {

  // A logs to C, B has a bounded loop; the spec only mentions B
  private val src =
    """module Test
      |
      |actor A {
      |    send X to C
      |    repeat 2 {
      |        send Y to C
      |    }
      |}
      |
      |actor B {
      |    repeat 3 {
      |        receive X from R
      |    }
      |    done: skip
      |}
      |
      |actor C {
      |    receive X from A
      |    receive Y from A
      |    receive Y from A
      |}
      |
      |specs {
      |    ltl Finish: "F B.done";
      |}
      |""".stripMargin

  private def frontend: FrontendResult =
    FrontendPipeline.run(CharStreams.fromString(src), debug = false)

  private def instrsOf(fr: FrontendResult, actor: String): mutable.Map[Int, IRInstruction] =
    fr.ir(actor)

  private def firstOf(fr: FrontendResult, actor: String)(f: IRInstruction => Boolean): IRInstruction =
    instrsOf(fr, actor).values.filter(f).toList.sortBy(_.id).head

  private def specOf(decisions: NodeDecision*): AbstractionSpec =
    AbstractionSpec(decisions.toList, Nil, Nil, Nil)

  test("loop-unroll replaces IRJumpGuard with IRChoice{body, exit} in place") {
    val fr = frontend
    val guard = firstOf(fr, "B") { case _: IRJumpGuard => true; case _ => false }.asInstanceOf[IRJumpGuard]
    val out = Abstractor(fr, specOf(NodeDecision("B", guard.id, "loop-unroll")))
    assert(out.applied.map(_.node) == List(guard.id))

    val choice = instrsOf(out.result, "B")(guard.id).asInstanceOf[IRChoice]
    assert(choice.branches.toSet == Set(guard.target, guard.next))
    assert(instrsOf(out.result, "B").values.forall { case _: IRJumpGuard => false; case _ => true },
      "the guard var must disappear with the last IRJumpGuard")
    // postcondition: structurally valid .tvir round trip
    TVIRReader.fromTVIRString(out.result.toTVIRString)
  }

  test("slice-actor removes the actor and turns sends to it into skips") {
    val fr = frontend
    val cEntry = instrsOf(fr, "C").keys.min
    val out = Abstractor(fr, specOf(NodeDecision("C", cEntry, "slice-actor")))
    assert(out.applied.map(_.actor) == List("C"))
    assert(out.refused.isEmpty && out.disabledSpecs.isEmpty)

    assert(!out.result.ir.contains("C"), "the sliced actor must be gone")
    val affected = out.applied.head.affected
    assert(affected.nonEmpty, "the sends to C must be reported as affected")
    affected.foreach { id =>
      val (actor, instr) = out.result.ir.find((_, m) => m.contains(id)).get
      val skip = instr(id).asInstanceOf[IRSkip]
      assert(actor == "A", "only A sends to C")
    }
    // the skip keeps the original successor, so the graph stays connected
    val aInstrs = instrsOf(out.result, "A")
    assert(aInstrs.values.forall {
      case p: IRQueuePush => !p.queueName.contains("][C]")
      case _ => true
    }, "no instruction may reference a queue of the sliced actor")
    TVIRReader.fromTVIRString(out.result.toTVIRString)
  }

  test("slice-actor refuses actors the specs/labels observe") {
    val fr = frontend
    val bEntry = instrsOf(fr, "B").keys.min
    val out = Abstractor(fr, specOf(NodeDecision("B", bEntry, "slice-actor")))
    assert(out.applied.isEmpty)
    assert(out.refused.exists(_._1.actor == "B"))
    assert(out.result.ir.contains("B"))
  }

  test("slice-actor refuses actors that send to kept actors") {
    val fr = frontend
    val aEntry = instrsOf(fr, "A").keys.min
    val out = Abstractor(fr, specOf(NodeDecision("A", aEntry, "slice-actor")))
    assert(out.applied.isEmpty)
    assert(out.refused.exists(_._1.actor == "A"))
    assert(out.refused.head._2.contains("sends"))
  }

  test("slice-actor drops template specs and reports them") {
    val withTemplate = FrontendResult(frontend.ir, List("FinishingProperty"), frontend.userSpecs, frontend.labels)
    val cEntry = instrsOf(withTemplate, "C").keys.min
    val out = Abstractor(withTemplate, specOf(NodeDecision("C", cEntry, "slice-actor")))
    assert(out.applied.map(_.actor) == List("C"))
    assert(out.disabledSpecs == List("FinishingProperty"))
    assert(out.result.templateSpecs.isEmpty)
  }

  test("auto expands slice-actor to one decision per actor, minus refusals; loop-unroll per guard") {
    val fr = frontend
    val spec = AbstractionSpec(Nil, List("slice-actor", "loop-unroll"), Nil, Nil)
    val out = Abstractor(fr, spec)
    // A's entry node is also its loop guard: the two decisions must coexist
    assert(out.applied.exists(d => d.actor == "A" && d.kind == "slice-actor"))
    assert(out.applied.exists(d => d.actor == "C" && d.kind == "slice-actor"))
    // B carries a label and is named by the spec, so it is never sliced, but
    // its loop guard is still unrolled
    assert(out.applied.exists(d => d.actor == "B" && d.kind == "loop-unroll"))
    assert(!out.applied.exists(d => d.actor == "B" && d.kind == "slice-actor"))
    assert(out.refused.map(_._1.actor).toSet == Set("B"))
  }

  test("explicit decisions respect the blacklist") {
    val fr = frontend
    val guard = firstOf(fr, "B") { case _: IRJumpGuard => true; case _ => false }
    val spec = AbstractionSpec(List(NodeDecision("B", guard.id, "loop-unroll")), Nil,
      List(BlacklistEntry("B", guard.id, "spurious")), Nil)
    assert(Abstractor(fr, spec).applied.isEmpty)
  }

  test("unknown kind is a hard error") {
    val fr = frontend
    val guard = firstOf(fr, "B") { case _: IRJumpGuard => true; case _ => false }
    val e = intercept[AbstractionException] {
      Abstractor(fr, specOf(NodeDecision("B", guard.id, "pop-skip")))
    }
    assert(e.getMessage.contains("pop-skip"))
  }

  test("kind that does not apply to the node is a hard error") {
    val fr = frontend
    val skip = firstOf(fr, "B") { case _: IRSkip => true; case _ => false }
    val e = intercept[AbstractionException] {
      Abstractor(fr, specOf(NodeDecision("B", skip.id, "loop-unroll")))
    }
    assert(e.getMessage.contains("does not apply"))
  }

  test("missing node is a hard error") {
    val e = intercept[AbstractionException] {
      Abstractor(frontend, specOf(NodeDecision("B", 99999, "loop-unroll")))
    }
    assert(e.getMessage.contains("99999"))
  }

  test("disable_specs drops template specs from the abstract result") {
    val fr = frontend
    val spec = AbstractionSpec(Nil, Nil, Nil, List("FinishingProperty"))
    val out = Abstractor(fr, spec)
    assert(out.result.templateSpecs == fr.templateSpecs.filterNot(_ == "FinishingProperty"))
  }

  test("sidecar spec survives a save/load round trip") {
    val spec = AbstractionSpec(
      List(NodeDecision("B", 12, "loop-unroll")),
      List("slice-actor"),
      List(BlacklistEntry("B", 12, "spurious at iter 1, step 7")),
      List("ValidityProperty"))
    val text = Json.print(spec.toJson)
    val back = AbstractionSpec.parse(text)
    assert(back == spec)
  }

  test("sidecar parsing rejects a wrong format tag") {
    val bad = """{"format": "tvl-abstraction/0", "decisions": [], "blacklist": []}"""
    assert(intercept[AbstractionException] { AbstractionSpec.parse(bad) }
      .getMessage.contains("format"))
  }

  test("minimal JSON parser handles the sidecar constructs") {
    val v = Json.parse("""{"a": [1, -2, "x\"y", true, null], "b": {"c": 3}}""")
    assert(v.field("a").get.items.flatMap(_.asInt) == List(1, -2))
    assert(v.field("b").get.field("c").get.asInt.get == 3)
    assert(Json.parse(Json.print(v)) == v)
  }
}
