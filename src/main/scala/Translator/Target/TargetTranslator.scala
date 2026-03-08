package Translator.Target

import Translator.IR.{IRInstruction, IRJumpGuard, IRParallelExec, IRQueuePush}

import scala.collection.mutable

def targetIsValid(target: String): Boolean =
  target match {
    case "tla" => true
    case "spin" => true
    case _ => false
  }

trait TargetTranslator:
  private val allProperties: Set[String] = Set("all", "finishing", "msg", "validity")
  var enabledProperties: Set[String] = Set("all")
  def setEnabledProperties(props: String): Unit = {
    enabledProperties = props.split(",").map(_.trim.toLowerCase).toSet
    for (x <- enabledProperties)
      if (!allProperties.contains(x))
        println(s"Error: erroneous name for generating property. Possible values: " + allProperties.toString())
        System.exit(1)
  }
  def isPropEnabled(prop: String): Boolean = {
    enabledProperties.contains("all") || enabledProperties.contains(prop)
  }

  // TODO: check if we should move it to Promela target
  def getChannelName(n: String): String = n.replaceAll("\\[", "_").replaceAll("]", "").replaceAll("[^a-zA-Z0-9_]", "_")
  def getMsgName(m: String): String = s"MSG_$m"
  // Messages variables
  def collectMsgVars(instrs: Iterable[IRInstruction]): Set[String] = {
    instrs.collect {
      case IRQueuePush(_, _, _, _, name) => name
    }.toSet
  }
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
  def getMsgDeliveredProperty: String // Part of validity property
  def getFinishingProperty: String // Both finishing property and progress (no deadlocks)
  def getValidityProperty: String // If protocol ends, all queues should be empty
  def translate(actors: mutable.Map[String, mutable.Map[Int, IRInstruction]]): String
