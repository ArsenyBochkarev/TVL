package Translator.Abstraction

import Translator.Abstraction.AbstractionSpec
import Translator.Frontend.{FrontendResult, TVIRReader}
import Translator.IR.*
import Translator.IR.Lib.QueueCondition

import scala.collection.mutable
import scala.util.matching.Regex

/** One IR-to-IR abstraction pass. */
trait AbstractionPass {
  val kind: String

  /** All currently implemented passes are sound over-approximations; unsound
    * passes must be refused outright until they carry applicability
    * conditions. Kept as a flag so future passes can be rejected here. */
  val sound: Boolean

  /** Per-node passes: can this instruction be rewritten in place? */
  def appliesTo(i: IRInstruction): Boolean = false

  /** Per-node passes: rewrite the head instruction (keeping its id, so labels
    * stay resolvable) plus optional extra instructions. */
  def rewrite(actor: String, i: IRInstruction, freshId: () => Int): (IRInstruction, List[IRInstruction]) =
    throw new AbstractionException(s"$kind is not a per-node pass")

  /** Which nodes of an actor a decision may target; actor-level passes yield
    * the actor's entry id. */
  def candidateIds(instrs: mutable.Map[Int, IRInstruction]): List[Int] =
    instrs.values.filter(appliesTo).map(_.id).toList.sorted

  /** Model-global passes do not target an (actor, node) pair: one decision
    * per abstraction unit over the whole model. The decision's `actor` field
    * carries the unit key (for collapse-messages: the message class name) and
    * `node` is always 0. */
  def modelGlobal: Boolean = false

  /** Model-global passes: one decision per abstraction unit. */
  def globalCandidates(fr: FrontendResult): List[NodeDecision] = Nil

  /** Model-global passes: apply one decision to the live IR map in place.
    * Right(ids of all touched instructions) or Left(refusal reason) — a
    * refusal is always sound: less abstraction. */
  def rewriteModel(ir: mutable.Map[String, mutable.Map[Int, IRInstruction]],
                   decision: NodeDecision, freshId: () => Int): Either[String, List[Int]] =
    Left(s"$kind is not a model-global pass")
}

/** `repeat N` (IRJumpGuard) -> IRChoice{enter body, exit loop}: the unbounded
  * loop over-approximates at most N iterations and the counter disappears from
  * the state (targets discover guard vars by scanning for IRJumpGuard nodes;
  * Curtis initializes them lazily, so nothing dangles). */
object LoopUnrollPass extends AbstractionPass {
  val kind = "loop-unroll"
  val sound = true

  override def appliesTo(i: IRInstruction): Boolean = i match
    case _: IRJumpGuard => true
    case _              => false

  override def rewrite(actor: String, i: IRInstruction, freshId: () => Int): (IRInstruction, List[IRInstruction]) =
    i match
      case g: IRJumpGuard =>
        (IRChoice(g.id, g.lineNumber, g.scheduler, List(g.target, g.next)), Nil)
      case _ => throw new AbstractionException(s"$kind does not apply to instruction ${i.id}")
}

/** Slicing (actor removal): remove an actor that the specification does not
  * observe, together with every send to it (each such IRQueuePush becomes an
  * IRSkip with the same successor). This deletes whole state dimensions: the
  * actor's pc and the contents of its queues.
  *
  * Soundness conditions (violations are REFUSED with a reason, never silently
  * skipped, and never weaken the check by accident):
  *   - no user spec mentions the actor (atoms `Actor.label`);
  *   - no label is defined on the actor (it would dangle);
  *   - the actor sends nothing to actors that remain (removing such sends
  *     would remove behaviors of the receivers - an under-approximation);
  *   - template specs are regenerated from the sliced IR and would silently
  *     change meaning, so applying any slice DROPS them all (reported back,
  *     the driver reports "verified under the user specs only");
  *   - slicing away every actor is refused wholesale: the empty model
  *     satisfies any spec and any slice drops the template specs, so the
  *     verification would be vacuous (sound like any refusal: less
  *     abstraction).
  *
  * Note the remaining unsoundness gap this pass accepts: removing a send also
  * removes the sender's blocking on a full queue, which can remove stuck
  * behaviors. That matters only for LIVENESS properties about the senders;
  * the no-sends-to-kept-actors rule above is what keeps it away from the
  * actors anyone observes. */
object SliceActorPass extends AbstractionPass {
  val kind = "slice-actor"
  val sound = true

  /** One decision per actor: its entry node. */
  override def candidateIds(instrs: mutable.Map[Int, IRInstruction]): List[Int] =
    List(instrs.keys.min)
}

/** Message-class collapse: messages with the same behavioral fate - the set of
  * queues they are pushed to and the set of (node, queue) points that match
  * them - merge into one class message (`One1..One4` -> `One`). Every
  * IRQueuePush/IRQueuePop and every matching IRBranch case is rewritten to
  * the class name; several class cases of one alts merge into a single case
  * whose body is a fresh IRChoice between the original bodies (the targets
  * resolve alts cases nondeterministically, so the split body set is a plain
  * union of behaviors - an over-approximation). A queue holding k class
  * messages then has k+1 distinguishable contents instead of one per member
  * multiset.
  *
  * Soundness conditions (violations are REFUSED with a reason, never silently
  * skipped):
  *   - all-or-nothing: for every class S, branch B and queue q, every member
  *     pushed to q must be matched by B on q. `otherwise` is exclusive-else
  *     in the targets and an unmatched head is a real "wait" behavior:
  *     merging would let a classmate match a foreign case and DELETE the
  *     unmatched-arrival behavior (an under-approximation, a false VERIFIED);
  *   - a plain receive (IRQueuePop) waits for exactly one member at the head:
  *     if a classmate is pushed to the same queue the renamed receive would
  *     eat it, again deleting the wait behavior;
  *   - template specs are per message and would silently change meaning, so
  *     applying any collapse DROPS them all (reported back), exactly like
  *     slicing.
  *
  * The fate signature satisfies all-or-nothing by construction (equal matched
  * sets), so the clustering never proposes an unsound class. The explicit
  * guardrail re-check at rewrite time is what makes coarser clusterings
  * (substrings, queues only) safe to experiment with later: soundness lives
  * in the guardrail, not in the clustering heuristic.
  *
  * Decisions are per class - the decision's actor field carries the class
  * name, node is 0 - so refinement has a real gradient: blacklisting one
  * class keeps the others collapsed. */
object CollapseMessagesPass extends AbstractionPass {
  val kind = "collapse-messages"
  val sound = true
  override val modelGlobal: Boolean = true

  /** Where a message travels and where it is matched. */
  case class Fate(pushed: Set[String], matched: Set[(Int, String)])

  /** A named group of same-fate messages. */
  case class MessageClass(name: String, members: List[String])

  def fates(ir: mutable.Map[String, mutable.Map[Int, IRInstruction]]): Map[String, Fate] = {
    val pushed = mutable.Map.empty[String, Set[String]]
    val matched = mutable.Map.empty[String, Set[(Int, String)]]
    for (_, instrs) <- ir.toList.sortBy(_._1); (_, i) <- instrs.toList.sortBy(_._1) do
      i match
        case p: IRQueuePush => pushed(p.msg) = pushed.getOrElse(p.msg, Set.empty) + p.queueName
        case o: IRQueuePop  => matched(o.msg) = matched.getOrElse(o.msg, Set.empty) + ((o.id, o.queueName))
        case b: IRBranch =>
          b.cases.foreach(c => matched(c.msg) = matched.getOrElse(c.msg, Set.empty) + ((b.id, c.queueName)))
        case _ => ()
    (pushed.keySet ++ matched.keySet).map { m =>
      m -> Fate(pushed.getOrElse(m, Set.empty), matched.getOrElse(m, Set.empty))
    }.toMap
  }

  private def longestCommonPrefix(ms: List[String]): String =
    ms.foldLeft(ms.head) { (acc, m) =>
      acc.take(acc.zip(m).takeWhile(p => p._1 == p._2).length)
    }

  /** Messages grouped by fate into classes of two or more, named by the
    * longest common prefix of the members (extended with `_` until fresh wrt
    * every message and class name in the model). Deterministic. */
  def classes(ir: mutable.Map[String, mutable.Map[Int, IRInstruction]]): List[MessageClass] = {
    val fs = fates(ir)
    val taken = mutable.SortedSet.empty[String] ++ fs.keySet
    fs.toList.groupBy(_._2)
      .map((_, ms) => ms.map(_._1).sorted)
      .filter(_.size > 1).toList.sortBy(_.mkString("\u0000"))
      .map { members =>
        var name = longestCommonPrefix(members)
        if name.isEmpty then name = "cls"
        while taken.contains(name) do name += "_"
        taken += name
        MessageClass(name, members)
      }
  }

  override def globalCandidates(fr: FrontendResult): List[NodeDecision] =
    classes(fr.ir).map(c => NodeDecision(c.name, 0, kind))

  /** The all-or-nothing guardrail checked against the live IR: None when
    * merging `members` into one message is a sound over-approximation. */
  def guardrailViolation(ir: mutable.Map[String, mutable.Map[Int, IRInstruction]],
                         members: Set[String]): Option[String] = {
    val pushedTo = mutable.Map.empty[String, Set[String]] // queue -> members pushed there
    for (_, instrs) <- ir.toList.sortBy(_._1); (_, i) <- instrs.toList.sortBy(_._1) do
      i match
        case p: IRQueuePush if members(p.msg) =>
          pushedTo(p.queueName) = pushedTo.getOrElse(p.queueName, Set.empty) + p.msg
        case _ => ()
    val violations = List.newBuilder[String]
    for ((actor, instrs) <- ir.toList.sortBy(_._1); (id, i) <- instrs.toList.sortBy(_._1)) do
      i match
        case b: IRBranch =>
          b.cases.groupBy(_.queueName).foreach { (q, cs) =>
            val pushed = pushedTo.getOrElse(q, Set.empty)
            val cased = cs.map(_.msg).toSet intersect members
            val unmatched = pushed diff cased
            if cased.nonEmpty && unmatched.nonEmpty then
              violations += s"message(s) ${unmatched.toList.sorted.mkString(", ")} are pushed to $q but not " +
                s"matched by the receive alts at ${actor}@$id; merging would delete the unmatched-arrival " +
                "behavior (the class case would swallow the classmate)"
          }
        case o: IRQueuePop if members(o.msg) =>
          val classmates = pushedTo.getOrElse(o.queueName, Set.empty) - o.msg
          if classmates.nonEmpty then
            violations += s"${o.msg} is consumed by a plain receive at ${actor}@$id but classmate(s) " +
              s"${classmates.toList.sorted.mkString(", ")} are pushed to ${o.queueName}; the renamed receive " +
              "would eat them"
        case _ => ()
    violations.result().headOption
  }

  override def rewriteModel(ir: mutable.Map[String, mutable.Map[Int, IRInstruction]],
                            decision: NodeDecision,
                            freshId: () => Int): Either[String, List[Int]] =
    classes(ir).find(_.name == decision.actor) match
      case None =>
        Left(s"message class \"${decision.actor}\" does not exist in the current model " +
          "(other abstractions applied in this run may have changed it)")
      case Some(cls) =>
        guardrailViolation(ir, cls.members.toSet) match
          case Some(reason) => Left(reason)
          case None => Right(applyClass(ir, cls, freshId))

  private def applyClass(ir: mutable.Map[String, mutable.Map[Int, IRInstruction]],
                         cls: MessageClass, freshId: () => Int): List[Int] = {
    val members = cls.members.toSet
    val affected = List.newBuilder[Int]
    for (_, instrs) <- ir.toList.sortBy(_._1) do
      for (id, i) <- instrs.toList.sortBy(_._1) do
        i match
          case p: IRQueuePush if members(p.msg) =>
            instrs(id) = p.copy(msg = cls.name)
            affected += id
          case o: IRQueuePop if members(o.msg) =>
            // unreachable while clustering is the fate signature (a pop point
            // is unique to one message, so popped messages never share a
            // class); kept so the rename stays total under coarser clusterings
            instrs(id) = o.copy(msg = cls.name)
            affected += id
          case b: IRBranch =>
            val classCases = b.cases.zipWithIndex.filter((c, _) => members(c.msg))
            if classCases.nonEmpty then
              val byQueue = classCases.groupMap(_._1.queueName)(_._2)
              val drop = mutable.Set.empty[Int]
              val replace = mutable.Map.empty[Int, QueueCondition]
              val choices = List.newBuilder[IRInstruction]
              for (q, idxs) <- byQueue.toList.sortBy(_._1) do
                val first = idxs.min
                val bodies = idxs.sorted.map(i => b.cases(i).bodyStart).distinct
                val bodyStart =
                  if bodies.size > 1 then
                    val c = IRChoice(freshId(), b.lineNumber, b.scheduler, bodies)
                    choices += c
                    c.id
                  else bodies.head
                replace(first) = QueueCondition(q, cls.name, bodyStart)
                drop ++= idxs.filter(_ != first)
              val newCases = b.cases.zipWithIndex.collect {
                case (_, i) if replace.contains(i) => replace(i)
                case (c, i) if !drop.contains(i)   => c
              }
              instrs(id) = b.copy(cases = newCases)
              affected += id
              // the class-case bodies are part of the decision's footprint: a
              // spurious counterexample routed through a merged body must map
              // back to this decision when the driver refines
              classCases.map(_._1.bodyStart).distinct.foreach(affected += _)
              choices.result().foreach { c =>
                instrs(c.id) = c
                affected += c.id
              }
          case _ => ()
    affected.result()
  }
}

/** A decision that was applied: the concrete node it targeted, plus the ids of
  * instructions the pass touched besides the head (for slice-actor: the
  * replaced send nodes). The driver maps trace culprits at those ids back to
  * the decision when refining. */
case class AppliedDecision(actor: String, node: Int, kind: String, affected: List[Int]) {
  def decision: NodeDecision = NodeDecision(actor, node, kind)
}

case class AbstractorResult(result: FrontendResult,
                            applied: List[AppliedDecision],
                            refused: List[(NodeDecision, String)],
                            disabledSpecs: List[String])

object Abstractor {
  val passes: List[AbstractionPass] = List(LoopUnrollPass, SliceActorPass, CollapseMessagesPass)

  def passByKind(kind: String): Option[AbstractionPass] = passes.find(_.kind == kind)

  /** The receiver part of a queue name "Q[Receiver][Sender]". */
  def queueReceiver(queueName: String): String =
    queueName.stripPrefix("Q[").takeWhile(_ != ']')

  /** Does a formula mention the actor as an atom prefix (`Actor.`)? */
  def mentionsActor(formula: String, actor: String): Boolean =
    s"(?<![A-Za-z0-9_])${Regex.quote(actor)}\\.".r.findFirstIn(formula).isDefined

  /** All (actor, node) pairs a kind applies to — for the `auto` list. */
  def candidates(fr: FrontendResult, kind: String): List[NodeDecision] =
    passByKind(kind) match
      case None => throw new AbstractionException(s"unknown abstraction kind \"$kind\"")
      case Some(pass) =>
        if pass.modelGlobal then pass.globalCandidates(fr)
        else fr.ir.toList.sortBy(_._1).flatMap { (actor, instrs) =>
          pass.candidateIds(instrs).map(id => NodeDecision(actor, id, kind))
        }

  /** Which actors may be sliced, given the full candidate set: iterate the
    * guardrails to a fixpoint (an actor that sends to another candidate may be
    * sliced only together with it, and a refused candidate turns such sends
    * into violations for the sender too). */
  private def sliceableActors(fr: FrontendResult, wanted: List[String]): (Set[String], List[(String, String)]) = {
    var refused: List[(String, String)] = Nil
    // seed: actors the specs or labels observe can never be sliced
    var ok = wanted.toSet
    for actor <- wanted do
      if fr.labels.contains(actor) then
        refused ::= (actor, "the actor carries labels")
        ok = ok - actor
      else if fr.userSpecs.exists(us => mentionsActor(us.formula, actor)) then
        refused ::= (actor, "a user spec mentions the actor")
        ok = ok - actor
    // fixpoint: sends to kept actors block the sender
    var changed = true
    while changed do
      changed = false
      for actor <- ok.toList do
        val sendsToKept = fr.ir(actor).values.exists {
          case p: IRQueuePush => val r = queueReceiver(p.queueName)
                                  r != actor && !ok.contains(r)
          case _ => false
        }
        if sendsToKept then
          refused ::= (actor, "the actor sends to an actor that remains")
          ok = ok - actor
          changed = true
    (ok, refused)
  }

  def apply(fr: FrontendResult, spec: AbstractionSpec): AbstractorResult = {
    val blacklisted = spec.blacklisted

    for kind <- spec.auto do
      if passByKind(kind).isEmpty then
        throw new AbstractionException(s"unknown abstraction kind in \"auto\": \"$kind\"")

    // effective decisions: explicit ones, plus auto-expanded ones, minus the blacklist
    // keyed by (actor, node, kind): the same node may legitimately carry
    // decisions of different kinds (an actor's entry node is often also its
    // first loop guard), so (actor, node) alone would collapse them
    val autoDecisions = spec.auto.flatMap(candidates(fr, _))
    val explicit = spec.decisions
      .filterNot(d => blacklisted.contains((d.actor, d.node)))
      .map(d => (d.actor, d.node, d.kind) -> d).toMap
    val effective =
      (explicit ++ autoDecisions.filterNot(d => blacklisted.contains((d.actor, d.node)))
        .map(d => (d.actor, d.node, d.kind) -> d).filterNot((k, _) => explicit.contains(k))).values.toList

    // unknown kinds / wrong node types are hard errors, never silent skips
    effective.foreach { d =>
      val pass = passByKind(d.kind).getOrElse(
        throw new AbstractionException(s"unknown abstraction kind \"${d.kind}\" (actor ${d.actor}, node ${d.node})"))
      if !pass.sound then
        throw new AbstractionException(s"pass ${d.kind} is not a sound over-approximation")
      if pass.modelGlobal then
        // model-global passes are keyed by their unit (message class), not by a node
        if d.node != 0 then
          throw new AbstractionException(s"${d.kind} decisions must carry node 0, got ${d.node}")
        if !pass.globalCandidates(fr).exists(_.actor == d.actor) then
          throw new AbstractionException(s"unknown message class \"${d.actor}\" for ${d.kind}")
      // per-node passes must match the instruction; slice-actor accepts any
      // node of the actor and slices the actor as a whole
      else if d.kind != SliceActorPass.kind then
        val instr = fr.ir.get(d.actor).flatMap(_.get(d.node)).getOrElse(
          throw new AbstractionException(s"actor \"${d.actor}\" has no instruction ${d.node}"))
        if !pass.appliesTo(instr) then
          throw new AbstractionException(
            s"${d.kind} does not apply to actor ${d.actor} node ${d.node} (${instr.getClass.getSimpleName})")
      else if !fr.ir.getOrElse(d.actor, mutable.Map.empty).contains(d.node) then
        throw new AbstractionException(s"actor \"${d.actor}\" has no instruction ${d.node}")
    }

    // ---- slicing: which actors pass the guardrails -------------------------
    val sliceDecisions = effective.filter(_.kind == SliceActorPass.kind)
    val (passing, guardRefusals) = sliceableActors(fr, sliceDecisions.map(_.actor))
    // Guardrail: slicing away EVERY actor would make the verification vacuous
    // - the empty model satisfies any spec, and applying ANY slice already
    // drops all template specs. Refuse the whole pass then. If refinement
    // blacklists one actor's entry node, sliceable drops below fr.ir.size and
    // the remaining actors may be sliced in later iterations.
    val removesEverything =
      passing.size == sliceDecisions.map(_.actor).distinct.size && passing.size == fr.ir.size
    val sliceable = if removesEverything then Set.empty[String] else passing
    val sliceRefusals: List[(String, String)] =
      if removesEverything then
        sliceDecisions.map(_.actor).distinct.map(a => (a, "slicing would remove every actor"))
      else guardRefusals

    // deep-copy the IR (instructions are immutable case classes)
    val ir: mutable.Map[String, mutable.Map[Int, IRInstruction]] =
      mutable.Map.from(fr.ir.view.map { (actor, instrs) => actor -> mutable.Map.from(instrs) })

    val nextId = {
      var cur = ir.values.flatMap(_.keys).max
      () => { cur += 1; cur }
    }

    val applied = List.newBuilder[AppliedDecision]
    val refused = List.newBuilder[(NodeDecision, String)]
    sliceDecisions.foreach { d =>
      if sliceable.contains(d.actor) then
        // every send to the sliced actor becomes a skip with the same successor
        var affected = List.empty[Int]
        ir.foreach { (sender, instrs) =>
          if sender != d.actor then
            instrs.foreach { (id, instr) =>
              instr match
                case p: IRQueuePush if queueReceiver(p.queueName) == d.actor =>
                  instrs(id) = IRSkip(p.id, p.lineNumber, p.scheduler, p.next)
                  affected ::= id
                case _ => ()
            }
        }
        ir.remove(d.actor)
        applied += AppliedDecision(d.actor, d.node, d.kind, affected)
      else
        sliceRefusals.find(_._1 == d.actor) match
          case Some((_, reason)) => refused += ((d, reason))
          case None => () // duplicate decision for an already-refused actor
    }
    val sliced = sliceable

    // ---- per-node passes (decisions for sliced actors are moot) ------------
    effective.filter(d => d.kind != SliceActorPass.kind
      && !passByKind(d.kind).exists(_.modelGlobal)
      && !sliced.contains(d.actor)).foreach { d =>
      val instr = ir(d.actor)(d.node)
      val pass = passByKind(d.kind).get
      val (head, extra) = pass.rewrite(d.actor, instr, nextId)
      ir(d.actor)(d.node) = head
      extra.foreach(e => ir(d.actor)(e.id) = e)
      applied += AppliedDecision(d.actor, d.node, d.kind, extra.map(_.id))
    }

    // ---- model-global passes (message-class collapse), on the live IR ------
    // classes are recomputed after slicing/unrolling, so the fate signature
    // always matches the model the rewrite actually touches
    var collapsed = false
    effective.filter(d => passByKind(d.kind).exists(_.modelGlobal)).foreach { d =>
      val pass = passByKind(d.kind).get
      pass.rewriteModel(ir, d, nextId) match
        case Right(affected) =>
          applied += AppliedDecision(d.actor, d.node, d.kind, affected)
          collapsed = true
        case Left(reason) =>
          refused += ((d, reason))
    }

    // ---- template specs are meaningless on a sliced or collapsed model -----
    val droppedBy = List("slicing" -> sliced.nonEmpty, "message-class collapse" -> collapsed)
      .filter(_._2).map(_._1)
    val disabledSpecs = if droppedBy.nonEmpty then fr.templateSpecs else Nil
    if disabledSpecs.nonEmpty then
      println(s"warning: ${droppedBy.mkString(" + ")} drops all template specs " +
        s"(regenerated formulas would silently change meaning): ${disabledSpecs.mkString(", ")}")

    val templateSpecs = if droppedBy.nonEmpty then Nil
                        else fr.templateSpecs.filterNot(spec.disableSpecs.contains)
    spec.disableSpecs.filterNot(fr.templateSpecs.contains).foreach { s =>
      println(s"warning: disable_specs entry \"$s\" is not an enabled template spec")
    }

    val result = FrontendResult(ir, templateSpecs, fr.userSpecs, fr.labels)

    // Postcondition: the abstracted IR must stay structurally valid
    TVIRReader.fromTVIRString(result.toTVIRString)

    AbstractorResult(result, applied.result(), refused.result(), disabledSpecs)
  }
}
