package Translator.Target

import Translator.IR.IRInstruction
import scala.collection.mutable

trait TargetTranslator:
  def translate(actors: mutable.Map[String, mutable.Map[Int, IRInstruction]]): String
