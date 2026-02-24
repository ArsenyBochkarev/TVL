package Translator

import Grammar.TVLParser.*
import Translator.IR.*
import Translator.IR.Lib.QueueCondition

import scala.jdk.CollectionConverters.*
import scala.collection.mutable

class ASTVisitor(val debug: Boolean = false) {
  private var pcCounter = 0
  private var scheduler: (Int, Int) = (-1, -1) // We'll need it for `parallel` blocks
  private def nextId(): Int = { pcCounter += 1; pcCounter }
  private val actorProcedures = mutable.Map[String, mutable.Map[Int, IRInstruction]]()
  def getIR: mutable.Map[String, mutable.Map[Int, IRInstruction]] = actorProcedures
  // Instruction IDs after current break/parallel block
  private val breakStack = mutable.Stack[Int]()

  private def getQueueName(receiver: String, sender: String): String = s"Q[$receiver][$sender]"
  private def getGuardVarName(id: Int, actorName: String): String = s"guard_${actorName}_$id"

  def visitProgram(ctx: ProgramContext): Unit = {
    ctx.module_def().actor_def().asScala.foreach { actorCtx =>
      val name = actorCtx.actor_name().getText
      actorProcedures(name) = mutable.Map[Int, IRInstruction]()

      val startId = nextId()
      val endId = translateBlock(actorCtx.block(), name, startId)
      actorProcedures(name)(endId) = IREnd(endId, scheduler)
    }
    if (debug) dumpIR()
  }

  private def translateBlock(ctx: BlockContext, actor: String, startId: Int): Int = {
    var currentPc = startId
    val statements = ctx.statement().asScala

    statements.foreach { stmt =>
      currentPc = translateStatement(stmt, actor, currentPc)
    }
    currentPc
  }

  private def translateStatement(ctx: StatementContext, actor: String, currentPc: Int): Int = {
    val nextPc = nextId()

    // SEND
    if (ctx.send_stmt() != null) {
      val s = ctx.send_stmt()
      val qName = getQueueName(s.actor_name().getText, actor)
      val sendInstr = IRQueuePush(currentPc, scheduler, nextPc, qName, s.msg_name().getText)
      actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(currentPc, sendInstr)
      nextPc
    }

    // RECEIVE
    else if (ctx.receive_stmt() != null) {
      val r = ctx.receive_stmt()
      val qName = getQueueName(actor, r.actor_name().getText)
      val receiveInstr = IRQueuePop(currentPc, scheduler, nextPc, qName, r.msg_name().getText)
      actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(currentPc, receiveInstr)
      nextPc
    }

    // RECEIVE ALTS
    else if (ctx.receive_alts_stmt() != null) {
      val rAlts = ctx.receive_alts_stmt()
      val exitPc = nextId()

      val cases = rAlts.receive_case().asScala.map { c =>
        val qName = getQueueName(actor, c.actor_name().getText)
        val bodyStart = nextId()
        val bodyEnd = translateBlock(c.block(), actor, bodyStart)
        val jumpInstr = IRJump(bodyEnd, scheduler, exitPc)
        actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(bodyEnd, jumpInstr)
        QueueCondition(qName, c.msg_name().getText, bodyStart)
      }.toList

      // TODO: add otherwise case to grammar and handle it here

      val branchInstr = IRBranch(currentPc, scheduler, cases, None)
      actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(currentPc, branchInstr)
      exitPc
    }

    // CHOOSE
    else if (ctx.choose_stmt() != null) {
      val branches = ctx.choose_stmt().block().asScala
      val branchStarts = branches.map(_ => nextId()).toList
      val afterChoice = nextId()

      val choiceInstr = IRChoice(currentPc, scheduler, branchStarts)
      actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(currentPc, choiceInstr)

      branches.zip(branchStarts).foreach { (branch, start) =>
        val end = translateBlock(branch, actor, start)
        val jumpInstr = IRJump(end, scheduler, afterChoice)
        actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(end, jumpInstr)
      }
      afterChoice
    }

    // REPEAT
    else if (ctx.repeat_stmt() != null) {
      val r = ctx.repeat_stmt()
      val loopStart = currentPc
      val afterLoop = nextId()

      breakStack.push(afterLoop)

      if (r.NUMBER() != null) {
        // Countable loop
        val bodyEnd = translateBlock(r.block(), actor, loopStart)
        val guardVar = getGuardVarName(bodyEnd, actor)
        val iterations = r.NUMBER().getText.toInt // TODO: throw proper error here if no Int was detected
        val jumpGuardInstr = IRJumpGuard(bodyEnd, scheduler, afterLoop, guardVar, loopStart, iterations)
        actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(bodyEnd, jumpGuardInstr)
      } else {
        // Uncountable loop
        val bodyEnd = translateBlock(r.block(), actor, loopStart)
        val jumpInstr = IRJump(bodyEnd, scheduler, loopStart)
        actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(bodyEnd, jumpInstr)
      }

      breakStack.pop()
      afterLoop
    }

    // BREAK
    else if (ctx.break_stmt() != null) {
      if (breakStack.nonEmpty) {
        val jumpInstr = IRJump(currentPc, scheduler, breakStack.top)
        actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(currentPc, jumpInstr)
      }
      nextId()
    }

    // PARALLEL
    else if (ctx.parallel_stmt() != null) {
      val branches = ctx.parallel_stmt().block().asScala
      val branchStarts = branches.map(_ => nextId()).toList
      val joinPc = nextId()

      scheduler = (-1, 0)
      val parallelExecInstr = IRParallelExec(currentPc, scheduler, branchStarts, joinPc)
      actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(currentPc, parallelExecInstr)

      var branchNum: Int = 1
      branches.zip(branchStarts).foreach { (b, start) =>
        scheduler = (currentPc, branchNum)
        val end = translateBlock(b, actor, start)
        val parallelEndInstr = IRParallelEnd(end, scheduler, joinPc) // Wait here for all parallel branches to end
        actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(end, parallelEndInstr)
        branchNum += 1
      }
      scheduler = (-1, -1)
      joinPc
    }

    else currentPc
  }

  private def dumpIR(): Unit = {
    actorProcedures.keys.toSeq.sorted.foreach { actor =>
      println(s"\n=== Flow for actor: $actor ===")
      val instrs = actorProcedures(actor)
      // startId for current actor is its min ID
      val startId = instrs.keys.min

      val visited = mutable.Set[Int]()
      val queue = mutable.Queue[Int](startId)

      // BFS:
      while (queue.nonEmpty) {
        val id = queue.dequeue()
        if (!visited.contains(id) && instrs.contains(id)) {
          visited.add(id)
          val instr = instrs(id)
          printInstruction(instr)

          instr.successors.foreach { succ =>
            if (!visited.contains(succ)) queue.enqueue(succ)
          }
        }
      }
    }
  }

  private def printInstruction(i: IRInstruction): Unit = {
    val successorsStr = if (i.successors.nonEmpty) s", NEXT: [${i.successors.mkString(", ")}]" else "ACTOR END"
    println(f"  [${i.id}%03d] ${formatInstr(i)}$successorsStr")
  }

  // TODO: make this instruction-specific
  private def formatInstr(i: IRInstruction): String = i match
    case b: IRBranch =>
      val casesStr = b.cases.map(c => s"JUMP TO [${c.bodyStart}] IF (${c.msg} from ${c.queueName})").mkString(", ")
      val otherStr = b.otherwise.map(o => s", otherwise -> [$o]").getOrElse("")
      s"BRANCH: { $casesStr$otherStr }"
    case IRParallelExec(_, _, branches, breakExit) => s"PARALLEL START: branches -> [${branches.mkString(", ")}], break -> [$breakExit]"
    case IRParallelEnd(_, _, j) => s"PARALLEL_END"
    case IRQueuePush(_, _, next, q, m) => s"PUSH $m to $q"
    case IRQueuePop(_, _, next, q, m) => s"POP $m from $q"
    case IRChoice(_, _, _) => "CHOICE"
    case IRJump(_, _, _) => s"JUMP"
    case IRJumpGuard(_, _, n, v, t, _) => s"JUMP TO [$t] IF $v > 0, ELSE to [$n]"
    case IREnd(_, _) => ""
    case IRSkip(_, _, _) => "SKIP" // TODO: add 'skip' to grammar
    case _ =>
      val succ = if (i.successors.nonEmpty) s" -> [${i.successors.mkString(", ")}]" else ""
      s"${i.getClass.getSimpleName.replace("IR", "")}$succ"
}