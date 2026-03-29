package Translator.Target

import Translator.IR.*
import Translator.UserSpec

import scala.collection.mutable
import java.nio.file.Paths
import java.io.{FileWriter, PrintWriter, BufferedWriter}

class PlusCal extends TargetTranslator {
  private val indent = "    "
  private val and = "/\\"
  private val queueSize = 10 // TODO: we should be able to control it

  private var finishedProperties: List[String] = List.empty[String]
  override def getFinishingProperty: String =
    if (!isPropEnabled("finishing") || finishedProperties.isEmpty) ""
    else s"FinishingProperty == <>(${finishedProperties.mkString(s"\n$and ")})"
  private var msgDeliveredProperties: List[String] = List.empty[String]
  override def getMsgDeliveredProperty: String =
    if (!isPropEnabled("msg") || msgDeliveredProperties.isEmpty) ""
    else "MessageDeliveredProperty == " + msgDeliveredProperties.mkString(s"\n$and ")
  override def getValidityProperty: String =
    if (!isPropEnabled("validity") || finishedProperties.isEmpty) ""
    else
      val sb = new StringBuilder()
      sb.append(s"ValidityProperty == []((${finishedProperties.mkString(s"\n$and ")}) => (")
      channels.foreach { chan =>
        sb.append(s"Len(channels[\"$chan\"]) = 0 $and ")
      }
      sb.setLength(sb.length - 4)
      sb.append("))")
      sb.toString()

  override def generateUserSpecs(specs: List[UserSpec], targetName: String): String = {
    val sb = new StringBuilder()
    specs.foreach { spec =>
      if logicIsSupported(spec.logic) then
        sb.append(formatLTL(spec.name, spec.formula))
        sb.append("\n")
      else
        println(s"[WARNING] Target '$targetName' does not support ${spec.logic} logic used by '${spec.name}' property. Skipping.")
    }
    sb.append("=============================================================================\n")

    // We also need to add them to .cfg file
    val path = Paths.get(getOutputFile)
    val fileWithExt = path.toString
    val fileWithoutExt = fileWithExt.takeWhile(_ != '.')
    val fileName = fileWithoutExt + ".cfg"
    val writer = new BufferedWriter(new FileWriter(fileName, true))
    try {
      userSpecs.foreach { spec => writer.write(indent + s"$spec\n") }
    } finally {
      writer.close()
    }

    sb.toString()
  }

  private var channels: List[String] = List.empty[String]
  override def translate(actors: mutable.Map[String, mutable.Map[Int, IRInstruction]]): String = {
    val sb = new StringBuilder()

    val path = Paths.get(getOutputFile)
    val fileNameWithExt = path.getFileName.toString
    val outputFileName = fileNameWithExt.takeWhile(_ != '.')
    sb.append(s"----------------------------- MODULE $outputFileName -----------------------------\n")
    sb.append("EXTENDS Naturals, Sequences, TLC\n\n")
    sb.append("(* --algorithm test\n")

    sb.append("variables\n")
    val queues = collectGlobalInfo(actors)
    if (queues.nonEmpty) {
      val qInit = queues.map(q => s"${getChannelName(q)} |-> <<>>").mkString(", ")
      queues.foreach { q => channels = channels :+ getChannelName(q) }
      sb.append(s"${indent}channels = [ $qInit ];\n")
    } else {
      sb.append(s"${indent}channels = <<>>;\n")
    }
    sb.append("\n")

    actors.keys.toSeq.sorted.foreach { actorName =>
      sb.append(translateActor(actorName, actors(actorName)))
    }

    sb.append("end algorithm; *)\n")

    // We also need to generate .cfg file for TLA+
    val cfgFileContents = generateCfgFile()
    val fileWithExt = path.toString
    val fileWithoutExt = fileWithExt.takeWhile(_ != '.')
    val writer = new PrintWriter(fileWithoutExt + ".cfg")
    writer.write(cfgFileContents)
    writer.close()

    sb.toString()
  }

  private def translateActor(name: String, instructions: mutable.Map[Int, IRInstruction]): String = {
    val sb = new StringBuilder()
    sb.append(s"fair process $name = \"$name\"\n")

    sb.append("variables\n")
    // Declare finishing variable for current actor
    sb.append(indent + s"${name}_finished = FALSE,\n")
    // Declare message variables
    val msgVars = collectMsgVars(instructions.values)
    msgVars.foreach { msgName =>
      sb.append(indent + s"cur_msg_$msgName = \"\",\n")
    }
    // Declare loop guards
    val guardVars = collectGuardVars(instructions.values)
    guardVars.foreach { (v, n) =>
      sb.append(indent + s"$v = $n,\n")
    }
    // Declare scheduler helper variables
    val schedulerVars = collectSchedVars(instructions.values)
    // Each parallel block should have `n` helper variables
    schedulerVars.foreach { (schedulerPc, numBranches) =>
      for (j <- 1 to numBranches)
        sb.append(indent + s"${getSchedVarName(schedulerPc, j)} = 1,\n")
    }
    if (sb.endsWith(",\n"))
      sb.setLength(sb.length - 2)
    if (msgVars.nonEmpty || guardVars.nonEmpty || schedulerVars.nonEmpty)
      sb.append("\n")

    sb.append(";\n")
    sb.append("begin\n")

    // Traverse all instructions, translating them (almost) independently
    val sortedIds = instructions.keys.toSeq.sorted
    sortedIds.foreach { id =>
      // We don't insert user-defined labels for this target
      val labelName = s"L_$id"
      sb.append(s"$labelName:\n")
      val instr = instructions(id)
      getMapper.add(labelName, instr.lineNumber)
      instr match {
        case IRQueuePush(_, _, s, next, q, msg) =>
          val qName = getChannelName(q)
          val queue = s"channels[\"$qName\"]"
          if isParallel(instr) then
            sb.append(s"await Len($queue) < $queueSize; $queue := Append($queue, \"${getMsgName(msg)}\"); ${getSchedVarName(s._1, s._2)} := $next; goto L_${s._1};\n")
          else
            sb.append(indent + s"await Len($queue) < $queueSize; $queue := Append($queue, \"${getMsgName(msg)}\"); goto L_$next;\n")

        case IRQueuePop(_, _, s, next, q, msg) =>
          val qName = getChannelName(q)
          val queue = s"channels[\"$qName\"]"
          val msgStr = s"\"${getMsgName(msg)}\""
            if isParallel(instr) then
            sb.append(indent + s"await Len($queue) > 0 $and Head($queue) = $msgStr;\n")
            sb.append(indent + s"cur_msg_$msg := Head($queue); $queue := Tail($queue);\n")
            sb.append(indent + s"${getSchedVarName(s._1, s._2)} := $next;")
            sb.append(indent + s"goto L_${s._1};\n")
          else
            sb.append(indent + s"await Len($queue) > 0 $and Head($queue) = $msgStr;\n")
            sb.append(indent + s"cur_msg_$msg := Head($queue); $queue := Tail($queue);\n")
            sb.append(indent + s"goto L_$next;\n")
          msgDeliveredProperties = msgDeliveredProperties :+ s"(Len($queue) > 0 $and Head($queue) = $msgStr ~> cur_msg_$msg = $msgStr)"

        case IRJump(_, _, _, target) =>
          sb.append(s"goto L_$target;\n")

        case IRJumpGuard(_, _, s, next, guardVar, target, _) =>
          if isParallel(instr) then
            sb.append(indent + s"if $guardVar > 0 then\n")
            sb.append(indent * 2 + s"$guardVar := $guardVar - 1;\n")
            sb.append(indent * 2 + s" ${getSchedVarName(s._1, s._2)} := $target; goto L_${s._1};\n")
            sb.append(indent + s"else\n")
            sb.append(indent * 2 + s" ${getSchedVarName(s._1, s._2)} := $next; goto L_${s._1};\n")
            sb.append(indent + s"end if;\n")
          else
            sb.append(indent + s"if $guardVar > 0 then\n")
            sb.append(indent * 2 + s"$guardVar := $guardVar - 1;\n")
            sb.append(indent * 2 + s"goto L_$target;\n")
            sb.append(indent + s"else\n")
            sb.append(indent * 2 + s"goto L_$next;\n")
            sb.append(indent + s"end if;\n")

        case IRChoice(_, _, s, branches) =>
          sb.append(indent + s"either\n")
          if isParallel(instr) then
            branches.zipWithIndex.foreach { case (b, i) =>
              if (i > 0) sb.append(indent + s"or\n")
              sb.append(indent * 2 + s"${getSchedVarName(s._1, s._2)} := $b; goto L_${s._1};\n")
            }
          else
            branches.zipWithIndex.foreach { case (b, i) =>
              if (i > 0) sb.append(indent + s"or\n")
              sb.append(indent * 2 + s"goto L_$b;\n")
            }
          sb.append(indent + s"end either;\n")

        case IRBranch(_, _, s, cases, otherwise) =>
          sb.append(indent + "either\n")
          if isParallel(instr) then
            cases.zipWithIndex.foreach { case (c, i) =>
              if (i > 0)
                sb.append(indent + s"or\n")
              val qName = getChannelName(c.queueName)
              val queue = s"channels[\"$qName\"]"
              sb.append(indent * 2 + s"await Len($queue) > 0 $and Head($queue) = \"${getMsgName(c.msg)}\";\n")
              sb.append(indent * 2 + s"cur_msg_${c.msg} := Head($queue); $queue := Tail($queue);\n")
              sb.append(indent * 2 + s"${getSchedVarName(s._1, s._2)} := ${c.bodyStart};\n")
              val msgStr = s"\"${getMsgName(c.msg)}\""
              msgDeliveredProperties = msgDeliveredProperties :+ s"(Len($queue) > 0 $and Head($queue) = $msgStr ~> cur_msg_${c.msg} = $msgStr)"
              sb.append(indent * 2 + s"goto L_${s._1};\n")
            }
          else
            cases.zipWithIndex.foreach { case (c, i) =>
              if (i > 0)
                sb.append(indent + s"or\n")
              val qName = getChannelName(c.queueName)
              val queue = s"channels[\"$qName\"]"
              sb.append(indent * 2 + s"await Len($queue) > 0 $and Head($queue) = \"${getMsgName(c.msg)}\";\n")
              sb.append(indent * 2 + s"cur_msg_${c.msg} := Head($queue); $queue := Tail($queue);\n")
              val msgStr = s"\"${getMsgName(c.msg)}\""
              msgDeliveredProperties = msgDeliveredProperties :+ s"(Len($queue) > 0 $and Head($queue) = $msgStr ~> cur_msg_${c.msg} = $msgStr)"
              sb.append(indent * 2 + s"goto L_${c.bodyStart};\n")
            }
          sb.append(indent + "end either;\n")

          otherwise match {
            case Some(otherwiseStart) =>
              // Negation for all other conditions
              sb.append(indent + s"or\n")
              val otherwiseGuard = cases.map { c =>
                val qName = getChannelName(c.queueName)
                val queue = s"channels[\"$qName\"]"
                s"(Len($queue) = 0 \\/ Head($queue) /= \"${getMsgName(c.msg)}\")"
              }.mkString(s" $and ")

              if (otherwiseGuard.nonEmpty) {
                sb.append(indent * 2 + s"await $otherwiseGuard;\n")
              }
              if isParallel(instr) then
                sb.append(indent * 2 + s"${getSchedVarName(s._1, s._2)} := $otherwiseStart;\n")
                sb.append(indent * 2 + s"goto L_${s._1};\n")
              else
                sb.append(indent * 2 + s"goto L_$otherwiseStart;\n")
            case None =>
          }

        case IRSkip(_, _, _, next) =>
          sb.append(s"skip; goto L_$next;\n")

        case IRParallelExec(schedulerPc, _, _, branches, breakExit) =>
          sb.append(indent + "either\n")
          var branchNumber = 1
          branches.zipWithIndex.foreach { (b, i) =>
            if (i > 0) sb.append(indent + "or\n")
            // Parallel branch choice
            sb.append(indent * 2 + s"await ${getSchedVarName(schedulerPc, branchNumber)} /= 0;\n")

            // Set correct step for current branch
            sb.append(indent * 2 + "either\n")
            sb.append(indent * 3 + s"await ${getSchedVarName(schedulerPc, branchNumber)} = 1; goto L_$b;\n")
            // Traverse CFG until IRParallelEnd (BFS)
            val branchStart = instructions(b)
            val visited = mutable.Set[Int]()
            val queue = mutable.Queue[Int]()
            branchStart.successors.foreach { succ =>
              if (!visited.contains(succ) && !branchStart.isInstanceOf[IRParallelEnd])
                queue.enqueue(succ)
            }
            while (queue.nonEmpty) {
              val succId = queue.dequeue()
              if (!visited.contains(succId) && instructions.contains(succId)) {
                visited.add(succId)
                val nextInstr = instructions(succId)
                sb.append(indent * 2 + "or\n")
                sb.append(indent * 3 + s"await ${getSchedVarName(schedulerPc, branchNumber)} = ${nextInstr.id}; goto L_${nextInstr.id};\n")

                nextInstr.successors.foreach { succ =>
                  if (!visited.contains(succ) && !nextInstr.isInstanceOf[IRParallelEnd])
                    queue.enqueue(succ)
                }
              }
            }
            branchNumber += 1
            sb.append(indent * 2 + "end either;\n")
          }
          sb.append(indent + "or\n")
          sb.append(indent * 2 + "await ")
          for (bn <- 1 until branchNumber)
            sb.append(s"${getSchedVarName(schedulerPc, bn)} = 0 $and ")
          sb.append("TRUE;\n") // TODO: we can actually dispose of this
          sb.append(indent * 2 + s"goto L_$breakExit;\n")
          sb.append(indent + "end either;\n")

        case IRParallelEnd(_, _, s, joinPc) =>
          sb.append(s"${getSchedVarName(s._1, s._2)} := 0; goto L_${s._1};\n")

        case IREnd(_, _, _) =>
          sb.append(s"goto L_END_ACTOR_$name;\n")
      }
    }

    sb.append(s"L_END_ACTOR_$name:\n")
    sb.append(indent + s"${name}_finished := TRUE;\n")
    finishedProperties = finishedProperties :+ s"(${name}_finished = TRUE)"
    sb.append("end process;\n\n")
    sb.toString()
  }

  private def collectGlobalInfo(actors: mutable.Map[String, mutable.Map[Int, IRInstruction]]): Set[String] = {
    val queues = mutable.Set[String]()
    actors.values.foreach(_.values.foreach {
      case IRQueuePush(_, _, _, _, q, m) => queues.add(q);
      case IRQueuePop(_, _, _, _, q, m) => queues.add(q);
      case IRBranch(_, _, _, cases, _) => cases.foreach { c => queues.add(c.queueName) }
      case _ =>
    })
    queues.toSet
  }

  override def logicIsSupported(logic: String): Boolean =
    logic match
      case "ltl" => true
      case "ctl" => false
  private var userSpecs: List[String] = List()
  override def formatLTL(name: String, formula: String): String =
    // For PlusCal target we also need to store formula's name to generate .cfg file
    userSpecs = name :: userSpecs

    // Normalize 'G' and 'F', if any
    val normalizedFormula = formula
      .replaceAll("\\bG\\b", "[]")
      .replaceAll("\\bF\\b", "<>")

    // Patterns <actor name>.<label>
    val pattern = "([a-zA-Z_0-9]+)\\.([a-zA-Z_0-9]+)".r

    // pc[<actor name>] = <label for corresponding instruction ID>
    // Inserting user-defined label breaks (a little) atomic semantics for labels
    var tlaFormula = pattern.replaceAllIn(normalizedFormula, m => {
      val actor = m.group(1)
      val label = m.group(2)
      val id = userLabels.get(actor).flatMap(_.get(label))

      if (id.isDefined) {
        s"""(pc["$actor"] = "L_${id.get}")"""
      } else {
        println(s"[WARNING] Label $actor.$label not found! Property might fail to compile.")
        s"""(pc["$actor"] = "$label")"""
      }
    })
    tlaFormula = tlaFormula
      .replace("->", "=>")
      .replace("&&", "/\\")
      .replace("||", "\\/")
      .replace("!", "~")

    s"$name == $tlaFormula"
  override def formatCTL(name: String, formula: String): String =
    println("Error: CTL is not supported for PlusCal target")
    System.exit(1)
    ""

  private def generateCfgFile(): String = {
    val sb = new StringBuilder()
    sb.append("SPECIFICATION Spec\n\n")

    val properties = mutable.ListBuffer[String]()
    if (isPropEnabled("finishing") && finishedProperties.nonEmpty)
      properties.append("FinishingProperty")
    if (isPropEnabled("msg") && msgDeliveredProperties.nonEmpty)
      properties.append("MessageDeliveredProperty")
    if (isPropEnabled("validity") && finishedProperties.nonEmpty)
      properties.append("ValidityProperty")

    if (properties.nonEmpty) {
      sb.append("PROPERTIES\n")
      properties.foreach { p => sb.append(indent + s"$p\n") }
    }
    sb.toString()
  }
}