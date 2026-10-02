import Translator.*
import Translator.Abstraction.*
import Translator.Abstraction.Json.*
import Translator.Frontend.{FrontendResult, TVIRReader, TVIRParseException}
import Translator.Target.{TargetTranslator, *}
import org.antlr.v4.runtime.CharStreams

import java.io.{File, PrintWriter}
import java.nio.file.{Files, Path}

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

def parse(input: String, output: String, target: String, dumpIrPath: String, debug: Boolean, channelSizeLimit: Int, abstractionFile: String = "-"): Unit = {
  val isIrTarget = target == "ir"
  if !isIrTarget && !targetIsValid(target) then
    println(s"Error: Invalid target \"$target\". Use \"tla\", \"spin\", \"curtis\" or \"ir\"")
    System.exit(1)

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

@main
def main(filePath: String, outputFile: String, target: String, channelSizeLimit: Int, dumpIrPath: String, abstractionFile: String): Unit = {
  parse(filePath, outputFile, target, dumpIrPath, /*debug=*/false, channelSizeLimit, abstractionFile)
  System.exit(0)
}
