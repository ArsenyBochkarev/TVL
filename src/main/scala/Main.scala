import Translator.*
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

def parse(input: String, output: String, target: String, dumpIrPath: String, debug: Boolean, channelSizeLimit: Int): Unit = {
  val isIrTarget = target == "ir"
  if !isIrTarget && !targetIsValid(target) then
    println(s"Error: Invalid target \"$target\". Use \"tla\", \"spin\" or \"ir\"")
    System.exit(1)

  val res = loadFrontendResult(input, debug)

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
def main(filePath: String, outputFile: String, target: String, channelSizeLimit: Int, dumpIrPath: String): Unit = {
  parse(filePath, outputFile, target, dumpIrPath, /*debug=*/false, channelSizeLimit)
  System.exit(0)
}
