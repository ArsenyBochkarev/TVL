package Translator

import Grammar.TVLParser.*
import Translator.IR.*
import Translator.IR.Lib.QueueCondition

import scala.jdk.CollectionConverters.*
import scala.collection.mutable

case class UserSpec(logic: String, name: String, formula: String)

class ASTVisitor(val debug: Boolean = false) {
  // actor name -> (label name -> instruction id)
  private val labels = mutable.Map[/*actor name=*/String, mutable.Map[/*label name*/String, /*instruction id*/Int]]()
  // actor name -> (msg name -> instruction id)
  private val receiveMap = mutable.Map[/*actor name=*/String, mutable.Map[/*label name*/String, /*instruction id*/Int]]()
  def getLabels: Map[String, Map[String, Int]] = labels.map(kv => kv._1 -> kv._2.toMap).toMap
  private val userSpecs = mutable.ListBuffer[UserSpec]()
  def getUserSpecs: List[UserSpec] = userSpecs.toList

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
    // Actors
    ctx.module_def().actor_def().asScala.foreach { actorCtx =>
      val name = actorCtx.actor_name().getText
      actorProcedures(name) = mutable.Map[Int, IRInstruction]()

      val startId = nextId()
      val endId = translateBlock(actorCtx.block(), name, startId)
      actorProcedures(name)(endId) = IREnd(endId, actorCtx.getStop.getLine, scheduler)
    }

    // User specs
    ctx.module_def().spec_def().asScala.foreach { specCtx =>
      specCtx.formula_def().asScala.foreach { fCtx =>
        val logic = fCtx.logic_type().getText.toLowerCase
        val name = fCtx.formula_name().getText
        val rawFormulaStr = fCtx.STRING().getText
        val formula = rawFormulaStr.substring(1, rawFormulaStr.length - 1)
        userSpecs.append(UserSpec(logic, name, formula))
      }
    }

    // There can also be some reserved names for labels
    // They'll be transformed into user-defined ones
    labels.foreach { (actor, actorLabels) =>
      val labelNames = actorLabels.keys.toList

      // Recovery property: [] (fail_i -> <> start_i)
      val failLabels = labelNames.filter(_.toLowerCase.startsWith("fail"))
      val startLabels = labelNames.filter(_.toLowerCase.startsWith("start"))

      if (failLabels.nonEmpty && startLabels.nonEmpty) {
        var recovNum = 0
        failLabels.foreach { fail =>
          startLabels.foreach { start =>
            val formula = s"[] ($actor.$fail -> <> $actor.$start)"
            userSpecs.append(UserSpec("ltl", s"RecoveryProperty_${actor}_${fail}_$recovNum", formula))
            recovNum += 1
          }
        }
      }

      // Loss Detection: [] (expired_msg => <> msg_loss_detected)
      // These labels should be in different actors
      val expiredLabels = labelNames.filter(_.startsWith("expired_msg_"))
      var lossDetectNum = 0
      expiredLabels.foreach { expLabel =>
        val msgName = expLabel.stripPrefix("expired_msg_")
        labels.foreach { (otherActor, otherActorLabels) =>
          if (otherActor != actor)
            val otherActorLabelNames = otherActorLabels.keys.toList
            val lossDetectLabels = otherActorLabelNames.filter(_.endsWith("_loss_detected"))
            lossDetectLabels.filter(_.equals(s"${msgName}_loss_detected")).foreach { ldLabel =>
              val formula = s"[] ($actor.$expLabel -> <>($otherActor.$ldLabel))"
              userSpecs.append(UserSpec("ltl", s"LossDetection_${actor}_${expLabel}_$lossDetectNum", formula))
              lossDetectNum += 1
            }
        }
      }
    }

    if (debug) dumpIR()
  }

  private def translateBlock(ctx: BlockContext, actor: String, startId: Int): Int = {
    var currentPc = startId
    val labeled_statements = ctx.labeled_statement().asScala

    labeled_statements.foreach { stmt =>
      currentPc = translateStatement(stmt, actor, currentPc)
    }
    currentPc
  }

  private def translateStatement(labeledCtx: Labeled_statementContext, actor: String, currentPc: Int): Int = {
    if (labeledCtx.label_def() != null) {
      val labelName = labeledCtx.label_def().IDENTIFIER().getText
      labels.getOrElseUpdate(actor, mutable.Map.empty[String, Int]).update(labelName, currentPc)
    }

    val ctx = labeledCtx.statement()
    val nextPc = nextId()

    // SEND
    if (ctx.send_stmt() != null) {
      val s = ctx.send_stmt()
      val qName = getQueueName(s.actor_name().getText, actor)
      val msgName = s.msg_name().getText
      val sendInstr = IRQueuePush(currentPc, s.getStart.getLine, scheduler, nextPc, qName, msgName)

      var iterPc = currentPc
      val count = if (s.NUMBER() != null) s.NUMBER().getText.toInt else 1
      for (_ <- 1 until count) {
        val nextInChain = nextId()
        val sendInstr = IRQueuePush(iterPc, s.getStart.getLine, scheduler, nextInChain, qName, msgName)
        actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(iterPc, sendInstr)
        iterPc = nextInChain
      }

      val finalSendInstr = IRQueuePush(iterPc, s.getStart.getLine, scheduler, nextPc, qName, msgName)
      actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(iterPc, finalSendInstr)
      nextPc
    }

    // RECEIVE
    else if (ctx.receive_stmt() != null) {
      val r = ctx.receive_stmt()
      val qName = getQueueName(actor, r.actor_name().getText)
      val msgName = r.msg_name().getText
      receiveMap.getOrElseUpdate(actor, mutable.Map.empty[String, Int]).update(msgName, currentPc)

      var iterPc = currentPc
      val count = if (r.NUMBER() != null) r.NUMBER().getText.toInt else 1
      for (_ <- 1 until count) {
        val nextInChain = nextId()
        val receiveInstr = IRQueuePop(iterPc, r.getStart.getLine, scheduler, nextInChain, qName, msgName)
        actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(iterPc, receiveInstr)
        iterPc = nextInChain
      }

      val finalReceiveInstr = IRQueuePop(iterPc, r.getStart.getLine, scheduler, nextPc, qName, msgName)
      actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(iterPc, finalReceiveInstr)
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
        val jumpInstr = IRJump(bodyEnd, rAlts.getStart.getLine, scheduler, exitPc)
        actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(bodyEnd, jumpInstr)
        val msgName = c.msg_name().getText
        receiveMap.getOrElseUpdate(actor, mutable.Map.empty[String, Int]).update(msgName, bodyStart)
        QueueCondition(qName, msgName, bodyStart)
      }.toList

      // TODO: add otherwise case to grammar and handle it here

      val branchInstr = IRBranch(currentPc, rAlts.getStart.getLine, scheduler, cases, None)
      actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(currentPc, branchInstr)
      exitPc
    }

    // CHOOSE
    else if (ctx.choose_stmt() != null) {
      val chooseStmt = ctx.choose_stmt()
      val branches = chooseStmt.block().asScala
      val branchStarts = branches.map(_ => nextId()).toList
      val afterChoice = nextId()

      val choiceInstr = IRChoice(currentPc, chooseStmt.getStart.getLine, scheduler, branchStarts)
      actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(currentPc, choiceInstr)

      branches.zip(branchStarts).foreach { (branch, start) =>
        val end = translateBlock(branch, actor, start)
        val jumpInstr = IRJump(end, chooseStmt.getStart.getLine, scheduler, afterChoice)
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
        val jumpGuardInstr = IRJumpGuard(bodyEnd, r.getStart.getLine, scheduler, afterLoop, guardVar, loopStart, iterations)
        actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(bodyEnd, jumpGuardInstr)
      } else {
        // Uncountable loop
        val bodyEnd = translateBlock(r.block(), actor, loopStart)
        val jumpInstr = IRJump(bodyEnd, r.getStart.getLine, scheduler, loopStart)
        actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(bodyEnd, jumpInstr)
      }

      breakStack.pop()
      afterLoop
    }

    // BREAK
    else if (ctx.break_stmt() != null) {
      if (breakStack.nonEmpty) {
        val jumpInstr = IRJump(currentPc, ctx.break_stmt().getStart.getLine, scheduler, breakStack.top)
        actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(currentPc, jumpInstr)
      }
      nextId()
    }

    // SKIP
    else if (ctx.skip_stmt() != null) {
      val skipInstr = IRSkip(currentPc, ctx.skip_stmt().getStart.getLine, scheduler, nextPc)
      actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(currentPc, skipInstr)
      nextPc
    }

    // PARALLEL
    else if (ctx.parallel_stmt() != null) {
      val branches = ctx.parallel_stmt().block().asScala
      val branchStarts = branches.map(_ => nextId()).toList
      val joinPc = nextId()

      scheduler = (-1, 0)
      val parallelExecInstr = IRParallelExec(currentPc, ctx.parallel_stmt().getStart.getLine, scheduler, branchStarts, joinPc)
      actorProcedures.getOrElseUpdate(actor, mutable.Map.empty[Int, IRInstruction]).update(currentPc, parallelExecInstr)

      var branchNum: Int = 1
      branches.zip(branchStarts).foreach { (b, start) =>
        scheduler = (currentPc, branchNum)
        val end = translateBlock(b, actor, start)
        val parallelEndInstr = IRParallelEnd(end, ctx.parallel_stmt().getStart.getLine, scheduler, joinPc) // Wait here for all parallel branches to end
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
    case IRParallelExec(_, _, _, branches, breakExit) => s"PARALLEL START: branches -> [${branches.mkString(", ")}], break -> [$breakExit]"
    case IRParallelEnd(_, _, _, j) => s"PARALLEL_END"
    case IRQueuePush(_, _, _, next, q, m) => s"PUSH $m to $q"
    case IRQueuePop(_, _, _, next, q, m) => s"POP $m from $q"
    case IRChoice(_, _, _, _) => "CHOICE"
    case IRJump(_, _, _, _) => s"JUMP"
    case IRJumpGuard(_, _, _, n, v, t, _) => s"JUMP TO [$t] IF $v > 0, ELSE to [$n]"
    case IREnd(_, _, _) => ""
    case IRSkip(_, _, _, _) => "SKIP"
    case _ =>
      val succ = if (i.successors.nonEmpty) s" -> [${i.successors.mkString(", ")}]" else ""
      s"${i.getClass.getSimpleName.replace("IR", "")}$succ"
}