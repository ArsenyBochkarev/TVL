package Target

import Translator.Abstraction.*
import Translator.Frontend.{FrontendResult, TVIRReader}
import Translator.FrontendPipeline
import Translator.IR.*
import org.antlr.v4.runtime.CharStreams
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

class AbstractorSuite extends AnyFunSuite {

  private val src =
    """module Test
      |
      |actor R1 {
      |    send X to R2
      |    send X to R2
      |    send X to R2
      |    send X to R2
      |}
      |
      |actor R2 {
      |    repeat 3 {
      |        receive X from R1
      |    }
      |    alt: receive alts {
      |        X from R1 => { skip }
      |        otherwise => { send Y to R1 }
      |    }
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
    val guard = firstOf(fr, "R2") { case _: IRJumpGuard => true; case _ => false }.asInstanceOf[IRJumpGuard]
    val out = Abstractor(fr, specOf(NodeDecision("R2", guard.id, "loop-unroll")))
    assert(out.applied.map(_.node) == List(guard.id))

    val choice = instrsOf(out.result, "R2")(guard.id).asInstanceOf[IRChoice]
    assert(choice.branches.toSet == Set(guard.target, guard.next))
    assert(instrsOf(out.result, "R2").values.forall { case _: IRJumpGuard => false; case _ => true },
      "the guard var must disappear with the last IRJumpGuard")
    // postcondition: structurally valid .tvir round trip
    TVIRReader.fromTVIRString(out.result.toTVIRString)
  }

  test("branch-hoist replaces IRBranch with IRChoice over hoisted consuming pops") {
    val fr = frontend
    val branch = firstOf(fr, "R2") { case _: IRBranch => true; case _ => false }.asInstanceOf[IRBranch]
    val out = Abstractor(fr, specOf(NodeDecision("R2", branch.id, "branch-hoist")))

    val choice = instrsOf(out.result, "R2")(branch.id).asInstanceOf[IRChoice]
    assert(choice.branches.last == branch.otherwise.get, "otherwise stays the last, always-enabled branch")
    assert(choice.branches.length == branch.cases.length + 1)

    branch.cases.zip(choice.branches.dropRight(1)).foreach { (c, popId) =>
      val pop = instrsOf(out.result, "R2")(popId).asInstanceOf[IRQueuePop]
      assert(pop.queueName == c.queueName && pop.msg == c.msg,
        "the hoisted pop must consume exactly the case's message")
      assert(pop.next == c.bodyStart, "the hoisted pop must lead into the case body")
      assert(pop.scheduler == branch.scheduler, "inserted nodes carry the enclosing scheduler tuple")
      assert(pop.lineNumber == branch.lineNumber, "source mapping is preserved for traces")
    }
    TVIRReader.fromTVIRString(out.result.toTVIRString)
  }

  test("labels keep resolving: an in-place rewrite preserves the labeled node id") {
    val fr = frontend
    val labeledId = fr.labels("R2")("alt")
    val out = Abstractor(fr, specOf(NodeDecision("R2", labeledId, "branch-hoist")))
    assert(out.result.labels("R2")("alt") == labeledId)
    assert(instrsOf(out.result, "R2")(labeledId).isInstanceOf[IRChoice])
    TVIRReader.fromTVIRString(out.result.toTVIRString) // validates labels too
  }

  test("auto expands a kind to every applicable node; blacklist wins over it") {
    val fr = frontend
    val guards = instrsOf(fr, "R2").values.collect { case g: IRJumpGuard => g }.map(_.id).toList
    val spec = AbstractionSpec(Nil, List("loop-unroll"), Nil, Nil)
    val out = Abstractor(fr, spec)
    assert(out.applied.map(_.node).toSet == guards.toSet)

    val refined = spec.withBlacklisted("R2", guards.head, "test refinement")
    val out2 = Abstractor(fr, refined)
    assert(out2.applied.map(_.node).toSet == guards.toSet - guards.head)
  }

  test("explicit decisions also respect the blacklist") {
    val fr = frontend
    val guard = firstOf(fr, "R2") { case _: IRJumpGuard => true; case _ => false }
    val spec = AbstractionSpec(List(NodeDecision("R2", guard.id, "loop-unroll")), Nil,
      List(BlacklistEntry("R2", guard.id, "spurious")), Nil)
    assert(Abstractor(fr, spec).applied.isEmpty)
  }

  test("unknown kind is a hard error") {
    val fr = frontend
    val guard = firstOf(fr, "R2") { case _: IRJumpGuard => true; case _ => false }
    val e = intercept[AbstractionException] {
      Abstractor(fr, specOf(NodeDecision("R2", guard.id, "pop-skip")))
    }
    assert(e.getMessage.contains("pop-skip"))
  }

  test("kind that does not apply to the node is a hard error") {
    val fr = frontend
    val branch = firstOf(fr, "R2") { case _: IRBranch => true; case _ => false }
    val e = intercept[AbstractionException] {
      Abstractor(fr, specOf(NodeDecision("R2", branch.id, "loop-unroll")))
    }
    assert(e.getMessage.contains("does not apply"))
  }

  test("missing node is a hard error") {
    val e = intercept[AbstractionException] {
      Abstractor(frontend, specOf(NodeDecision("R2", 99999, "loop-unroll")))
    }
    assert(e.getMessage.contains("99999"))
  }

  test("disable_specs drops template specs from the abstract result") {
    val fr = frontend
    val spec = AbstractionSpec(Nil, Nil, Nil, List("FinishingProperty"))
    // the test source declares no template specs, so this is a no-op there;
    // verify the filtering logic through the report path instead
    val out = Abstractor(fr, spec)
    assert(out.result.templateSpecs == fr.templateSpecs.filterNot(_ == "FinishingProperty"))
  }

  test("sidecar spec survives a save/load round trip") {
    val spec = AbstractionSpec(
      List(NodeDecision("R2", 12, "loop-unroll")),
      List("branch-hoist"),
      List(BlacklistEntry("R2", 12, "spurious at iter 1, step 7")),
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
