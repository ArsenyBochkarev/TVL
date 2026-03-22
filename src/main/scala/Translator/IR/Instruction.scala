package Translator.IR

import Translator.IR.Lib.QueueCondition

sealed trait IRInstruction:
  val id: Int
  val lineNumber: Int // Line number in source (.tvl) file
  val scheduler: (Int, Int) // (scheduler, branch number)
  def successors: List[Int]

case class IRQueuePush(id: Int, lineNumber: Int, scheduler: (Int, Int), next: Int, queueName: String, msg: String) extends IRInstruction:
  override def successors: List[Int] = List(next)
case class IRQueuePop(id: Int, lineNumber: Int, scheduler: (Int, Int), next: Int, queueName: String, msg: String) extends IRInstruction:
  override def successors: List[Int] = List(next)
case class IRJump(id: Int, lineNumber: Int, scheduler: (Int, Int), target: Int) extends IRInstruction:
  override def successors: List[Int] = List(target)
case class IRJumpGuard(id: Int, lineNumber: Int, scheduler: (Int, Int), next: Int, guardVarName: String, target: Int, iterations: Int) extends IRInstruction:
  override def successors: List[Int] = List(target, next)
case class IRSkip(id: Int, lineNumber: Int, scheduler: (Int, Int), next: Int) extends IRInstruction:
  override def successors: List[Int] = List(next)
case class IREnd(id: Int, lineNumber: Int, scheduler: (Int, Int)) extends IRInstruction:
  override def successors: List[Int] = List.empty
case class IRChoice(id: Int, lineNumber: Int, scheduler: (Int, Int), branches: List[Int]) extends IRInstruction:
  override def successors: List[Int] = branches
case class IRBranch(id: Int, lineNumber: Int, scheduler: (Int, Int), cases: List[QueueCondition], otherwise: Option[Int]) extends IRInstruction:
  override def successors: List[Int] = cases.map(_.bodyStart) ++ otherwise.toList
case class IRParallelExec(id: Int, lineNumber: Int, scheduler: (Int, Int), branches: List[Int], breakExit: Int) extends IRInstruction:
  override def successors: List[Int] = branches
case class IRParallelEnd(id: Int, lineNumber: Int, scheduler: (Int, Int), joinPc: Int) extends IRInstruction:
  override def successors: List[Int] = List(joinPc)
