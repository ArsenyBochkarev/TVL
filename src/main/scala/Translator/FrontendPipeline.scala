package Translator

import org.antlr.v4.runtime.*
import Grammar.*
import Translator.Frontend.*

object FrontendPipeline {
  def run(input: CharStream, debug: Boolean): FrontendResult = {
    val lexer = new TVLLexer(input)
    val tokens = new CommonTokenStream(lexer)
    val parser = new TVLParser(tokens)
    val tree = parser.program()

    val visitor = new ASTVisitor(debug)
    visitor.visitProgram(tree)

    FrontendResult(
      ir = visitor.getIR,
      templateSpecs = visitor.getTemplateSpecs,
      userSpecs = visitor.getUserSpecs,
      labels = visitor.getLabels
    )
  }
}