package Translator.Frontend

import Translator.Frontend.UserSpec
import Translator.IR.IRInstruction
import scala.collection.mutable

case class FrontendResult(ir: mutable.Map[String, mutable.Map[Int, IRInstruction]], templateSpecs: List[String],
                          userSpecs: List[UserSpec], labels: Map[String, Map[String, Int]])