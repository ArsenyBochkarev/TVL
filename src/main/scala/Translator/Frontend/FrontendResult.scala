package Translator.Frontend

import Translator.Frontend.UserSpec
import Translator.IR.IRInstruction
import scala.collection.mutable

case class FrontendResult(ir: mutable.Map[String, mutable.Map[Int, IRInstruction]], templateSpecs: List[String],
                          userSpecs: List[UserSpec], labels: Map[String, Map[String, Int]]) {

  /** Actors + instructions only */
  def toActorsTVIRString: String = FrontendResult.actorsToTVIR(ir)

  /** Full extended .tvir file */
  def toTVIRString: String =
    toActorsTVIRString + List(FrontendResult.templateSpecsSection(templateSpecs),
                              FrontendResult.userSpecsSection(userSpecs),
                              FrontendResult.labelsSection(labels)).flatten.mkString
}

/** Single source of truth for the .tvir text format. Also used by
  * Translator.Target.Curtis, which passes the IR through verbatim. */
object FrontendResult {
  def actorsToTVIR(ir: mutable.Map[String, mutable.Map[Int, IRInstruction]]): String =
    ir.toList.sortBy(_._1).map { case (actor, instrs) =>
      s"Actor: $actor\n" + instrs.toList.sortBy(_._1).map { case (id, instr) =>
        s"  $id: $instr"
      }.mkString("\n")
    }.mkString("\n\n") + "\n"

  def templateSpecsSection(templateSpecs: List[String]): Option[String] =
    Option.when(templateSpecs.nonEmpty)(
      "\nTemplate specs:\n" + templateSpecs.sorted.map(s => s"  $s").mkString("\n") + "\n")

  def userSpecsSection(userSpecs: List[UserSpec]): Option[String] =
    Option.when(userSpecs.nonEmpty)(
      "\nUser specs:\n" + userSpecs.sortBy(s => (s.name, s.formula)).map(s => s"  ${s.logic} ${s.name}: ${s.formula}").mkString("\n") + "\n")

  def labelsSection(labels: Map[String, Map[String, Int]]): Option[String] =
    Option.when(labels.nonEmpty)(
      "\nLabels:\n" + labels.toList.sortBy(_._1).flatMap { (actor, ls) =>
        ls.toList.sortBy(_._1).map { (label, id) => s"  $actor.$label: $id" }
      }.mkString("\n") + "\n")
}
