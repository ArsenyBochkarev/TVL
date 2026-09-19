import Translator.*
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

def parse(input: String, output: String, target: String, dumpIrPath: String, debug: Boolean, channelSizeLimit: Int): Unit = {
  if target == "ir" then
    // Dump the IR, skip target codegen, source mapping and verification
    val res = FrontendPipeline.run(CharStreams.fromFileName(input), debug)
    writeFile(output, res.toTvirString)
    if dumpIrPath != "-" then writeFile(dumpIrPath, res.toTvirString)
    return

  if !targetIsValid(target) then
    println(s"Error: Invalid target \"$target\". Use \"tla\", \"spin\" or \"ir\"")
    System.exit(1)

  val cs = CharStreams.fromFileName(input)
  val res = FrontendPipeline.run(cs, debug)

  if dumpIrPath != "-" then writeFile(dumpIrPath, res.toTvirString)

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
