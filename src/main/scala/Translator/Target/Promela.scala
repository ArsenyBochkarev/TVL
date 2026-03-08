package Translator.Target

import Translator.IR.*
import scala.collection.mutable

class Promela extends TargetTranslator {
  private val indent = "  "

  private case class ParallelContext(
    schedulerLabel: String,
    pcVarName: String
  )

  private var finishedProperties: List[String] = List.empty[String]
  override def getFinishingProperty: String =
    if (!isPropEnabled("finishing") || finishedProperties.isEmpty) ""
    else "ltl FinishingProperty { <>(" + finishedProperties.mkString(s" && ") + ") }"
  private var msgDeliveredProperties: List[String] = List.empty[String]
  override def getMsgDeliveredProperty: String =
    if (!isPropEnabled("msg") || msgDeliveredProperties.isEmpty) ""
    else "ltl MessageDeliveredProperty { " + msgDeliveredProperties.mkString(s" && ") + " }"
  private var channels: List[String] = List.empty[String]
  override def getValidityProperty: String =
    if (!isPropEnabled("validity") || finishedProperties.isEmpty || channels.isEmpty) ""
    else
      val sb = new StringBuilder()
      sb.append(s"ltl ValidityProperty { [](${finishedProperties.mkString(s" && ")} -> (")
      channels.foreach { chan =>
        sb.append(s"len($chan) == 0 && ")
      }
      sb.setLength(sb.length - 4)
      sb.append(")) }")
      sb.toString()

  override def translate(actors: mutable.Map[String, mutable.Map[Int, IRInstruction]]): String = {
    val sb = new StringBuilder()
    sb.append("/* Automatically generated file, do not edit! */\n\n")

    val (messages, queues) = collectGlobalInfo(actors)
    // Define all messages
    sb.append("/* Message Types */\n")
    if (messages.nonEmpty)
      sb.append("mtype = { " + messages.map(getMsgName).mkString(", ") + " };\n\n")

    // Create all required channels
    sb.append("/* Channels */\n")
    queues.foreach { qName =>
      val size = 10 // FIXME: I guess this should be changeable
      channels = channels :+ getChannelName(qName)
      sb.append(s"chan ${getChannelName(qName)} = [$size] of { mtype };\n")
    }
    sb.append("\n")

    // Declare global variables for 'message delivered' property
    queues.foreach { qName =>
      messages.foreach { msg =>
        sb.append(s"bool send_${getChannelName(qName)}_${getMsgName(msg)} = false; bool recv_${getChannelName(qName)}_${getMsgName(msg)} = false;\n")
      }
    }
    sb.append("\n")

    // Declare finishing variable for current actor
    actors.keys.toSeq.sorted.foreach { actorName => sb.append(indent + s"bool ${actorName}_finished = false;\n") }

    // Actors
    actors.keys.toSeq.sorted.foreach { actorName =>
      sb.append(translateActor(actorName, actors(actorName)))
      sb.append("\n")
    }

    sb.append("/* Initialization */\n")
    sb.append("init {\n")
    sb.append(indent + "atomic {\n")
    actors.keys.foreach { actorName =>
      sb.append(indent * 2 + s"run $actorName();\n")
    }
    sb.append(indent + "}\n")
    sb.append("}\n")

    sb.toString()
  }

  private def translateActor(name: String, instructions: mutable.Map[Int, IRInstruction]): String = {
    var parallelBlockNum: Int = -1 // If multiple parallel blocks were used in current actor
    val sb = new StringBuilder()
    sb.append(s"proctype $name() {\n")

    // Declare loop guards
    val guardVars = collectGuardVars(instructions.values)
    guardVars.foreach { (v, n) =>
      sb.append(indent + s"int $v = $n;\n")
    }
    // Declare scheduler helper variables
    val schedulerVars = collectSchedVars(instructions.values)
    // Each parallel block should have `n` helper variables
    schedulerVars.foreach { (schedulerPc, numBranches) =>
      for (j <- 1 to numBranches)
        sb.append(indent + s"int ${getSchedVarName(schedulerPc, j)} = 1;\n")
    }
    if (guardVars.nonEmpty || schedulerVars.nonEmpty) sb.append("\n")

    // Traverse all instructions, translating them (almost) independently
    val sortedIds = instructions.keys.toSeq.sorted
    sortedIds.foreach { id =>
      sb.append(s"L_$id: ")
      val instr = instructions(id)
      instr match {
        case IRQueuePush(_, s, next, q, msg) =>
          if isParallel(instr) then
            sb.append(s"${getChannelName(q)} ! ${getMsgName(msg)}; ${getSchedVarName(s._1, s._2)} = $next;\n")
            sb.append(s"atomic { send_${getChannelName(q)}_${getMsgName(msg)} = true; }; \n")
            sb.append(s"goto L_${s._1}\n")
          else
            sb.append(s"${getChannelName(q)} ! ${getMsgName(msg)};\n")
            sb.append(s"atomic { send_${getChannelName(q)}_${getMsgName(msg)} = true; }; \n")
            sb.append(s"goto L_$next\n")

        case IRQueuePop(_, s, next, q, msg) =>
          if isParallel(instr) then
            sb.append(s"${getChannelName(q)} ? ${getMsgName(msg)}; ${getSchedVarName(s._1, s._2)} = $next;\n")
            sb.append(s"atomic { recv_${getChannelName(q)}_${getMsgName(msg)} = true; }; \n")
            sb.append(s"goto L_${s._1};\n")
          else
            sb.append(s"${getChannelName(q)} ? ${getMsgName(msg)};\n")
            sb.append(s"atomic { recv_${getChannelName(q)}_${getMsgName(msg)} = true; }; \n")
            sb.append(s"goto L_$next;\n")
          msgDeliveredProperties = msgDeliveredProperties :+ s"[] (send_${getChannelName(q)}_${getMsgName(msg)} == true -> <> (recv_${getChannelName(q)}_${getMsgName(msg)} == true))"

        case IRJump(_, _, target) =>
          sb.append(s"goto L_$target;\n")

        case IRJumpGuard(_, s, next, guardVar, target, _) =>
          if isParallel(instr) then
            sb.append(indent + s"if\n")
            sb.append(indent * 2 + s":: $guardVar > 0 -> $guardVar = $guardVar - 1; ${getSchedVarName(s._1, s._2)} = $target; goto L_${s._1}\n")
            sb.append(indent * 2 + s":: else -> ${getSchedVarName(s._1, s._2)} = $next; goto L_${s._1}\n")
            sb.append(indent + s"fi;\n")
          else
            sb.append(indent + s"if\n")
            sb.append(indent * 2 + s":: $guardVar > 0 -> $guardVar = $guardVar - 1; goto L_$target;\n")
            sb.append(indent * 2 + s":: else -> goto L_$next;\n")
            sb.append(indent + s"fi;\n")

        case IRChoice(_, s, branches) =>
          sb.append("if\n")
          branches.foreach { b =>
            if isParallel(instr) then
              sb.append(indent * 2 + s":: true -> ${getSchedVarName(s._1, s._2)} = $b; goto L_${s._1}\n")
            else
              sb.append(indent * 2 + s":: true -> goto L_$b;\n")
          }
          sb.append(indent + "fi;\n")

        case IRBranch(_, s, cases, otherwise) =>
          if isParallel(instr) then
            sb.append("if\n")
            cases.foreach { c =>
              sb.append(indent * 2 + s":: ${getChannelName(c.queueName)} ? ${getMsgName(c.msg)} ->\n")
              sb.append(indent * 3 + s"${getSchedVarName(s._1, s._2)} = ${c.bodyStart};\n")
              sb.append(indent * 3 + s"atomic { recv_${getChannelName(c.queueName)}_${getMsgName(c.msg)} = true; }; \n")
              msgDeliveredProperties = msgDeliveredProperties :+ s"[] (send_${getChannelName(c.queueName)}_${getMsgName(c.msg)} -> <> (recv_${getChannelName(c.queueName)}_${getMsgName(c.msg)} = true))"
              sb.append(indent * 3 + s"goto L_${s._1};\n")
            }
            sb.append(indent + "fi;\n")
          else
            sb.append("if\n")
            cases.foreach { c =>
              sb.append(indent * 2 + s":: ${getChannelName(c.queueName)} ? ${getMsgName(c.msg)} ->\n")
              sb.append(indent * 3 + s"atomic { recv_${getChannelName(c.queueName)}_${getMsgName(c.msg)} = true; }; \n")
              msgDeliveredProperties = msgDeliveredProperties :+ s"[] (send_${getChannelName(c.queueName)}_${getMsgName(c.msg)} -> <> (recv_${getChannelName(c.queueName)}_${getMsgName(c.msg)} = true))"
              sb.append(indent * 3 + s"goto L_${c.bodyStart};\n")
            }
            sb.append(indent + "fi;\n")
          // TODO: otherwise

        case IRSkip(_, _, next) =>
          sb.append(s"skip; goto L_$next\n")

        case IRParallelExec(schedulerPc, _, branches, breakExit) =>
          parallelBlockNum += 1
          sb.append("if\n")
          var branchNumber = 1
          branches.foreach { b =>
            // Parallel branch choice
            sb.append(indent * 2 + s":: ${getSchedVarName(schedulerPc, branchNumber)} != 0 ->\n")

            // Set correct step for current branch
            sb.append(indent * 3 + "if\n")
            sb.append(indent * 3 + s":: ${getSchedVarName(schedulerPc, branchNumber)} == 1 -> goto L_$b\n")
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
                sb.append(indent * 3 + s":: ${getSchedVarName(schedulerPc, branchNumber)} == ${nextInstr.id} -> goto L_${nextInstr.id}\n")

                nextInstr.successors.foreach { succ =>
                  if (!visited.contains(succ) && !nextInstr.isInstanceOf[IRParallelEnd])
                    queue.enqueue(succ)
                }
              }
            }
            branchNumber += 1
            sb.append(indent * 3 + "fi\n")
          }
          sb.append(indent * 2 + s":: else -> goto L_$breakExit\n")
          sb.append(indent + "fi;\n")

        case IRParallelEnd(_, s, joinPc) =>
          sb.append(s"${getSchedVarName(s._1, s._2)} = 0; goto L_${s._1}\n")

        case IREnd(_, _) =>
          sb.append(s"goto L_END_ACTOR_$name\n")
      }
    }

    sb.append(s"L_END_ACTOR_$name: ${name}_finished = true;\n")
    finishedProperties = finishedProperties :+ s"(${name}_finished == true)"
    sb.append("}\n")
    sb.toString()
  }

  // mtype and chan
  private def collectGlobalInfo(actors: mutable.Map[String, mutable.Map[Int, IRInstruction]]): (Set[String], Set[String]) = {
    val messages = mutable.Set[String]()
    val queues = mutable.Set[String]()

    actors.values.foreach(_.values.foreach {
      case IRQueuePush(_, _, _, q, m) =>
        queues.add(q)
        messages.add(m)
      case IRQueuePop(_, _, _, q, m) =>
        queues.add(q)
        messages.add(m)
      case IRBranch(_, _, cases, _) =>
        cases.foreach { c =>
          queues.add(c.queueName)
          messages.add(c.msg)
        }
      case _ =>
    })
    (messages.toSet, queues.toSet)
  }
}