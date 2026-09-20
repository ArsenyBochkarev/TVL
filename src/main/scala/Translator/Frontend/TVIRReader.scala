package Translator.Frontend

import Translator.IR.*
import Translator.IR.Lib.QueueCondition

import scala.collection.mutable
import scala.util.matching.Regex

class TVIRParseException(message: String)
  extends RuntimeException(s"Error: invalid .tvir: $message")

object TVIRReader {
  private val InstructionLine: Regex = "^  (\\d+): (.+)$".r

  /** Reconstructs a FrontendResult from .tvir text; the inverse of FrontendResult.toTVIRString */
  def fromTVIRString(content: String): FrontendResult = {
    val ir = mutable.Map[String, mutable.Map[Int, IRInstruction]]()
    val templateSpecs = List.newBuilder[String]
    val userSpecs = List.newBuilder[UserSpec]
    val labels = mutable.Map[String, mutable.Map[String, Int]]()

    var section = 0 // 0 actors, 1 template specs, 2 user specs, 3 labels
    var currentActor: Option[String] = None

    content.split("\n", -1).zipWithIndex.foreach { (raw, idx) =>
      val lineNo = idx + 1
      val line = raw.stripTrailing() // tolerates CRLF
      if line.nonEmpty then
        line match
          case "Template specs:" | "User specs:" | "Labels:" =>
            val ord = line match
              case "Template specs:" => 1
              case "User specs:" => 2
              case _ => 3
            if ord <= section then fail(lineNo, s"section \"$line\" appears out of order or twice")
            section = ord
            currentActor = None
          case l if l.startsWith("Actor: ") =>
            if section != 0 then fail(lineNo, "actor block after sections began")
            val name = l.drop("Actor: ".length)
            if !name.matches("[a-zA-Z_][a-zA-Z0-9_]*") then fail(lineNo, s"malformed actor name \"$name\"")
            if ir.contains(name) then fail(lineNo, s"duplicate actor \"$name\"")
            currentActor = Some(name)
            ir(name) = mutable.Map[Int, IRInstruction]()
          case l if l.startsWith("  ") =>
            if section == 0 then
              currentActor match
                case None => fail(lineNo, "instruction line outside any actor block")
                case Some(actor) =>
                  val (id, instr) = parseInstructionLine(lineNo, l)
                  if ir(actor).contains(id) then fail(lineNo, s"duplicate instruction id $id in actor $actor")
                  ir(actor)(id) = instr
            else parseSectionEntry(lineNo, section, l, templateSpecs, userSpecs, labels)
          case l => fail(lineNo, s"unrecognized line \"$l\"")
    }

    if ir.isEmpty then throw new TVIRParseException("no actor blocks found")
    validate(ir, labels)

    FrontendResult(ir, templateSpecs.result(), userSpecs.result(),
      labels.map(kv => kv._1 -> kv._2.toMap).toMap)
  }

  // ---------- instructions ----------

  private def parseInstructionLine(lineNo: Int, line: String): (Int, IRInstruction) =
    line match {
      case InstructionLine(idStr, rest) =>
        val id = intArg(idStr, lineNo)
        val instr = parseInstruction(lineNo, rest)
        if instr.id != id then fail(lineNo, s"instruction id $id does not match constructor id ${instr.id}")
        (id, instr)
      case _ => fail(lineNo, "malformed instruction line (expected \"  <id>: <Instruction>\")")
    }

  private def parseInstruction(lineNo: Int, text: String): IRInstruction = {
    val open = text.indexOf('(')
    if open <= 0 || !text.endsWith(")") then fail(lineNo, s"malformed instruction \"$text\"")
    val name = text.substring(0, open)
    val args = splitTopLevel(text.substring(open + 1, text.length - 1), lineNo)
    def argc(n: Int): Unit = if args.length != n then fail(lineNo, s"$name expects $n args, got ${args.length}")
    def a(i: Int): Int = intArg(args(i), lineNo)
    def sched: (Int, Int) = tuple2(args(2), lineNo)

    name match {
      case "IRQueuePush" => argc(6); IRQueuePush(a(0), a(1), sched, a(3), strArg(args(4), lineNo), strArg(args(5), lineNo))
      case "IRQueuePop" => argc(6); IRQueuePop(a(0), a(1), sched, a(3), strArg(args(4), lineNo), strArg(args(5), lineNo))
      case "IRJump" => argc(4); IRJump(a(0), a(1), sched, a(3))
      case "IRJumpGuard" => argc(7); IRJumpGuard(a(0), a(1), sched, a(3), strArg(args(4), lineNo), a(5), a(6))
      case "IRSkip" => argc(4); IRSkip(a(0), a(1), sched, a(3))
      case "IREnd" => argc(3); IREnd(a(0), a(1), sched)
      case "IRChoice" => argc(4); IRChoice(a(0), a(1), sched, intList(args(3), lineNo))
      case "IRBranch" => argc(5); IRBranch(a(0), a(1), sched, queueConditionList(args(3), lineNo), intOption(args(4), lineNo))
      case "IRParallelExec" => argc(5); IRParallelExec(a(0), a(1), sched, intList(args(3), lineNo), a(4))
      case "IRParallelEnd" => argc(4); IRParallelEnd(a(0), a(1), sched, a(3))
      case other => fail(lineNo, s"unknown instruction \"$other\"")
    }
  }

  // ---------- section entries ----------

  private def parseSectionEntry(lineNo: Int, section: Int, line: String,
                                templateSpecs: mutable.Builder[String, List[String]],
                                userSpecs: mutable.Builder[UserSpec, List[UserSpec]],
                                labels: mutable.Map[String, mutable.Map[String, Int]]): Unit = {
    val entry = line.stripPrefix("  ")
    section match {
      // "  <PropertyName>"
      case 1 =>
        if !entry.matches("[a-zA-Z_][a-zA-Z0-9_]*") then fail(lineNo, s"malformed template spec \"$entry\"")
        templateSpecs += entry
      // "  <logic> <name>: <formula>" — the formula is the rest of the line after the first ':'
      case 2 =>
        val space = entry.indexOf(' ')
        val colon = entry.indexOf(':')
        if space <= 0 || colon <= space then
          fail(lineNo, s"malformed user spec \"$entry\" (expected \"<logic> <name>: <formula>\")")
        val logic = entry.substring(0, space)
        val name = entry.substring(space + 1, colon).trim
        val formula = entry.substring(colon + 1).trim
        if logic != "ltl" && logic != "ctl" then fail(lineNo, s"unknown spec logic \"$logic\" (expected ltl or ctl)")
        if !name.matches("[a-zA-Z_][a-zA-Z0-9_]*") || formula.isEmpty then
          fail(lineNo, s"malformed user spec \"$entry\"")
        userSpecs += UserSpec(logic, name, formula)
      // "  <actor>.<label>: <instructionId>" — identifiers contain no dots or colons
      case 3 =>
        val dot = entry.indexOf('.')
        val colon = entry.indexOf(": ")
        if dot <= 0 || colon < dot then
          fail(lineNo, s"malformed label \"$entry\" (expected \"<actor>.<label>: <id>\")")
        val actor = entry.substring(0, dot)
        val label = entry.substring(dot + 1, colon)
        val id = intArg(entry.substring(colon + 2).trim, lineNo)
        if !actor.matches("[a-zA-Z_][a-zA-Z0-9_]*") || !label.matches("[a-zA-Z_][a-zA-Z0-9_]*") then
          fail(lineNo, s"malformed label \"$entry\"")
        if label == "ACTOR_END" then
          fail(lineNo, "ACTOR_END is a target-side pseudo-label and must not appear in Labels")
        val m = labels.getOrElseUpdate(actor, mutable.Map[String, Int]())
        if m.contains(label) then fail(lineNo, s"duplicate label $actor.$label")
        m(label) = id
      case _ => fail(lineNo, "internal error: unknown section")
    }
  }

  // ---------- validation ----------

  private def validate(ir: mutable.Map[String, mutable.Map[Int, IRInstruction]],
                       labels: mutable.Map[String, mutable.Map[String, Int]]): Unit = {
    ir.foreach { (actor, instrs) =>
      instrs.foreach { (id, instr) =>
        instr.successors.foreach { succ =>
          if !instrs.contains(succ) then
            throw new TVIRParseException(s"actor $actor, instruction $id: successor $succ does not exist")
        }
      }
    }
    labels.foreach { (actor, ls) =>
      ir.get(actor) match {
        case None => throw new TVIRParseException(s"labels reference unknown actor \"$actor\"")
        case Some(instrs) =>
          ls.foreach { (label, id) =>
            if !instrs.contains(id) then
              throw new TVIRParseException(s"label $actor.$label references missing instruction $id")
          }
      }
    }
  }

  // ---------- argument parsing helpers ----------

  private def fail(lineNo: Int, msg: String): Nothing =
    throw new TVIRParseException(s"(line $lineNo) $msg")

  private def intArg(tok: String, lineNo: Int): Int =
    tok.toIntOption.getOrElse(fail(lineNo, s"expected integer, got \"$tok\""))

  /** Verbatim string argument (queue/msg/guard names); docs guarantee [A-Za-z0-9_\[\]]+. */
  private def strArg(tok: String, lineNo: Int): String =
    if !tok.matches("[A-Za-z0-9_\\[\\]]+") then fail(lineNo, s"malformed identifier \"$tok\"")
    tok

  /** Splits a comma-separated argument string at paren depth 0, trimming tokens.
    * "13,17,(-1,-1),List(QueueCondition(Q[R3][R2],X,18)),None" -> 5 tokens.
    * Queue names use [...] which never contains commas, so only parentheses are tracked. */
  private def splitTopLevel(s: String, lineNo: Int): List[String] = {
    if s.trim.isEmpty then return Nil // handles "List()"
    val out = List.newBuilder[String]
    val cur = new StringBuilder
    var depth = 0
    var i = 0
    while i < s.length do {
      val c = s(i)
      if c == ',' && depth == 0 then {
        out += cur.toString.trim // trim: List prints "List(7, 8)"
        cur.clear()
      } else {
        if c == '(' then depth += 1
        else if c == ')' then {
          depth -= 1
          if depth < 0 then fail(lineNo, s"unbalanced parentheses in \"$s\"")
        }
        cur += c
      }
      i += 1
    }
    if depth != 0 then fail(lineNo, s"unbalanced parentheses in \"$s\"")
    out += cur.toString.trim
    out.result()
  }

  /** "(-1,-1)" -> (-1, -1) */
  private def tuple2(tok: String, lineNo: Int): (Int, Int) = {
    if !tok.startsWith("(") || !tok.endsWith(")") || tok.count(_ == '(') != 1 then
      fail(lineNo, s"malformed scheduler tuple \"$tok\"")
    val parts = splitTopLevel(tok.substring(1, tok.length - 1), lineNo)
    if parts.length != 2 then fail(lineNo, s"malformed scheduler tuple \"$tok\"")
    (intArg(parts(0), lineNo), intArg(parts(1), lineNo))
  }

  /** "List(7, 8)" -> List(7, 8); "List()" -> Nil */
  private def intList(tok: String, lineNo: Int): List[Int] =
    listInner(tok, lineNo).map(intArg(_, lineNo))

  /** "List(QueueCondition(Q[R3][R2],X,18), ...)" -> List(QueueCondition(...), ...) */
  private def queueConditionList(tok: String, lineNo: Int): List[QueueCondition] =
    listInner(tok, lineNo).map(queueCondition(_, lineNo))

  private def listInner(tok: String, lineNo: Int): List[String] = {
    if !tok.startsWith("List(") || !tok.endsWith(")") then
      fail(lineNo, s"malformed List \"$tok\"")
    splitTopLevel(tok.substring("List(".length, tok.length - 1), lineNo)
  }

  /** "QueueCondition(Q[R3][R2],X,18)" */
  private def queueCondition(tok: String, lineNo: Int): QueueCondition = {
    if !tok.startsWith("QueueCondition(") || !tok.endsWith(")") then
      fail(lineNo, s"malformed QueueCondition \"$tok\"")
    val parts = splitTopLevel(tok.substring("QueueCondition(".length, tok.length - 1), lineNo)
    if parts.length != 3 then fail(lineNo, s"QueueCondition expects 3 args, got ${parts.length} in \"$tok\"")
    QueueCondition(strArg(parts(0), lineNo), strArg(parts(1), lineNo), intArg(parts(2), lineNo))
  }

  /** "None" -> None; "Some(15)" -> Some(15) */
  private def intOption(tok: String, lineNo: Int): Option[Int] =
    if tok == "None" then None
    else if tok.startsWith("Some(") && tok.endsWith(")") then
      Some(intArg(tok.substring("Some(".length, tok.length - 1), lineNo))
    else fail(lineNo, s"malformed Option \"$tok\" (expected None or Some(<int>))")
}
