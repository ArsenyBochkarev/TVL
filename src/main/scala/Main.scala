import Translator.*
import Translator.CEGAR.*
import Translator.CEGAR.Json.*
import Translator.Frontend.{FrontendResult, TVIRReader, TVIRParseException}
import Translator.Target.{TargetTranslator, *}
import org.antlr.v4.runtime.CharStreams

import java.io.{File, PrintWriter}
import java.nio.file.{Files, Path}
import scala.sys.process.{Process, ProcessLogger}
import scala.util.matching.Regex

// `-` is the "not set" sentinel for the IR dump path
def writeFile(path: String, content: String): Unit = {
  val parent = Path.of(path).getParent
  if parent != null then Files.createDirectories(parent)
  Files.writeString(Path.of(path), content)
}

/** Single source-selection point: a .tvir input is read back into a FrontendResult
  * (no ANTLR parsing); anything else goes through the TVL frontend */
def loadFrontendResult(input: String, debug: Boolean): FrontendResult = {
  try
    if input.endsWith(".tvir") then TVIRReader.fromTVIRString(Files.readString(Path.of(input)))
    else FrontendPipeline.run(CharStreams.fromFileName(input), debug)
  catch
    case e: TVIRParseException =>
      println(e.getMessage)
      System.exit(1)
      null // unreachable, same pattern as PlusCal.formatCTL
}

/** Applies the abstraction sidecar (if any) and writes a machine-readable
  * report of what was applied next to the output file. */
def abstractResult(res: FrontendResult, output: String, abstractionFile: String): FrontendResult = {
  if abstractionFile == "-" then return res
  val spec = AbstractionSpec.load(abstractionFile)
  val abs = Abstractor(res, spec)
  def decisionJson(d: NodeDecision): Json.JValue =
    Json.JObj(List("actor" -> Json.JStr(d.actor), "node" -> Json.JNum(d.node), "kind" -> Json.JStr(d.kind)))
  def appliedJson(d: AppliedDecision): Json.JValue =
    decisionJson(d.decision) match
      case Json.JObj(fields) => Json.JObj(fields :+ ("affected" -> Json.JArr(d.affected.map(Json.JNum.apply))))
      case other => other
  writeFile(s"$output.abs.json", Json.print(Json.JObj(List(
    "format" -> Json.JStr("tvl-abstraction-report/1"),
    "applied" -> Json.JArr(abs.applied.map(appliedJson)),
    "refused" -> Json.JArr(abs.refused.map { (d, reason) =>
      Json.JObj(List("actor" -> Json.JStr(d.actor), "node" -> Json.JNum(d.node),
        "kind" -> Json.JStr(d.kind), "reason" -> Json.JStr(reason)))
    }),
    "disabled_specs" -> Json.JArr(abs.disabledSpecs.map(Json.JStr.apply)),
  ))) + "\n")
  println(s"Abstraction applied: ${abs.applied.size} decision(s), ${abs.refused.size} refused")
  abs.result
}

def parse(input: String, output: String, target: String, dumpIrPath: String, debug: Boolean, channelSizeLimit: Int, abstractionFile: String = "-", fairness: String = "weak"): Unit = {
  val isIrTarget = target == "ir"
  if !isIrTarget && !targetIsValid(target) then
    println(s"Error: Invalid target \"$target\". Use \"tla\", \"spin\", \"curtis\" or \"ir\"")
    System.exit(1)
  if !Seq("weak", "strong", "none").contains(fairness) then
    println(s"Error: invalid fairness \"$fairness\". Use \"weak\", \"strong\" or \"none\"")
    System.exit(2)
  if target == "spin" && fairness == "strong" then
    println("Error: strong fairness is not supported by the SPIN target (weak or none)")
    System.exit(2)

  val res = abstractResult(loadFrontendResult(input, debug), output, abstractionFile)

  if isIrTarget then
    // Dump the IR (for a .tvir input this is a validated, canonicalized round-trip),
    // skip target codegen, source mapping and verification
    writeFile(output, res.toTVIRString)
    if dumpIrPath != "-" then writeFile(dumpIrPath, res.toTVIRString)
    return

  // The IR is a frontend artifact: dump it even if the target codegen below fails
  if dumpIrPath != "-" then writeFile(dumpIrPath, res.toTVIRString)

  val translator: TargetTranslator = target match {
    case "spin" => new Promela()
    case "tla" => new PlusCal()
    case "curtis" => new Curtis()
  }

  translator.setOutputFile(output)
  translator.setEnabledProperties(res.templateSpecs)
  translator.setUserLabels(res.labels)
  translator.setChannelSizeLimit(channelSizeLimit)
  translator.setFairness(fairness)

  val code = translator.translate(res.ir)
  val writer = new PrintWriter(new File(output))
  try {
    writer.println(code)

    // Properties
    writer.println(translator.generateTemplateSpecs)
    writer.println(translator.generateUserSpecs(res.userSpecs, target))

    // Mapping for trace
    translator.getMapper.saveMapping(input)
  } finally {
    writer.close()
  }
}

// ============================================================================
// The CEGAR loop driver (--cegar)
//
//   ./translate <input> <tla|spin> --cegar [flags]
//
//     0. concrete.tvir  = frontend dump of the source model (validation target
//                         and the abstraction input)
//     1. abstract       = concrete transformed per the tvl-abstraction/1
//                         sidecar (auto kinds minus the refinement blacklist)
//     2. abstract.<ext> = translation of the abstract model (in-process)
//     3. verifier.py    -> verdict + canonical tvl-trace/1 (subprocess leaf)
//     4. curtis validate -> replay the trace against the CONCRETE model
//                         (subprocess leaf)
//     5. spurious -> blacklist the culprit node, goto 1
//     6. no convergence (budget exhausted or no fresh culprit) ->
//        verify the concrete model directly: the run always ends
//        with a definitive verdict
//
//   exit 0 -- VERIFIED (abstract model satisfies all specs; with sound
//             abstractions this implies the concrete model does too)
//   exit 1 -- REAL counterexample (validated against the concrete model,
//             or found by the concrete fallback directly)
//   exit 2 -- usage / tooling error
// ============================================================================

case class CegarOpts(
  model: String = "",
  target: String = "tla",
  workdir: String = "",
  channelSize: Int = 20,
  traceSize: Int = 20,
  iterations: Int = 20,
  kinds: String = "loop-unroll,slice-actor,collapse-messages",
  fairness: String = "weak",
)

val cegarUsage: String =
  "usage: translate <input> <tla|spin> --cegar [--workdir DIR] [--channel-size N]\n" ++
  "                        [--trace-size N] [--iterations N] [--kinds a,b]\n" ++
  "  --iterations N   refinement budget (default 20; on exhaustion - or when\n" ++
  "                   refinement makes no progress - the loop falls back to\n" ++
  "                   verifying the concrete model)\n" ++
  "  --kinds          comma-separated auto abstraction kinds\n" ++
  "                   (default loop-unroll,slice-actor,collapse-messages)\n" ++
  "  --fairness F     fairness for verification: weak | strong | none\n" ++
  "                   (default weak; the SPIN target rejects strong)\n" ++
  "requires: python3 + verifier.py (repo root), tlc/pcal or spin+gcc, curtis on PATH"

/** Parses `--flag value` and `--flag=value` forms alike. */
def parseCegarArgs(raw: Seq[String]): Either[String, CegarOpts] =
  val args = raw.flatMap { a =>
    if a.startsWith("--") && a.contains('=') then
      val i = a.indexOf('=')
      Seq(a.substring(0, i), a.substring(i + 1))
    else Seq(a)
  }.toList
  val withValue: Map[String, String => CegarOpts => CegarOpts] = Map(
    "--model" -> (v => o => o.copy(model = v)),
    "--target" -> (v => o => o.copy(target = v)),
    "--workdir" -> (v => o => o.copy(workdir = v)),
    "--kinds" -> (v => o => o.copy(kinds = v)),
    "--fairness" -> (v => o => o.copy(fairness = v)),
  )
  val withInt: Map[String, Int => CegarOpts => CegarOpts] = Map(
    "--channel-size" -> (v => o => o.copy(channelSize = v)),
    "--trace-size" -> (v => o => o.copy(traceSize = v)),
    "--iterations" -> (v => o => o.copy(iterations = v)),
  )
  var opts = CegarOpts()
  var positional = List.empty[String]
  var i = 0
  while i < args.length do
    val a = args(i)
    if withValue.contains(a) && i + 1 < args.length then
      opts = withValue(a)(args(i + 1))(opts); i += 2
    else if withInt.contains(a) && i + 1 < args.length && args(i + 1).toIntOption.isDefined then
      opts = withInt(a)(args(i + 1).toInt)(opts); i += 2
    else if a.startsWith("-") then
      return Left(s"unknown or incomplete flag '$a'\n$cegarUsage")
    else
      positional = positional :+ a; i += 1
  // positional <model> <target> style
  if opts.model.isEmpty && positional.length == 2 then
    opts = opts.copy(model = positional.head, target = positional(1))
  if opts.model.isEmpty then return Left(s"expected a model file\n$cegarUsage")
  if !Seq("spin", "tla").contains(opts.target) then
    return Left(s"expected --target spin|tla, got '${opts.target}'")
  if !Seq("weak", "strong", "none").contains(opts.fairness) then
    return Left(s"invalid --fairness '${opts.fairness}' (weak|strong|none)\n$cegarUsage")
  if opts.target == "spin" && opts.fairness == "strong" then
    return Left("strong fairness is not supported by the SPIN target (weak or none)\n" + cegarUsage)
  Right(opts)

/** Runs a subprocess, capturing combined stdout+stderr and the exit code. */
def runCmd(cmd: Seq[String]): (Int, String) =
  val buf = new StringBuilder
  val code = Process(cmd) ! ProcessLogger(s => buf.append(s).append('\n'))
  (code, buf.toString)

/** verifier.py outcome: 'violated' | 'clean' | 'error' (a tooling failure is
  * never a successful check). */
def verifierStatus(output: String, traceJson: Path): String =
  if Files.exists(traceJson) then "violated"
  else if output.trim.isEmpty then "error"
  else if output.contains("VERIFICATION FAILED") || output.contains("RESULT: FAILED") then "error"
  else "clean"

val ValidateLine: Regex = """^validate: (\S+)(?: step=(\S+) actor=(\S+) node=(\S+))?\s*$""".r

/** The parsed `validate:` line of curtis. */
case class Verdict(kind: String, step: Option[Int], actor: Option[String], node: Option[Int])

object Verdict:
  def parse(output: String): Option[Verdict] =
    output.linesIterator.collectFirst {
      case ValidateLine(kind, step, actor, node) =>
        Verdict(
          kind,
          if step != null && step.matches("\\d+") then Some(step.toInt) else None,
          if actor == null || actor == "?" then None else Some(actor),
          if node != null && node.matches("-?\\d+") then Some(node.toInt) else None,
        )
    }

/** Maps a spurious culprit to a CONCRETE node id of a NOT-YET-BLACKLISTED
  * abstraction (blacklisting an already-refined node makes no progress and
  * ends the loop as UNKNOWN). Ids are shared between the abstract and concrete
  * IR except for nodes a pass touched beyond its head (reported per decision).
  * Candidates, in order: the verdict's own node (an affected id maps back to
  * its decision; node ids are unique across actors, and model-global passes
  * like collapse-messages key decisions by class name rather than an actor,
  * so the lookup ignores the executing actor); the LAST trace step executed
  * at an abstracted (or affected) node; and, as a
  * last resort so the loop always makes progress while any abstraction is
  * still applied, the last still-applied decision. */
def culpritToConcrete(v: Verdict, applied: List[AppliedDecision],
                      steps: List[(String, Option[Int])],
                      blacklisted: Set[(String, Int)]): Option[(String, Int)] =
  def fresh(c: (String, Int)): Boolean = !blacklisted.contains(c)
  val direct: Option[(String, Int)] = v.node match
    case Some(n) =>
      applied.find(_.affected.contains(n))
        .map(d => (d.actor, d.node))
        .orElse(v.actor.map(a => (a, n)))
    case None => None
  val abstracted = applied.map(d => (d.actor, d.node, d.affected))
  val fromTrace = steps.reverse.flatMap {
    case (actor, Some(n)) =>
      abstracted.collectFirst {
        case (a, dn, aff) if actor == a && (n == dn || aff.contains(n)) => (a, dn)
      }.orElse(abstracted.collectFirst {
        // actor-agnostic, same reason as in `direct` (class-keyed decisions)
        case (a, dn, aff) if n == dn || aff.contains(n) => (a, dn)
      })
    case _ => None
  }.headOption
  direct.filter(fresh)
    .orElse(fromTrace.filter(fresh))
    .orElse(applied.reverse.find(d => fresh((d.actor, d.node))).map(d => (d.actor, d.node)))

/** Prints a fatal message to stderr (unbuffered: a plain println right
  * before sys.exit is lost with the stdout buffer of the forked JVM) and
  * exits. */
def die(msg: String, code: Int): Nothing =
  System.err.println(s"error: $msg")
  System.err.flush()
  sys.exit(code)

def tvlSourceFor(model: Path): Path =
  val s = model.toString
  Path.of(if s.endsWith(".tvir") then s.stripSuffix(".tvir") + ".tvl" else s)

def cegar(args: Seq[String]): Unit =
  val opts = parseCegarArgs(args) match
    case Left(msg) => die(msg, 2)
    case Right(o) => o

  val model = Path.of(opts.model).toAbsolutePath
  if !Files.exists(model) then die(s"model not found: $model", 2)
  if !Files.exists(Path.of("verifier.py")) then
    die("verifier.py not found - run translate from the repository root", 2)
  if Process(Seq("which", "curtis")).! != 0 then
    die("curtis is required for trace validation but is not on PATH", 2)

  val workdir = Path.of(if opts.workdir.nonEmpty then opts.workdir
                        else model.getParent.toString + "/cegar-out").toAbsolutePath
  Files.createDirectories(workdir)
  val ext = if opts.target == "spin" then "pml" else "tla"
  val sourceFile = tvlSourceFor(model)

  // ---- step 0: the concrete IR (validation target + abstraction input) ----
  val concreteTvir = workdir.resolve("concrete.tvir")
  println("[cegar] dumping the concrete IR")
  parse(model.toString, concreteTvir.toString, "ir", "-", debug = false, opts.channelSize)

  // ---- the abstraction sidecar (persisted for observability) --------------
  val abstractionJson = workdir.resolve("abstraction.json")
  var spec = AbstractionSpec(Nil, opts.kinds.split(",").map(_.trim).filter(_.nonEmpty).toList, Nil, Nil)
  AbstractionSpec.save(spec, abstractionJson.toString)

  val history = List.newBuilder[Json.JValue]

  def report(verdict: String): Unit =
    writeFile(workdir.resolve("report.json").toString,
      Json.print(Json.JObj(List(
        "format" -> Json.JStr("tvl-cegar-report/1"),
        "verdict" -> Json.JStr(verdict),
        "iterations" -> Json.JArr(history.result()),
      ))) + "\n")

  def iterationEntry(it: Int, fields: (String, Json.JValue)*): Unit =
    history += Json.JObj(("iteration" -> Json.JNum(it)) +: fields.toList)

  /** The loop did not converge: verify the concrete model directly - one more
    * iteration with every abstraction disabled, so its "abstract" model IS
    * the concrete one. A counterexample found there is real by construction
    * (it comes from that very model), so no curtis validation is needed and
    * the run always ends with a definitive verdict. */
  def concreteFallback(reason: String, it: Int): Nothing =
    println(s"\n[cegar] UNKNOWN: $reason")
    println("[cegar] falling back to the concrete model (no abstractions)")
    val itdir = workdir.resolve(s"iter_$it")
    Files.createDirectories(itdir)
    val concreteModel = itdir.resolve(s"abstract.$ext")
    parse(concreteTvir.toString, concreteModel.toString, opts.target,
          itdir.resolve("abstract.tvir").toString, debug = false, opts.channelSize,
          fairness = opts.fairness)
    println(s"\n[cegar] iteration $it: verifying the concrete model")
    val traceJson = itdir.resolve("trace.json")
    Files.deleteIfExists(traceJson)
    val (_, vout) = runCmd(Seq(
      "python3", "verifier.py", opts.target, concreteModel.toString,
      sourceFile.toString, workdir.resolve("concrete.map.json").toString,
      opts.traceSize.toString,
      "--trace-json", traceJson.toString, "--channel-size", opts.channelSize.toString,
      "--fairness", opts.fairness))
    println(vout)
    verifierStatus(vout, traceJson) match
      case "error" =>
        println("[cegar] ERROR: the verifier failed without a verdict (see output above)")
        iterationEntry(it, "verdict" -> Json.JStr("error")); report("ERROR"); sys.exit(2)
      case "clean" =>
        println(s"\n[cegar] VERIFIED: all specs hold on the concrete model (fallback)")
        iterationEntry(it, "verdict" -> Json.JStr("verified"), "fallback" -> Json.JBool(true))
        report("VERIFIED"); sys.exit(0)
      case _ =>
        println(s"\n[cegar] REAL counterexample on the concrete model (see $itdir/trace.json)")
        iterationEntry(it, "verdict" -> Json.JStr("real"), "fallback" -> Json.JBool(true))
        report("REAL"); sys.exit(1)

  for it <- 1 to opts.iterations do
    val itdir = workdir.resolve(s"iter_$it")
    Files.createDirectories(itdir)

    // abstract + translate, all in-process: the .tvir is parsed, the
    // abstractor applied and the target code generated inside this JVM
    // (no sbt, no JVM restarts)
    val abstractModel = itdir.resolve(s"abstract.$ext")
    parse(concreteTvir.toString, abstractModel.toString, opts.target,
          itdir.resolve("abstract.tvir").toString, debug = false,
          opts.channelSize, abstractionFile = abstractionJson.toString,
          fairness = opts.fairness)
    val absReport = itdir.resolve(s"abstract.$ext.abs.json")
    val applied: List[AppliedDecision] =
      Json.parse(Files.readString(absReport)).field("applied") match
        case Some(entries) =>
          entries.items.flatMap { d =>
            (for
              actor <- d.field("actor").flatMap(_.asString)
              node <- d.field("node").flatMap(_.asInt)
              kind <- d.field("kind").flatMap(_.asString)
              affected = d.field("affected").map(ij => ij.items.flatMap(_.asInt)).getOrElse(Nil)
            yield AppliedDecision(actor, node, kind, affected)).toList
          }
        case None => Nil

    // ---- verify the abstract model (verifier.py subprocess leaf) ----------
    println(s"\n[cegar] iteration $it: ${applied.length} abstraction(s) applied, verifying")
    val traceJson = itdir.resolve("trace.json")
    Files.deleteIfExists(traceJson)
    val (_, vout) = runCmd(Seq(
      "python3", "verifier.py", opts.target, abstractModel.toString,
      sourceFile.toString, workdir.resolve("concrete.map.json").toString,
      opts.traceSize.toString,
      "--trace-json", traceJson.toString, "--channel-size", opts.channelSize.toString,
      "--fairness", opts.fairness))
    println(vout)
    verifierStatus(vout, traceJson) match
      case "error" =>
        println("[cegar] ERROR: the verifier failed without a verdict (see output above)")
        iterationEntry(it, "verdict" -> Json.JStr("error")); report("ERROR"); sys.exit(2)
      case "clean" =>
        println(s"\n[cegar] VERIFIED: all specs hold on the abstract model (iteration $it)")
        iterationEntry(it, "verdict" -> Json.JStr("verified")); report("VERIFIED"); sys.exit(0)
      case _ => ()

    // ---- validate the counterexample against the CONCRETE model -----------
    println("[cegar] counterexample found, validating against the concrete model")
    val (_, cout) = runCmd(Seq(
      "curtis", "validate", concreteTvir.toString, traceJson.toString,
      "--channel-size", opts.channelSize.toString))
    println(cout)
    val verdict = Verdict.parse(cout).getOrElse {
      die("curtis produced no verdict line", 1)
    }
    if verdict.kind == "feasible" then
      println(s"\n[cegar] REAL counterexample (see $itdir/trace.json)")
      iterationEntry(it, "verdict" -> Json.JStr("real")); report("REAL"); sys.exit(1)

    // ---- spurious: refine at the culprit node ------------------------------
    val steps: List[(String, Option[Int])] =
      Json.parse(Files.readString(traceJson)).field("steps") match
        case Some(ss) => ss.items.flatMap(s =>
          (for a <- s.field("actor").flatMap(_.asString) yield
            (a, s.field("node").flatMap(_.asInt))).toList)
        case None => Nil
    val blacklisted = spec.blacklist.map(e => (e.actor, e.node)).toSet
    culpritToConcrete(verdict, applied, steps, blacklisted) match
      case None => concreteFallback("spurious counterexample without a culprit node", it + 1)
      case Some((actor, node)) =>
        val already = spec.blacklist.exists(e => e.actor == actor && e.node == node)
        spec = spec.withBlacklisted(actor, node, s"spurious at iter $it, step ${verdict.step.getOrElse("?")}")
        AbstractionSpec.save(spec, abstractionJson.toString)
        iterationEntry(it,
          "verdict" -> Json.JStr("spurious"),
          "step" -> verdict.step.map(n => Json.JNum(n)).getOrElse(Json.JNull),
          "actor" -> Json.JStr(actor), "node" -> Json.JNum(node))
        if already then
          concreteFallback(s"culprit ($actor, $node) is already blacklisted - no progress", it + 1)
        println(s"[cegar] SPURIOUS at step ${verdict.step.getOrElse("?")} " +
                s"(actor=$actor, node=$node) - refined, retrying")

  concreteFallback(s"iteration budget (${opts.iterations}) exhausted", opts.iterations + 1)

@main
def main(args: String*): Unit =
  if args.headOption.contains("--cegar") then
    cegar(args.tail)
  else
    args match
      case Seq(input, output, target, channelSizeLimit, dumpIrPath, abstractionFile, fairness) =>
        parse(input, output, target, dumpIrPath, debug = false, channelSizeLimit.toInt, abstractionFile, fairness)
        System.exit(0)
      case Seq(input, output, target, channelSizeLimit, dumpIrPath, abstractionFile) =>
        parse(input, output, target, dumpIrPath, debug = false, channelSizeLimit.toInt, abstractionFile)
        System.exit(0)
      case _ =>
        System.err.println(
          "usage (direct):  runMain main <input> <output> <tla|spin|curtis|ir> <channel-size> <dump-ir|-> <abstraction|-> [weak|strong|none]\n" ++
          "usage (--cegar): runMain main --cegar <input> <tla|spin> [--workdir DIR] [--iterations N] [--kinds a,b] [--fairness weak|strong|none]")
        System.exit(2)
