package Translator.Target

import Translator.IR.IRInstruction
import scala.collection.mutable

trait TargetTranslator:
  def isParallel(inst: IRInstruction): Boolean = inst.scheduler._1 != -1
  def translate(actors: mutable.Map[String, mutable.Map[Int, IRInstruction]]): String
