package Translator

import Grammar.TVLParser.*

class ASTVisitor(out: String) {
  val Output: String = out

  def visitProgram(ctx: ProgramContext): Unit = {
    // TODO: handle imports
    ctx.module_def().actor_def().forEach(a => {
      println(a.actor_name())
    })
  }
}
