package Translator.Target

import Translator.IR.*
import scala.collection.mutable

class PlusCal extends TargetTranslator {
  private val indent = "    "
  private val and = "/\\"
  private val queueSize = 10 // TODO: we should be able to control it

  override def translate(actors: mutable.Map[String, mutable.Map[Int, IRInstruction]]): String = {
    val sb = new StringBuilder()

    sb.append("----------------------------- MODULE test -----------------------------\n")
    sb.append("EXTENDS Naturals, Sequences, TLC\n\n")
    sb.append("(* --algorithm test\n")

    sb.append("variables\n")
    val queues = collectGlobalInfo(actors)
    if (queues.nonEmpty) {
      val qInit = queues.map(q => s"${getChannelName(q)} |-> <<>>").mkString(", ")
      sb.append(s"${indent}queues = [ $qInit ];\n")
    } else {
      sb.append(s"${indent}queues = <<>>;\n")
    }
    sb.append("\n")

    actors.keys.toSeq.sorted.foreach { actorName =>
      sb.append(translateActor(actorName, actors(actorName)))
    }

    sb.append("end algorithm; *)\n")
    sb.append("=============================================================================\n")
    sb.toString()
  }

  private def translateActor(name: String, instructions: mutable.Map[Int, IRInstruction]): String = {
    val sb = new StringBuilder()
    sb.append(s"process $name = \"$name\"\n")

    // Tmp variable. TODO: check if we really need it
    sb.append("variables\n")
    sb.append(indent + s"cur_msg = \"\",\n")
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
    if (sb.endsWith(",\n")) sb.setLength(sb.length - 2)
    if (guardVars.nonEmpty || schedulerVars.nonEmpty) sb.append("\n")

    sb.append(";\n")
    sb.append("begin\n")

    // Traverse all instructions, translating them (almost) independently
    val sortedIds = instructions.keys.toSeq.sorted
    sortedIds.foreach { id =>
      sb.append(s"L_$id:\n")
      val instr = instructions(id)
      instr match {
        case IRQueuePush(_, s, next, q, msg) =>
          val qName = getChannelName(q)
          val queue = s"queues[\"$qName\"]"
          if isParallel(instr) then
            sb.append(s"$queue := Append($queue, \"${getMsgName(msg)}\"); ${getSchedVarName(s._1, s._2)} := $next; goto L_${s._1};\n")
          else
            sb.append(indent + s"$queue := Append($queue, \"${getMsgName(msg)}\"); goto L_$next;\n")

        case IRQueuePop(_, s, next, q, msg) =>
          val qName = getChannelName(q)
          val queue = s"queues[\"$qName\"]"
          if isParallel(instr) then
            sb.append(indent + s"await Len($queue) > 0 $and Head($queue) = \"${getMsgName(msg)}\";\n")
            sb.append(indent + s"cur_msg := Head($queue); $queue := Tail($queue);\n")
            sb.append(indent + s"${getSchedVarName(s._1, s._2)} := $next; goto L_${s._1}\n")
          else
            sb.append(indent + s"await Len($queue) > 0 $and Head($queue) = \"${getMsgName(msg)}\";\n")
            sb.append(indent + s"cur_msg := Head($queue); $queue := Tail($queue);\n")
            sb.append(indent + s"goto L_$next;\n")

        case IRJump(_, _, target) =>
          sb.append(s"goto L_$target;\n")

        case IRJumpGuard(_, s, next, guardVar, target, _) =>
          if isParallel(instr) then
            sb.append(indent + s"if $guardVar > 0 then\n")
            sb.append(indent * 2 + s"$guardVar := $guardVar - 1;\n")
            sb.append(indent * 2 + s" ${getSchedVarName(s._1, s._2)} := $target; goto L_${s._1}\n")
            sb.append(indent + s"else\n")
            sb.append(indent * 2 + s" ${getSchedVarName(s._1, s._2)} := $next; goto L_${s._1}\n")
            sb.append(indent + s"end if;\n")
          else
            sb.append(indent + s"if $guardVar > 0 then\n")
            sb.append(indent * 2 + s"$guardVar := $guardVar - 1;\n")
            sb.append(indent * 2 + s"goto L_$target;\n")
            sb.append(indent + s"else\n")
            sb.append(indent * 2 + s"goto L_$next;\n")
            sb.append(indent + s"end if;\n")

        case IRChoice(_, s, branches) =>
          sb.append(indent + s"either\n")
          if isParallel(instr) then
            branches.zipWithIndex.foreach { case (b, i) =>
              if (i > 0) sb.append(indent + s"or\n")
              sb.append(indent * 2 + s"${getSchedVarName(s._1, s._2)} := $b; goto L_${s._1}\n")
            }
          else
            branches.zipWithIndex.foreach { case (b, i) =>
              if (i > 0) sb.append(indent + s"or\n")
              sb.append(indent * 2 + s"goto L_$b;\n")
            }
          sb.append(indent + s"end either;\n")

        case IRBranch(_, s, cases, otherwise) =>
          sb.append(indent + "either\n")
          if isParallel(instr) then
            cases.zipWithIndex.foreach { case (c, i) =>
              if (i > 0) sb.append(indent + s"or\n")
              val qName = getChannelName(c.queueName)
              val queue = s"queues[\"$qName\"]"
              sb.append(indent * 2 + s"await Len($queue) > 0 $and Head($queue) = \"${getMsgName(c.msg)}\";\n")
              sb.append(indent * 2 + s"cur_msg := Head(queue); $queue := Tail(queue);\n")
              sb.append(indent * 2 + s"${getSchedVarName(s._1, s._2)} := ${c.bodyStart}; goto L_${s._1};\n")
            }
          else
            cases.zipWithIndex.foreach { case (c, i) =>
              if (i > 0) sb.append(indent + s"or\n")
              val qName = getChannelName(c.queueName)
              val queue = s"queues[\"$qName\"]"
              sb.append(indent * 2 + s"await Len($queue) > 0 $and Head($queue) = \"${getMsgName(c.msg)}\";\n")
              sb.append(indent * 2 + s"cur_msg := Head(queue);\n")
              sb.append(indent * 2 + s"$queue := Tail(queue);\n")
              sb.append(indent * 2 + s"goto L_${c.bodyStart};\n")
            }
          sb.append(indent + "end either;\n")
        // TODO: otherwise

        case IRSkip(_, _, next) =>
          sb.append(s"skip; goto L_$next\n;")

        case IRParallelExec(schedulerPc, _, branches, breakExit) =>
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
            sb.append(s"${getSchedVarName(schedulerPc, branchNumber)} = 0 $and ")
          sb.append("TRUE;\n") // TODO: we can actually dispose of this
          sb.append(indent * 2 + s"goto L_$breakExit;\n")
          sb.append(indent + "end either;\n")

        case IRParallelEnd(_, s, joinPc) =>
          sb.append(s"${getSchedVarName(s._1, s._2)} := 0; goto L_${s._1};\n")

        case IREnd(_, _) =>
          sb.append(s"goto L_END_ACTOR_$name;\n")
      }
    }

    sb.append(s"L_END_ACTOR_$name:\n")
    sb.append(s"${indent}skip;\n")
    sb.append("end process;\n\n")
    sb.toString()
  }

  private def collectGlobalInfo(actors: mutable.Map[String, mutable.Map[Int, IRInstruction]]): Set[String] = {
    val queues = mutable.Set[String]()
    actors.values.foreach(_.values.foreach {
      case IRQueuePush(_, _, _, q, m) => queues.add(q);
      case IRQueuePop(_, _, _, q, m) => queues.add(q);
      case IRBranch(_, _, cases, _) => cases.foreach { c => queues.add(c.queueName) }
      case _ =>
    })
    queues.toSet
  }
}