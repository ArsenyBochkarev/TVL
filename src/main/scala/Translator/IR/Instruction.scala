package Translator.IR

import Translator.IR.Lib.QueueCondition

sealed trait IRInstruction:
  val id: Int
  def successors: List[Int]

case class IRQueuePush(id: Int, next: Int, queueName: String, msg: String) extends IRInstruction:
  override def successors: List[Int] = List(next)
case class IRQueuePop(id: Int, next: Int, queueName: String, msg: String) extends IRInstruction:
  override def successors: List[Int] = List(next)
case class IRJump(id: Int, target: Int) extends IRInstruction:
  override def successors: List[Int] = List(target)
case class IRJumpGuard(id: Int, next: Int, guardVarName: String, target: Int, iterations: Int) extends IRInstruction:
  override def successors: List[Int] = List(target, next)
case class IRSkip(id: Int, next: Int) extends IRInstruction:
  override def successors: List[Int] = List(next)
case class IREnd(id: Int) extends IRInstruction:
  override def successors: List[Int] = List.empty
case class IRChoice(id: Int, branches: List[Int]) extends IRInstruction:
  override def successors: List[Int] = branches
case class IRBranch(id: Int, cases: List[QueueCondition], otherwise: Option[Int]) extends IRInstruction:
  override def successors: List[Int] = cases.map(_.bodyStart) ++ otherwise.toList
case class IRParallelExec(id: Int, branches: List[Int], breakExit: Int) extends IRInstruction:
  override def successors: List[Int] = branches
case class IRParallelEnd(id: Int, joinPc: Int) extends IRInstruction:
  override def successors: List[Int] = List(joinPc)
