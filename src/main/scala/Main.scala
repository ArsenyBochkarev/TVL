import Translator.*
import Translator.Target.{TargetTranslator, *}
import org.antlr.v4.runtime.CharStreams

import java.io.{File, PrintWriter}

def parse(input: String, output: String, target: String, debug: Boolean, channelSizeLimit: Int): Unit = {
  if !targetIsValid(target) then
    println(s"Error: Invalid target \"$target\". Use \"tla\" or \"spin\"")
    System.exit(1)

  val cs = CharStreams.fromFileName(input)
  val res = FrontendPipeline.run(cs, debug)
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
def main(filePath: String, outputFile: String, target: String, channelSizeLimit: Int): Unit = {
  parse(filePath, outputFile, target, /*debug=*/false, channelSizeLimit)
  System.exit(0)
}