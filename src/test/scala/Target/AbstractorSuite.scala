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

  // No specs, no labels: every actor passes the per-actor guardrails, so only
  // the every-actor guardrail stands between slicing and a vacuous model
  private val bareSrc =
    """module Bare
      |
      |actor A {
      |    send X to B
      |}
      |
      |actor B {
      |    receive X from A
      |}
      |""".stripMargin

  private def bareFrontend: FrontendResult =
    FrontendPipeline.run(CharStreams.fromString(bareSrc), debug = false)

  // A sends two same-fate messages, B's alts matches both of them (plus a
  // never-pushed Data in between); {One1, One2} is a sound class
  private val collapseSrc =
    """module Collapse
      |
      |actor A {
      |    send One1 to B
      |    send One2 to B
      |}
      |
      |actor B {
      |    receive alts {
      |        One1 from A => { got1: skip }
      |        Data from A => { gotD: skip }
      |        One2 from A => { got2: skip }
      |    }
      |}
      |""".stripMargin

  private def collapseFrontend: FrontendResult =
    FrontendPipeline.run(CharStreams.fromString(collapseSrc), debug = false)

  // One2 is pushed to the queue but never matched by the alts: merging would
  // delete the unmatched-arrival (wait) behavior - the guardrail's exhibit A
  private val unmatchedSrc =
    """module Unmatched
      |
      |actor A {
      |    send One1 to B
      |    send One2 to B
      |}
      |
      |actor B {
      |    receive alts {
      |        One1 from A => { got1: skip }
      |    }
      |}
      |""".stripMargin

  private def unmatchedFrontend: FrontendResult =
    FrontendPipeline.run(CharStreams.fromString(unmatchedSrc), debug = false)

  // One1 is consumed by a plain receive: the renamed receive would eat One2
  private val plainReceiveSrc =
    """module PlainReceive
      |
      |actor A {
      |    send One1 to B
      |    send One2 to B
      |}
      |
      |actor B {
      |    receive One1 from A
      |    receive alts {
      |        One2 from A => { got2: skip }
      |    }
      |}
      |""".stripMargin

  private def plainReceiveFrontend: FrontendResult =
    FrontendPipeline.run(CharStreams.fromString(plainReceiveSrc), debug = false)

  // two independent classes on two queues: the refinement gradient unit
  private val twoClassesSrc =
    """module TwoClasses
      |
      |actor A {
      |    send One1 to B
      |    send One2 to B
      |    send Win1 to C
      |    send Win2 to C
      |}
      |
      |actor B {
      |    receive alts {
      |        One1 from A => { b1: skip }
      |        One2 from A => { b2: skip }
      |    }
      |}
      |
      |actor C {
      |    receive alts {
      |        Win1 from A => { c1: skip }
      |        Win2 from A => { c2: skip }
      |    }
      |}
      |""".stripMargin

  private def twoClassesFrontend: FrontendResult =
    FrontendPipeline.run(CharStreams.fromString(twoClassesSrc), debug = false)

  // the class LCP "One" collides with a real message name and must be extended
  private val nameClashSrc =
    """module NameClash
      |
      |actor A {
      |    send One to B
      |    send One1 to B
      |    send One2 to B
      |}
      |
      |actor B {
      |    receive alts {
      |        One1 from A => { b1: skip }
      |        One2 from A => { b2: skip }
      |    }
      |}
      |""".stripMargin

  private def nameClashFrontend: FrontendResult =
    FrontendPipeline.run(CharStreams.fromString(nameClashSrc), debug = false)

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

  test("slice-actor refuses everything when slicing would remove every actor") {
    val fr = bareFrontend
    // the default CEGAR auto set: no repeat loops, so loop-unroll also finds
    // nothing - the call must apply nothing and NOT throw
    val out = Abstractor(fr, AbstractionSpec(Nil, List("loop-unroll", "slice-actor"), Nil, Nil))
    assert(out.applied.isEmpty)
    assert(out.refused.map(_._1.actor).sorted == List("A", "B"),
      "every actor refused exactly once, no duplicates")
    assert(out.refused.forall(_._2 == "slicing would remove every actor"))
    assert(out.result.ir.keySet == fr.ir.keySet, "the model must stay intact")
    assert(out.disabledSpecs.isEmpty && out.result.templateSpecs == fr.templateSpecs)
    TVIRReader.fromTVIRString(out.result.toTVIRString)
  }

  test("blacklisting one actor's entry node lets the others be sliced") {
    val fr = bareFrontend
    val aEntry = instrsOf(fr, "A").keys.min // the auto slice decision's node
    val spec = AbstractionSpec(Nil, List("slice-actor"),
      List(BlacklistEntry("A", aEntry, "spurious at iter 1, step 1")), Nil)
    val out = Abstractor(fr, spec)
    assert(out.applied.map(_.actor) == List("B"))
    assert(!out.result.ir.contains("B") && out.result.ir.contains("A"))
  }

  test("collapse-messages merges a class into one case with a fresh IRChoice") {
    val fr = collapseFrontend
    val origBranch = firstOf(fr, "B") { case _: IRBranch => true; case _ => false }.asInstanceOf[IRBranch]
    val classBodies = List(origBranch.cases.head.bodyStart, origBranch.cases(2).bodyStart) // One1, One2 cases

    val out = Abstractor(fr, AbstractionSpec(Nil, List("collapse-messages"), Nil, Nil))
    assert(out.refused.isEmpty)
    assert(out.applied.map(d => (d.actor, d.node, d.kind)) == List(("One", 0, "collapse-messages")))

    // both pushes renamed
    assert(instrsOf(out.result, "A").values.collect { case p: IRQueuePush => p.msg }.toSet == Set("One"))
    // the two class cases merge into one; the interleaved Data case is untouched
    val branch = firstOf(out.result, "B") { case _: IRBranch => true; case _ => false }.asInstanceOf[IRBranch]
    assert(branch.cases.map(_.msg) == List("One", "Data"))
    assert(branch.cases(1) == origBranch.cases(1))
    // the merged body is a fresh IRChoice between the original case bodies
    val choiceId = branch.cases.head.bodyStart
    val choice = instrsOf(out.result, "B")(choiceId).asInstanceOf[IRChoice]
    assert(choice.branches == classBodies)
    // the report covers every touched node: the pushes, the branch, the choice
    // and the merged bodies (a spurious counterexample through a body must map
    // back to the class decision when refining)
    val affected = out.applied.head.affected.toSet
    val pushIds = instrsOf(fr, "A").values.collect { case p: IRQueuePush => p.id }.toSet
    assert(pushIds.subsetOf(affected) && affected.contains(origBranch.id) && affected.contains(choiceId))
    assert(classBodies.toSet.subsetOf(affected))
    assert(affected.size == pushIds.size + 4)
    TVIRReader.fromTVIRString(out.result.toTVIRString)
  }

  test("collapse-messages never proposes a class with an unmatched member") {
    // One2 is pushed but not matched: its fate differs from One1's, so the
    // fate clustering yields no class at all - nothing applied, nothing refused
    val out = Abstractor(unmatchedFrontend, AbstractionSpec(Nil, List("collapse-messages"), Nil, Nil))
    assert(out.applied.isEmpty && out.refused.isEmpty)
    // the all-or-nothing guardrail is the explicit invariant behind that: it
    // flags the same IR should a coarser clustering ever propose the class
    val violation = CollapseMessagesPass.guardrailViolation(unmatchedFrontend.ir, Set("One1", "One2"))
    assert(violation.exists(_.contains("not matched")))
  }

  test("a plain receive blocks its message from any class") {
    // the renamed receive would eat the classmate at the queue head
    val violation = CollapseMessagesPass.guardrailViolation(plainReceiveFrontend.ir, Set("One1", "One2"))
    assert(violation.exists(_.contains("plain receive")))
    // and the fate signature (a pop point is unique to one message) never
    // proposes the class in the first place
    val out = Abstractor(plainReceiveFrontend, AbstractionSpec(Nil, List("collapse-messages"), Nil, Nil))
    assert(out.applied.isEmpty && out.refused.isEmpty)
  }

  test("blacklisting one class keeps the others collapsed") {
    val spec = AbstractionSpec(Nil, List("collapse-messages"),
      List(BlacklistEntry("One", 0, "spurious at iter 1, step 3")), Nil)
    val out = Abstractor(twoClassesFrontend, spec)
    assert(out.applied.map(_.actor) == List("Win"))
    // One's members keep their names, Win's are renamed
    assert(instrsOf(out.result, "A").values.collect { case p: IRQueuePush => p.msg }.toSet ==
      Set("One1", "One2", "Win"))
  }

  test("class name extends the longest common prefix until fresh") {
    // the LCP of {One1, One2} is "One", which is itself a message here
    val out = Abstractor(nameClashFrontend, AbstractionSpec(Nil, List("collapse-messages"), Nil, Nil))
    assert(out.applied.map(_.actor) == List("One_"))
    assert(instrsOf(out.result, "A").values.collect { case p: IRQueuePush => p.msg }.toSet ==
      Set("One", "One_", "One_"))
  }

  test("collapse-messages drops template specs and reports them") {
    val withTemplate = FrontendResult(collapseFrontend.ir, List("FinishingProperty"),
      collapseFrontend.userSpecs, collapseFrontend.labels)
    val out = Abstractor(withTemplate, AbstractionSpec(Nil, List("collapse-messages"), Nil, Nil))
    assert(out.applied.map(_.actor) == List("One"))
    assert(out.disabledSpecs == List("FinishingProperty"))
    assert(out.result.templateSpecs.isEmpty)
  }

  test("collapse-messages decisions with a wrong shape are hard errors") {
    val e1 = intercept[AbstractionException] {
      Abstractor(collapseFrontend, specOf(NodeDecision("Nope", 0, "collapse-messages")))
    }
    assert(e1.getMessage.contains("unknown message class"))
    val e2 = intercept[AbstractionException] {
      Abstractor(collapseFrontend, specOf(NodeDecision("One", 7, "collapse-messages")))
    }
    assert(e2.getMessage.contains("node 0"))
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
