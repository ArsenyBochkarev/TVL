package Translator.Abstraction

import Translator.Abstraction.AbstractionSpec
import Translator.Frontend.{FrontendResult, TVIRReader}
import Translator.IR.*
import Translator.IR.Lib.QueueCondition

import scala.collection.mutable

/** One IR-to-IR abstraction pass. A pass rewrites the head instruction
  * (keeping its id, so labels stay resolvable) and may insert extra
  * instructions that carry the same scheduler tuple. */
trait AbstractionPass {
  val kind: String

  /** All currently implemented passes are sound over-approximations; unsound
    * passes must be refused outright until they carry applicability
    * conditions. Kept as a flag so future passes can be rejected here. */
  val sound: Boolean

  def appliesTo(i: IRInstruction): Boolean
  def rewrite(actor: String, i: IRInstruction, freshId: () => Int): (IRInstruction, List[IRInstruction])
}

/** `repeat N` (IRJumpGuard) -> IRChoice{enter body, exit loop}: the unbounded
  * loop over-approximates at most N iterations and the counter disappears from
  * the state (targets discover guard vars by scanning for IRJumpGuard nodes;
  * Curtis initializes them lazily, so nothing dangles). */
object LoopUnrollPass extends AbstractionPass {
  val kind = "loop-unroll"
  val sound = true

  def appliesTo(i: IRInstruction): Boolean = i match
    case _: IRJumpGuard => true
    case _              => false

  def rewrite(actor: String, i: IRInstruction, freshId: () => Int): (IRInstruction, List[IRInstruction]) =
    i match
      case g: IRJumpGuard =>
        (IRChoice(g.id, g.lineNumber, g.scheduler, List(g.target, g.next)), Nil)
      case _ => throw new AbstractionException(s"$kind does not apply to instruction ${i.id}")
}

/** `receive alts` (IRBranch) -> IRChoice with a consuming IRQueuePop hoisted
  * as the first node of every case body. The plain IRChoice-over-bodyStarts
  * rewrite is UNSOUND: IRBranch consumes the matched message, and dropping
  * the consumption leaves it stuck at the FIFO head, blocking later receives
  * (under-approximation — real behaviors would go missing). Keeping the pop
  * makes the abstract branch commit early and then block at the pop until
  * the message arrives: a genuine over-approximation. The `otherwise` arm is
  * kept as an always-enabled choice branch (superset of "enabled when no case
  * matches"). */
object BranchHoistPass extends AbstractionPass {
  val kind = "branch-hoist"
  val sound = true

  def appliesTo(i: IRInstruction): Boolean = i match
    case _: IRBranch => true
    case _           => false

  def rewrite(actor: String, i: IRInstruction, freshId: () => Int): (IRInstruction, List[IRInstruction]) =
    i match
      case b: IRBranch =>
        val pops = b.cases.map { c =>
          IRQueuePop(freshId(), b.lineNumber, b.scheduler, c.bodyStart, c.queueName, c.msg)
        }
        (IRChoice(b.id, b.lineNumber, b.scheduler, pops.map(_.id) ++ b.otherwise.toList), pops)
      case _ => throw new AbstractionException(s"$kind does not apply to instruction ${i.id}")
}

/** A decision that was applied: the concrete node that was rewritten, plus the
  * ids of instructions the pass inserted (fresh ids absent from the concrete
  * IR — the driver maps trace culprits at inserted nodes back to `node`). */
case class AppliedDecision(actor: String, node: Int, kind: String, inserted: List[Int]) {
  def decision: NodeDecision = NodeDecision(actor, node, kind)
}

case class AbstractorResult(result: FrontendResult,
                            applied: List[AppliedDecision],
                            refused: List[(NodeDecision, String)])

object Abstractor {
  val passes: List[AbstractionPass] = List(LoopUnrollPass, BranchHoistPass)

  def passByKind(kind: String): Option[AbstractionPass] = passes.find(_.kind == kind)

  /** All (actor, node) pairs a kind applies to — for the `auto` list and for
    * reporting the refinement bound. */
  def candidates(fr: FrontendResult, kind: String): List[NodeDecision] =
    passByKind(kind) match
      case None => throw new AbstractionException(s"unknown abstraction kind \"$kind\"")
      case Some(pass) =>
        fr.ir.toList.flatMap { (actor, instrs) =>
          instrs.values.filter(pass.appliesTo).toList
            .sortBy(_.id)
            .map(i => NodeDecision(actor, i.id, kind))
        }

  def apply(fr: FrontendResult, spec: AbstractionSpec): AbstractorResult = {
    val blacklisted = spec.blacklisted

    for kind <- spec.auto do
      if passByKind(kind).isEmpty then
        throw new AbstractionException(s"unknown abstraction kind in \"auto\": \"$kind\"")

    // effective decisions: explicit ones, plus auto-expanded ones, minus the blacklist
    val autoDecisions = spec.auto.flatMap(candidates(fr, _))
      .filterNot(d => blacklisted.contains((d.actor, d.node)))
    val explicit = spec.decisions
      .filterNot(d => blacklisted.contains((d.actor, d.node)))
      .map(d => (d.actor, d.node) -> d).toMap
    val effective =
      (explicit ++ autoDecisions.map(d => (d.actor, d.node) -> d).filterNot((k, _) => explicit.contains(k))).values.toList

    // unknown kinds / wrong node types are hard errors, never silent skips
    effective.foreach { d =>
      val pass = passByKind(d.kind).getOrElse(
        throw new AbstractionException(s"unknown abstraction kind \"${d.kind}\" (actor ${d.actor}, node ${d.node})"))
      val instr = fr.ir.get(d.actor).flatMap(_.get(d.node)).getOrElse(
        throw new AbstractionException(s"actor \"${d.actor}\" has no instruction ${d.node}"))
      if !pass.appliesTo(instr) then
        throw new AbstractionException(
          s"${d.kind} does not apply to actor ${d.actor} node ${d.node} (${instr.getClass.getSimpleName})")
      if !pass.sound then
        throw new AbstractionException(s"pass ${d.kind} is not a sound over-approximation")
    }

    // deep-copy the IR (instructions are immutable case classes)
    val ir: mutable.Map[String, mutable.Map[Int, IRInstruction]] =
      mutable.Map.from(fr.ir.view.map { (actor, instrs) => actor -> mutable.Map.from(instrs) })

    val nextId = {
      var cur = ir.values.flatMap(_.keys).max
      () => { cur += 1; cur }
    }

    val applied = List.newBuilder[AppliedDecision]
    effective.foreach { d =>
      val instr = ir(d.actor)(d.node)
      val pass = passByKind(d.kind).get
      val (head, extra) = pass.rewrite(d.actor, instr, nextId)
      ir(d.actor)(d.node) = head
      extra.foreach(e => ir(d.actor)(e.id) = e)
      applied += AppliedDecision(d.actor, d.node, d.kind, extra.map(_.id))
    }

    val templateSpecs =
      val dropped = fr.templateSpecs.filter(spec.disableSpecs.contains)
      dropped.foreach(s => println(s"warning: template spec \"$s\" disabled by the abstraction spec"))
      fr.templateSpecs.filterNot(spec.disableSpecs.contains)

    val result = FrontendResult(ir, templateSpecs, fr.userSpecs, fr.labels)

    // Postcondition: the abstracted IR must stay structurally valid
    TVIRReader.fromTVIRString(result.toTVIRString)

    AbstractorResult(result, applied.result(), Nil)
  }
}
