// Generated from ./src/main/scala/Grammar/TVL.g4 by ANTLR 4.13.2
package Grammar;

import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link TVLParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface TVLVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link TVLParser#program}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitProgram(TVLParser.ProgramContext ctx);
	/**
	 * Visit a parse tree produced by {@link TVLParser#module_def}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitModule_def(TVLParser.Module_defContext ctx);
	/**
	 * Visit a parse tree produced by {@link TVLParser#module_name}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitModule_name(TVLParser.Module_nameContext ctx);
	/**
	 * Visit a parse tree produced by {@link TVLParser#import_def}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitImport_def(TVLParser.Import_defContext ctx);
	/**
	 * Visit a parse tree produced by {@link TVLParser#import_path}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitImport_path(TVLParser.Import_pathContext ctx);
	/**
	 * Visit a parse tree produced by {@link TVLParser#actor_def}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitActor_def(TVLParser.Actor_defContext ctx);
	/**
	 * Visit a parse tree produced by {@link TVLParser#actor_name}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitActor_name(TVLParser.Actor_nameContext ctx);
	/**
	 * Visit a parse tree produced by {@link TVLParser#actor_list}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitActor_list(TVLParser.Actor_listContext ctx);
	/**
	 * Visit a parse tree produced by {@link TVLParser#block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlock(TVLParser.BlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link TVLParser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement(TVLParser.StatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link TVLParser#send_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSend_stmt(TVLParser.Send_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link TVLParser#msg_name}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMsg_name(TVLParser.Msg_nameContext ctx);
	/**
	 * Visit a parse tree produced by {@link TVLParser#receive_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReceive_stmt(TVLParser.Receive_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link TVLParser#receive_alts_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReceive_alts_stmt(TVLParser.Receive_alts_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link TVLParser#receive_case}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReceive_case(TVLParser.Receive_caseContext ctx);
	/**
	 * Visit a parse tree produced by {@link TVLParser#choose_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitChoose_stmt(TVLParser.Choose_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link TVLParser#repeat_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRepeat_stmt(TVLParser.Repeat_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link TVLParser#break_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBreak_stmt(TVLParser.Break_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link TVLParser#parallel_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParallel_stmt(TVLParser.Parallel_stmtContext ctx);
}