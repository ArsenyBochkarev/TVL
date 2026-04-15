import org.antlr.v4.runtime.*
import Grammar.*
import Translator.*
import Translator.Target.{TargetTranslator, *}

import java.io.{File, PrintWriter}

def parse(input: String, output: String, target: String, debug: Boolean, channelSizeLimit: Int): Unit = {
  if !targetIsValid(target) then
    println(s"Error: Invalid target \"$target\". Use \"tla\" or \"spin\"")
    System.exit(1)

  val charstream = CharStreams.fromFileName(input)
  val lexer = new TVLLexer(charstream)
  val tokens = new CommonTokenStream(lexer)
  val parser = new TVLParser(tokens)
  val tree = parser.program()

  val visitor = new ASTVisitor(debug)
  visitor.visitProgram(tree)
  val ir = visitor.getIR
  val translator: TargetTranslator = target match {
    case "spin" => new Promela()
    case "tla" => new PlusCal()
  }

  translator.setOutputFile(output)
  translator.setEnabledProperties(visitor.getTemplateSpecs)
  translator.setUserLabels(visitor.getLabels)
  translator.setChannelSizeLimit(channelSizeLimit)

  val code = translator.translate(ir)
  val writer = new PrintWriter(new File(output))
  try {
    writer.println(code)

    // Properties
    writer.println(translator.generateTemplateSpecs)
    writer.println(translator.generateUserSpecs(visitor.getUserSpecs, target))

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