package Translator.CEGAR

import Translator.CEGAR.Json.*

import java.nio.file.{Files, Path}
import scala.collection.mutable

class AbstractionException(message: String) extends RuntimeException(s"Error: invalid abstraction spec: $message")

/** One per-node abstraction decision: apply the pass `kind` to instruction
  * `node` of `actor`. */
case class NodeDecision(actor: String, node: Int, kind: String)

/** Refinement output: never abstract this node again (wins over decisions). */
case class BlacklistEntry(actor: String, node: Int, reason: String)

/** The `tvl-abstraction/1` sidecar driving the CEGAR loop.
  *
  * Format:
  * {{{
  * {
  *   "format": "tvl-abstraction/1",
  *   "decisions": [ { "actor": "R2", "node": 12, "kind": "loop-unroll" } ],
  *   "auto": [ "loop-unroll", "slice-actor", "collapse-messages" ],  // kinds applied
  *                                                                   // to every applicable, non-blacklisted unit
  *   "blacklist": [ { "actor": "R2", "node": 12, "reason": "spurious at iter 1, step 7" } ],
  *   "disable_specs": []
  * }
  * }}}
  */
case class AbstractionSpec(decisions: List[NodeDecision],
                           auto: List[String],
                           blacklist: List[BlacklistEntry],
                           disableSpecs: List[String]) {
  def blacklisted: Set[(String, Int)] = blacklist.map(e => (e.actor, e.node)).toSet

  /** Adds/updates a blacklist entry (refinement step) and returns the new spec. */
  def withBlacklisted(actor: String, node: Int, reason: String): AbstractionSpec = {
    val kept = blacklist.filterNot(e => e.actor == actor && e.node == node)
    AbstractionSpec(decisions, auto, kept :+ BlacklistEntry(actor, node, reason), disableSpecs)
  }

  def toJson: JObj = {
    def decision(d: NodeDecision): JValue =
      JObj(List("actor" -> JStr(d.actor), "node" -> JNum(d.node), "kind" -> JStr(d.kind)))
    def entry(e: BlacklistEntry): JValue =
      JObj(List("actor" -> JStr(e.actor), "node" -> JNum(e.node), "reason" -> JStr(e.reason)))
    JObj(List(
      "format" -> JStr(AbstractionSpec.Format),
      "decisions" -> JArr(decisions.map(decision)),
      "auto" -> JArr(auto.map(JStr.apply)),
      "blacklist" -> JArr(blacklist.map(entry)),
      "disable_specs" -> JArr(disableSpecs.map(JStr.apply)),
    ))
  }
}

object AbstractionSpec {
  val Format = "tvl-abstraction/1"

  def empty: AbstractionSpec = AbstractionSpec(Nil, Nil, Nil, Nil)

  def parse(text: String): AbstractionSpec = {
    val root = Json.parse(text) match
      case obj: JObj => obj
      case _         => throw new AbstractionException("top-level value must be an object")

    root.field("format").flatMap(_.asString) match
      case Some(f) if f == Format =>
      case other => throw new AbstractionException(s"expected format \"$Format\", got ${other.getOrElse("missing")}")

    def strList(name: String): List[String] =
      root.field(name).map(_.items.flatMap(_.asString)).getOrElse(Nil)

    val decisions = root.field("decisions").map(_.items.map { d =>
      (for
        actor <- d.field("actor").flatMap(_.asString)
        node <- d.field("node").flatMap(_.asInt)
        kind <- d.field("kind").flatMap(_.asString)
      yield NodeDecision(actor, node, kind)).getOrElse(
        throw new AbstractionException(s"malformed decision entry (need actor/node/kind): ${Json.print(d)}"))
    }).getOrElse(Nil)

    val blacklist = root.field("blacklist").map(_.items.map { e =>
      (for
        actor <- e.field("actor").flatMap(_.asString)
        node <- e.field("node").flatMap(_.asInt)
        reason <- e.field("reason").flatMap(_.asString)
      yield BlacklistEntry(actor, node, reason)).getOrElse(
        throw new AbstractionException(s"malformed blacklist entry (need actor/node/reason): ${Json.print(e)}"))
    }).getOrElse(Nil)

    val dupDecisions = decisions.groupMapReduce(d => (d.actor, d.node))(_ => 1)(_ + _).filter(_._2 > 1).keys
    if dupDecisions.nonEmpty then
      throw new AbstractionException(s"duplicate decisions for ${dupDecisions.mkString(", ")}")

    AbstractionSpec(decisions, strList("auto"), blacklist, strList("disable_specs"))
  }

  def load(path: String): AbstractionSpec = {
    val text =
      try Files.readString(Path.of(path))
      catch case _: java.io.IOException => throw new AbstractionException(s"cannot read \"$path\"")
    parse(text)
  }

  def save(spec: AbstractionSpec, path: String): Unit = {
    val parent = Path.of(path).getParent
    if parent != null then Files.createDirectories(parent)
    Files.writeString(Path.of(path), Json.print(spec.toJson) + "\n")
  }
}
