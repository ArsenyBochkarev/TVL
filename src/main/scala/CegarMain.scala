import Translator.*
import Translator.Abstraction.*

import java.nio.file.{Files, Path}
import scala.sys.process.{Process, ProcessLogger}
import scala.util.matching.Regex

// ============================================================================
// cegar -- the CEGAR loop driver, running inside ONE JVM (no per-step sbt):
//
//   sbt "runMain cegar --model <in> --target <spin|tla> [flags]"
//   ./cegar <in> <target> [flags]           -- wrapper, same style as ./translate
//
//     0. concrete.tvir  = frontend dump of the source model (validation target
//                         and the abstraction input)
//     1. abstract       = concrete transformed per the tvl-abstraction/1
//                         sidecar (auto kinds minus the refinement blacklist)
//     2. abstract.<ext> = translation of the abstract model (in-process)
//     3. verifier.py    -> verdict + canonical tvl-trace/1 (subprocess leaf)
//     4. curtis validate --abstraction <report> -> replay the trace against
//                         the CONCRETE model (subprocess leaf)
//     5. spurious -> blacklist the culprit node, goto 1
//
//   exit 0 -- VERIFIED (abstract model satisfies all specs; with sound
//             abstractions this implies the concrete model does too)
//   exit 1 -- REAL counterexample / UNKNOWN (budget or no progress)
//   exit 2 -- usage / tooling error
// ============================================================================

case class CegarOpts(
  model: String = "",
  target: String = "tla",
  workdir: String = "",
  channelSize: Int = 20,
  traceSize: Int = 20,
  iterations: Int = 20,
  kinds: String = "loop-unroll,branch-hoist",
)

val cegarUsage: String =
  "usage: cegar --model <file.tvl|.tvir> --target <spin|tla> [--workdir DIR]\n" ++
  "             [--channel-size N] [--trace-size N] [--iterations N] [--kinds a,b]\n" ++
  "  --iterations N   refinement budget (default 20; the loop also stops early\n" ++
  "                   when a culprit repeats - no progress is possible)\n" ++
  "  --kinds          comma-separated auto abstraction kinds\n" ++
  "requires: python3 + verifier.py (repo root), tlc/pcal or spin+gcc, curtis on PATH"

def parseCegarArgs(args: Seq[String]): Either[String, CegarOpts] =
  val withValue: Map[String, String => CegarOpts => CegarOpts] = Map(
    "--model" -> (v => o => o.copy(model = v)),
    "--target" -> (v => o => o.copy(target = v)),
    "--workdir" -> (v => o => o.copy(workdir = v)),
    "--kinds" -> (v => o => o.copy(kinds = v)),
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
  // ./cegar <model> <target> positional style
  if opts.model.isEmpty && positional.length == 2 then
    opts = opts.copy(model = positional.head, target = positional(1))
  if opts.model.isEmpty then return Left(s"expected --model <file>\n$cegarUsage")
  if !Seq("spin", "tla").contains(opts.target) then
    return Left(s"expected --target spin|tla, got '${opts.target}'")
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
  * IR except for nodes a pass inserted (reported per decision). Candidates, in
  * order: the verdict's own node (an inserted id maps back to its decision);
  * the LAST trace step executed at an abstracted (or inserted) node; and, as a
  * last resort so the loop always makes progress while any abstraction is
  * still applied, the last still-applied decision. */
def culpritToConcrete(v: Verdict, applied: List[AppliedDecision],
                      steps: List[(String, Option[Int])],
                      blacklisted: Set[(String, Int)]): Option[(String, Int)] =
  def fresh(c: (String, Int)): Boolean = !blacklisted.contains(c)
  val direct: Option[(String, Int)] = v.node match
    case Some(n) =>
      applied.find(d => d.actor == v.actor.getOrElse("") && d.inserted.contains(n))
        .map(d => (d.actor, d.node))
        .orElse(v.actor.map(a => (a, n)))
    case None => None
  val abstracted = applied.map(d => (d.actor, d.node, d.inserted))
  val fromTrace = steps.reverse.flatMap {
    case (actor, Some(n)) =>
      abstracted.collectFirst {
        case (a, dn, inserted) if actor == a && (n == dn || inserted.contains(n)) => (a, dn)
      }
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

@main def cegar(args: String*): Unit =
  val opts = parseCegarArgs(args.toSeq) match
    case Left(msg) => die(msg, 2)
    case Right(o) => o

  val model = Path.of(opts.model).toAbsolutePath
  if !Files.exists(model) then die(s"model not found: $model", 2)
  if !Files.exists(Path.of("verifier.py")) then
    die("verifier.py not found - run cegar from the repository root", 2)
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

  for it <- 1 to opts.iterations do
    val itdir = workdir.resolve(s"iter_$it")
    Files.createDirectories(itdir)

    // abstract + translate, all in-process: the .tvir is parsed, the
    // abstractor applied and the target code generated inside this JVM
    // (no sbt, no JVM restarts)
    val abstractModel = itdir.resolve(s"abstract.$ext")
    parse(concreteTvir.toString, abstractModel.toString, opts.target,
          itdir.resolve("abstract.tvir").toString, debug = false,
          opts.channelSize, abstractionFile = abstractionJson.toString)
    val absReport = itdir.resolve(s"abstract.$ext.abs.json")
    val applied: List[AppliedDecision] =
      Json.parse(Files.readString(absReport)).field("applied") match
        case Some(entries) =>
          entries.items.flatMap { d =>
            (for
              actor <- d.field("actor").flatMap(_.asString)
              node <- d.field("node").flatMap(_.asInt)
              kind <- d.field("kind").flatMap(_.asString)
              inserted = d.field("inserted").map(ij => ij.items.flatMap(_.asInt)).getOrElse(Nil)
            yield AppliedDecision(actor, node, kind, inserted)).toList
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
      "--trace-json", traceJson.toString, "--channel-size", opts.channelSize.toString))
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
      "--channel-size", opts.channelSize.toString, "--abstraction", absReport.toString))
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
      case None => die("spurious counterexample without a culprit node", 1)
      case Some((actor, node)) =>
        val already = spec.blacklist.exists(e => e.actor == actor && e.node == node)
        spec = spec.withBlacklisted(actor, node, s"spurious at iter $it, step ${verdict.step.getOrElse("?")}")
        AbstractionSpec.save(spec, abstractionJson.toString)
        iterationEntry(it,
          "verdict" -> Json.JStr("spurious"),
          "step" -> verdict.step.map(n => Json.JNum(n)).getOrElse(Json.JNull),
          "actor" -> Json.JStr(actor), "node" -> Json.JNum(node))
        if already then
          println(s"[cegar] UNKNOWN: culprit ($actor, $node) is already blacklisted")
          report("UNKNOWN"); sys.exit(1)
        println(s"[cegar] SPURIOUS at step ${verdict.step.getOrElse("?")} " +
                s"(actor=$actor, node=$node) - refined, retrying")

  println(s"\n[cegar] UNKNOWN: iteration budget (${opts.iterations}) exhausted")
  report("UNKNOWN"); sys.exit(1)
