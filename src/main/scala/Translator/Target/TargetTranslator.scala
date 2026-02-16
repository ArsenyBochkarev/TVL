package Translator.Target

import Translator.IR.{IRInstruction, IRJumpGuard, IRParallelExec}

import scala.collection.mutable

trait TargetTranslator:
  def getChannelName(n: String): String = n.replaceAll("\\[", "_").replaceAll("]", "").replaceAll("[^a-zA-Z0-9_]", "_")
  def getMsgName(m: String): String = s"MSG_$m"
  def getSchedVarName(parallelBlockNum: Int, branchNum: Int): String =
    s"sched_block${parallelBlockNum}_branch$branchNum"
  // Guard vars for loops
  def collectGuardVars(instrs: Iterable[IRInstruction]): Set[(String, Int)] = {
    instrs.collect {
      case IRJumpGuard(_, _, _, v, _, i) => (v, i)
    }.toSet
  }
  // Scheduler vars for parallel blocks
  def collectSchedVars(instrs: Iterable[IRInstruction]): List[(Int, Int)] = {
    instrs.collect {
      case IRParallelExec(schedulerPc, _, b, _) => (schedulerPc, b.size)
    }.toList
  }
  def isParallel(inst: IRInstruction): Boolean = inst.scheduler._1 != -1
  def translate(actors: mutable.Map[String, mutable.Map[Int, IRInstruction]]): String
