package Translator.Target

import Translator.Frontend.{FrontendResult, UserSpec}
import Translator.IR.IRInstruction

import scala.collection.mutable

/**
  * Curtis (https://github.com/ArsenyBochkarev/Curtis) is a TVL-specific model checker
  * that consumes the TVL IR itself, so this target is basically a no-op
  */
class Curtis extends TargetTranslator {
  // Raw list from setEnabledProperties (Main passes res.templateSpecs); the trait keeps
  // it private, but the dump must list the names verbatim rather than the expanded set
  private var templateProps: List[String] = List.empty

  override def setEnabledProperties(props: List[String]): Unit = {
    super.setEnabledProperties(props)
    templateProps = props
  }

  override def translate(actors: mutable.Map[String, mutable.Map[Int, IRInstruction]]): String =
    FrontendResult.actorsToTVIR(actors).dropRight(1) // println supplies the newline

  override def generateTemplateSpecs: String =
    FrontendResult.templateSpecsSection(templateProps).map(_.dropRight(1)).getOrElse("")

  override def generateUserSpecs(specs: List[UserSpec], targetName: String): String = {
    val parts = List(
      FrontendResult.userSpecsSection(specs),
      FrontendResult.labelsSection(userLabels)).flatten
    parts.mkString.dropRight(1) // "" when there are no specs and no labels
  }

  // Curtis checks both LTL and CTL formulas itself; the formulas go into the dump verbatim
  override def logicIsSupported(logic: String): Boolean = true
  // generateUserSpecs already preserves the desired format
  override def formatLTL(name: String, formula: String): String = formula
  override def formatCTL(name: String, formula: String): String = formula

  // generateUserSpecs already preserves the desired format
  override def getFinishingProperty: String = ""
  override def getMsgDeliveredProperty: String = ""
  override def getValidityProperty: String = ""
}
