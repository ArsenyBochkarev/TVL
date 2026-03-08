package Translator.Target

import Translator.IR.{IRInstruction, IRJumpGuard, IRParallelExec, IRQueuePush}
import Translator.UserSpec

import scala.collection.mutable

def targetIsValid(target: String): Boolean =
  target match {
    case "tla" => true
    case "spin" => true
    case _ => false
  }

trait TargetTranslator:
  private val allProperties: Set[String] = Set("all", "finishing", "msg", "validity")
  private var enabledProperties: Set[String] = Set("all")
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

  var userLabels: Map[String, Map[String, Int]] = Map.empty
  def setUserLabels(l: Map[String, Map[String, Int]]): Unit = {
    userLabels = l
  }

  def logicIsSupported(logic: String): Boolean
  def generateUserSpecs(specs: List[UserSpec], targetName: String): String = {
    val sb = new StringBuilder()
    specs.foreach { spec =>
      if logicIsSupported(spec.logic) then
        sb.append(formatLogic(spec.logic, spec.name, spec.formula))
        sb.append("\n")
      else
        println(s"[WARNING] Target '$targetName' does not support ${spec.logic} logic used by '${spec.name}' property. Skipping.")
    }
    sb.toString()
  }
  private def formatLogic(logic: String, name: String, formula: String): String =
    logic match
      case "ltl" => formatLTL(name, formula)
      case "ctl" => formatCTL(name, formula)
  def formatLTL(name: String, formula: String): String
  def formatCTL(name: String, formula: String): String

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
