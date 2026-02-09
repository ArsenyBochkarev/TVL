package Translator.Target

import Translator.IR.*
import scala.collection.mutable

class Promela extends TargetTranslator {
  private val indent = "  "

  private case class ParallelContext(
    schedulerLabel: String,
    pcVarName: String
  )

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
      sb.append(s"chan ${getChannelName(qName)} = [$size] of { mtype };\n")
    }
    sb.append("\n")

    // Actors
    actors.keys.toSeq.sorted.foreach { actorName =>
      sb.append(translateActor(actorName, actors(actorName)))
      sb.append("\n")
    }

    // Init
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
    val sb = new StringBuilder()
    sb.append(s"proctype $name() {\n")

    // Declare loop guards
    val guardVars = collectGuardVars(instructions.values)
    guardVars.foreach { (v, n) =>
      sb.append(indent + s"int $v = $n;\n")
    }
    if (guardVars.nonEmpty) sb.append("\n")

    // Remember all parallel branches
    val parallelCtxMap = analyzeParallelBranches(instructions)

    // Traverse all instructions, translating them (almost) independently
    val sortedIds = instructions.keys.toSeq.sorted
    sortedIds.foreach { id =>
      sb.append(s"L_$id: ")
      val instr = instructions(id)
      instr match {
        case IRQueuePush(_, next, q, msg) =>
          sb.append(s"${getChannelName(q)} ! ${getMsgName(msg)}\n")

        case IRQueuePop(_, next, q, msg) =>
          sb.append(s"${getChannelName(q)} ? ${getMsgName(msg)}\n")

        case IRJump(_, target) =>
          sb.append(s"goto L_$target;\n")

        case IRJumpGuard(_, next, guardVar, target, _) =>
          sb.append(s"if\n")
          sb.append(indent * 2 + s":: $guardVar > 0 -> $guardVar = $guardVar - 1; goto L_$target;\n")
          sb.append(indent * 2 + s":: else -> goto L_$next;\n")
          sb.append(indent + s"fi;\n")

        case IRChoice(_, branches) =>
          sb.append("if\n")
          branches.foreach { b =>
            sb.append(indent * 2 + s":: true -> goto L_$b;\n")
          }
          sb.append(indent + "fi;\n")

        case IRBranch(_, cases, otherwise) =>
          sb.append("if\n")
          cases.foreach { c =>
            sb.append(indent * 2 + s":: ${getChannelName(c.queueName)} ? ${getMsgName(c.msg)} -> goto L_${c.bodyStart};\n")
          }
          // TODO: otherwise
          sb.append(indent + "fi;\n")

        case IRSkip(_, next) =>
          sb.append(s"skip\n")

        // TODO: add scheduler loop
        case IRParallelExec(_, branches, breakExit) =>
          sb.append("if\n")
          branches.foreach { b =>
            sb.append(indent * 2 + s":: true -> goto L_$b;\n")
          }
          sb.append(indent + "fi;\n")

        case IRParallelEnd(_, joinPc) =>
          sb.append(s"goto L_$joinPc;\n")

        case IREnd(_) =>
          sb.append("goto L_END_ACTOR;\n") // Заглушка
      }
    }

    sb.append("L_END_ACTOR: skip;\n")
    sb.append("}\n")
    sb.toString()
  }

  // mtype and chan
  private def collectGlobalInfo(actors: mutable.Map[String, mutable.Map[Int, IRInstruction]]): (Set[String], Set[String]) = {
    val messages = mutable.Set[String]()
    val queues = mutable.Set[String]()

    actors.values.foreach(_.values.foreach {
      case IRQueuePush(_, _, q, m) =>
        queues.add(q)
        messages.add(m)
      case IRQueuePop(_, _, q, m) =>
        queues.add(q)
        messages.add(m)
      case IRBranch(_, cases, _) =>
        cases.foreach { c =>
          queues.add(c.queueName)
          messages.add(c.msg)
        }
      case _ =>
    })
    (messages.toSet, queues.toSet)
  }
  // Guard vars for loops
  private def collectGuardVars(instrs: Iterable[IRInstruction]): Set[(String, Int)] = {
    instrs.collect {
      case IRJumpGuard(_, _, v, _, i) => (v, i)
    }.toSet
  }
  // We'll need to patch `next` field in a DAG for instructions executed in parallel
  private def analyzeParallelBranches(instrs: mutable.Map[Int, IRInstruction]): Map[Int, ParallelContext] = {
    val result = mutable.Map[Int, ParallelContext]()
    instrs.values.collect { case p: IRParallelExec => p }.foreach { parInstr =>
      val schedLabel = s"L_SCHED_${parInstr.id}"

      parInstr.branches.zipWithIndex.foreach { case (startBranchId, index) =>
        val pcVar = s"pc_par_${parInstr.id}_$index"
        val ctx = ParallelContext(schedLabel, pcVar)

        val queue = mutable.Queue[Int](startBranchId)
        val visited = mutable.Set[Int]()
        // BFS
        while (queue.nonEmpty) {
          val currId = queue.dequeue()

          if (instrs.contains(currId) && !visited.contains(currId)) {
            visited.add(currId)
            val currInstr = instrs(currId)
            result(currId) = ctx

            currInstr match {
              case _: IRParallelEnd => // Stop for end of parallel branch
              // Note: no nested parallel blocks is allowed
              case _ =>
                currInstr.successors.foreach { succ =>
                  if (!visited.contains(succ)) queue.enqueue(succ)
                }
            }
          }
        }
      }
    }
    result.toMap
  }

  private def getMsgName(m: String): String = s"MSG_$m"
  private def getChannelName(n: String): String = n.replaceAll("\\[", "_").replaceAll("]", "").replaceAll("[^a-zA-Z0-9_]", "_")
}