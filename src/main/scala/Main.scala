import org.antlr.v4.runtime.*
import Grammar.*
import Translator.*
import Translator.Mapping.SourceMapper
import Translator.Target.{TargetTranslator, *}

import java.io.{File, PrintWriter}

def parse(input: String, output: String, target: String, debug: Boolean, enabledProps: String): Unit = {
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

  translator.setEnabledProperties(enabledProps)
  translator.setUserLabels(visitor.getLabels)

  val code = translator.translate(ir)
  val customSpecs = translator.generateUserSpecs(visitor.getUserSpecs, target)
  val writer = new PrintWriter(new File(output))
  try {
    writer.println(code)
    writer.println(translator.getFinishingProperty)
    writer.println(translator.getMsgDeliveredProperty)
    writer.println(translator.getValidityProperty)
    writer.println(customSpecs)

    translator.getMapper.saveMapping(input)
  } finally {
    writer.close()
  }
}

@main
def main(filePath: String, outputFile: String, target: String, enabledProps: String): Unit = {
  parse(filePath, outputFile, target, /*debug=*/false, enabledProps)
  System.exit(0)
}