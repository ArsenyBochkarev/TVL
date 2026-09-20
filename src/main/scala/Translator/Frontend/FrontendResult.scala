package Translator.Frontend

import Translator.Frontend.UserSpec
import Translator.IR.IRInstruction
import scala.collection.mutable

case class FrontendResult(ir: mutable.Map[String, mutable.Map[Int, IRInstruction]], templateSpecs: List[String],
                          userSpecs: List[UserSpec], labels: Map[String, Map[String, Int]]) {

  /** Actors + instructions only */
  def toActorsTVIRString: String =
    ir.toList.sortBy(_._1).map { case (actor, instrs) =>
      s"Actor: $actor\n" + instrs.toList.sortBy(_._1).map { case (id, instr) =>
        s"  $id: $instr"
      }.mkString("\n")
    }.mkString("\n\n") + "\n"

  /** Full extended .tvir file */
  def toTVIRString: String =
    toActorsTVIRString + List(templateSpecsSection, userSpecsSection, labelsSection).flatten.mkString

  private def templateSpecsSection: Option[String] =
    Option.when(templateSpecs.nonEmpty)(
      "\nTemplate specs:\n" + templateSpecs.sorted.map(s => s"  $s").mkString("\n") + "\n")

  private def userSpecsSection: Option[String] =
    Option.when(userSpecs.nonEmpty)(
      "\nUser specs:\n" + userSpecs.sortBy(s => (s.name, s.formula)).map(s => s"  ${s.logic} ${s.name}: ${s.formula}").mkString("\n") + "\n")

  private def labelsSection: Option[String] =
    Option.when(labels.nonEmpty)(
      "\nLabels:\n" + labels.toList.sortBy(_._1).flatMap { (actor, ls) =>
        ls.toList.sortBy(_._1).map { (label, id) => s"  $actor.$label: $id" }
      }.mkString("\n") + "\n")
}