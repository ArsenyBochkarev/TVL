package Translator.Target

import Translator.IR.*
import scala.collection.mutable

class PlusCal extends TargetTranslator {
  private val indent = "    "
  private val and = "/\\"
  private val queueSize = 10 // TODO: we should be able to control it

  override def translate(actors: mutable.Map[String, mutable.Map[Int, IRInstruction]]): String = {
    val sb = new StringBuilder()

    sb.append("----------------------------- MODULE System -----------------------------\n")
    sb.append("EXTENDS Naturals, Sequences, TLC\n\n")
    sb.append("(* --algorithm System\n")

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

    sb.append("variables\n")
    sb.append(s"${indent}cur_msg = \"\",\n") // Tmp variable. TODO: check if we really need it
    val guardVars = collectGuardVars(instructions.values)
    guardVars.foreach { (v, n) => sb.append(s"$indent$v = $n,\n") }

    val (parallelMap, containedIds) = analyzeParallelStructure(instructions)
    // PC variables for parallel blocks
    // parallelMap: ParallelID -> List[BranchIndex, StartID]
    parallelMap.foreach { case (parId, branches) =>
      branches.indices.foreach { idx =>
        sb.append(s"${indent}pc_par_${parId}_$idx = 0,\n")
      }
    }
    if (sb.endsWith(",\n")) sb.setLength(sb.length - 2)
    sb.append(";\n")
    sb.append("begin\n")

    val sortedIds = instructions.keys.toSeq.sorted
    sortedIds.foreach { id =>
      val instr = instructions(id)
      if (!isParallel(instr)) {
        sb.append(s"L_$id:\n")

        instr match {
          case IRParallelExec(_, s, branches, breakExit) =>
            branches.zipWithIndex.foreach { case (startId, idx) =>
              sb.append(s"${indent}pc_par_${id}_$idx := $startId;\n")
            }

            val schedLabel = s"L_SCHED_$id"
            sb.append(s"$schedLabel:\n")

            val activeCond = branches.indices.map(i => s"pc_par_${id}_$i /= 0").mkString(" \\/ ")
            sb.append(s"${indent}while ($activeCond) do\n")
            sb.append(s"$indent${indent}either\n")

            branches.indices.foreach { idx =>
              if (idx > 0) sb.append(s"$indent${indent}or\n")

              val pcVar = s"pc_par_${id}_$idx"
              sb.append(s"$indent$indent${indent}await $pcVar /= 0;\n")

              // Switch for parallel branch instructions
              // TODO: scheduler loop
//              val branchInstrIds = getBranchInstructions(id, idx, instructions)
//              sb.append(s"$indent$indent${indent}if $pcVar = ${branchInstrIds.head} then\n")
//              translateParInstr(branchInstrIds.head, instructions, pcVar, indent * 5, sb)
//
//              branchInstrIds.tail.foreach { bId =>
//                sb.append(s"$indent$indent${indent}elsif $pcVar = $bId then\n")
//                translateParInstr(bId, instructions, pcVar, indent * 5, sb)
//              }
//              sb.append(s"$indent$indent${indent}end if;\n")
            }

            sb.append(s"$indent${indent}end either;\n")
            sb.append(s"${indent}end while;\n")
            sb.append(s"${indent}goto L_$breakExit;\n")

          case _ => translateInstr(instr, indent, sb)
        }
      }
    }

    sb.append("L_END_ACTOR:\n")
    sb.append(s"${indent}skip;\n")
    sb.append("end process;\n\n")
    sb.toString()
  }

  /**
   * Non-parallel instruction translation
   */
  private def translateInstr(instr: IRInstruction, indent: String, sb: StringBuilder): Unit = {
    instr match {
      case IRQueuePush(_, _, next, q, msg) =>
        val qName = getChannelName(q)
        val queue = s"queues[\"$qName\"]"
        sb.append(s"$indent$queue := Append($queue, \"${getMsgName(msg)}\");\n")
        sb.append(s"${indent}goto L_$next;\n")

      case IRQueuePop(_, _, next, q, msg) =>
        val qName = getChannelName(q)
        val queue = s"queues[\"$qName\"]"
        sb.append(s"${indent}await Len($queue) > 0 $and Head($queue) = \"${getMsgName(msg)}\";\n")
        sb.append(s"${indent}cur_msg := Head($queue);\n")
        sb.append(s"$indent$queue := Tail($queue);\n")
        sb.append(s"${indent}goto L_$next;\n")

      case IRBranch(_, _, cases, otherwise) =>
        sb.append(s"${indent}either\n")
        cases.zipWithIndex.foreach { case (c, i) =>
          if (i > 0) sb.append(s"${indent}or\n")
          val qName = getChannelName(c.queueName)
          val queue = s"queues[\"$qName\"]"
          sb.append(s"$indent${indent}await Len($queue) > 0 $and Head($queue) = \"${getMsgName(c.msg)}\";\n")
          sb.append(s"$indent${indent}cur_msg := Head(queue);\n")
          sb.append(s"$indent$indent$queue := Tail(queue);\n")
          sb.append(s"$indent${indent}goto L_${c.bodyStart};\n")
        }
        sb.append(s"${indent}end either;\n")
        // TODO: otherwise

      case IRJumpGuard(_, _, next, v, target, _) =>
        sb.append(s"${indent}if $v > 0 then\n")
        sb.append(s"$indent$indent$v := $v - 1;\n")
        sb.append(s"$indent${indent}goto L_$target;\n")
        sb.append(s"${indent}else\n")
        sb.append(s"$indent${indent}goto L_$next;\n")
        sb.append(s"${indent}end if;\n")

      case IRChoice(_, _, branches) =>
        sb.append(s"${indent}either\n")
        branches.zipWithIndex.foreach { case (b, i) =>
          if (i > 0) sb.append(s"${indent}or\n")
          sb.append(s"$indent${indent}goto L_$b;\n")
        }
        sb.append(s"${indent}end either;\n")

      case IRJump(_, _, target) =>
        sb.append(s"${indent}goto L_$target;\n")

      case IRSkip(_, _, next) =>
        sb.append(s"${indent}goto L_$next;\n")

      case IREnd(_, _) =>
        sb.append(s"${indent}goto L_END_ACTOR;\n")

      case _ => assert(false, "ill-formed IR")
    }
  }

  // TODO:
  private def translateParInstr(id: Int, allInstrs: mutable.Map[Int, IRInstruction], pcVar: String, indent: String, sb: StringBuilder): Unit = {
    val instr = allInstrs(id)
    def setPc(next: Int): String = s"$pcVar := $next;"

    instr match {
      case IRQueuePush(_, s, next, q, msg) =>
        val qName = getChannelName(q)
        sb.append(s"${indent}queues[\"$qName\"] := Append(queues[\"$qName\"], \"$msg\");\n")
        sb.append(s"$indent${setPc(next)}\n")

      case IRQueuePop(_, s, next, q, msg) =>
        val qName = getChannelName(q)
        sb.append(s"${indent}await Len(queues[\"$qName\"]) > 0;\n")
        sb.append(s"${indent}cur_msg := Head(queues[\"$qName\"]);\n")
        sb.append(s"${indent}queues[\"$qName\"] := Tail(queues[\"$qName\"]);\n")
        sb.append(s"$indent${setPc(next)}\n")

      case IRJump(_, s, target) =>
        sb.append(s"$indent${setPc(target)}\n")

      case IRParallelEnd(_, s, joinPc) =>
        sb.append(s"$indent$pcVar := 0;\n")

      case _ =>
        sb.append(s"${indent}skip;\n")
    }
  }

  private def analyzeParallelStructure(instrs: mutable.Map[Int, IRInstruction]): (Map[Int, List[Int]], Set[Int]) = {
    val parallelOps = mutable.Map[Int, List[Int]]()
    val containedIds = mutable.Set[Int]()

    instrs.values.collect { case p: IRParallelExec => p }.foreach { p =>
      parallelOps(p.id) = p.branches

      p.branches.foreach { startId =>
        val q = mutable.Queue(startId)
        while (q.nonEmpty) {
          val curr = q.dequeue()
          if (!containedIds.contains(curr) && instrs.contains(curr)) {
            containedIds.add(curr)
            val i = instrs(curr)
            i match {
              case _: IRParallelEnd =>
              case _ => i.successors.foreach(q.enqueue)
            }
          }
        }
      }
    }
    (parallelOps.toMap, containedIds.toSet)
  }

  private def getBranchInstructions(parId: Int, branchIdx: Int, instrs: mutable.Map[Int, IRInstruction]): Seq[Int] = {
    val parInstr = instrs(parId).asInstanceOf[IRParallelExec]
    val startId = parInstr.branches(branchIdx)
    val ids = mutable.ListBuffer[Int]()

    val q = mutable.Queue(startId)
    val visited = mutable.Set[Int]()

    while(q.nonEmpty) {
      val curr = q.dequeue()
      if (!visited.contains(curr) && instrs.contains(curr)) {
        visited.add(curr)
        ids += curr
        val i = instrs(curr)
        i match {
          case _: IRParallelEnd =>
          case _ => i.successors.foreach(q.enqueue)
        }
      }
    }
    ids.toSeq.sorted
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
  private def collectGuardVars(instrs: Iterable[IRInstruction]): Set[(String, Int)] = {
    instrs.collect { case IRJumpGuard(_, _, _, v, _, n) => (v, n) }.toSet
  }

  private def getMsgName(m: String): String = s"MSG_$m"
  private def getChannelName(n: String): String = n.replaceAll("\\[", "_").replaceAll("]", "").replaceAll("[^a-zA-Z0-9_]", "_")
}