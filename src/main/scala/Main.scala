import org.antlr.v4.runtime.*
import Grammar.*
import Translator.*
import Translator.Target.Promela

def parse(input: String, output: String): Unit = {
  val charstream = CharStreams.fromFileName(input)
  val lexer = new TVLLexer(charstream)
  val tokens = new CommonTokenStream(lexer)
  val parser = new TVLParser(tokens)
  val tree = parser.program()

  val visitor = new ASTVisitor(true)
  visitor.visitProgram(tree)
  val ir = visitor.getIR
  val promelaTranslator = new Promela()
  val promelaCode = promelaTranslator.translate(ir)

  println(promelaCode)
}

@main
def main(filePath: String , outputFile: String): Unit = {
  parse(filePath, outputFile)
  System.exit(0)
}