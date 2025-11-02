// Generated from ./src/main/scala/Grammar/TVL.g4 by ANTLR 4.13.2
package Grammar;

import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class TVLParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		MODULE=1, IMPORT=2, ACTOR=3, INTERACTS=4, WITH=5, SEND=6, TO=7, RECEIVE=8, 
		FROM=9, ALTS=10, CHOOSE=11, OR=12, REPEAT=13, BREAK=14, PARALLEL=15, AND=16, 
		LBRACE=17, RBRACE=18, LBRACK=19, RBRACK=20, COMMA=21, DOT=22, ARROW=23, 
		IDENTIFIER=24, NUMBER=25, WS=26, COMMENT=27, BLOCK_COMMENT=28;
	public static final int
		RULE_program = 0, RULE_module_def = 1, RULE_module_name = 2, RULE_import_def = 3, 
		RULE_import_path = 4, RULE_actor_def = 5, RULE_actor_name = 6, RULE_actor_list = 7, 
		RULE_block = 8, RULE_statement = 9, RULE_send_stmt = 10, RULE_msg_name = 11, 
		RULE_receive_stmt = 12, RULE_receive_alts_stmt = 13, RULE_receive_case = 14, 
		RULE_choose_stmt = 15, RULE_repeat_stmt = 16, RULE_break_stmt = 17, RULE_parallel_stmt = 18;
	private static String[] makeRuleNames() {
		return new String[] {
			"program", "module_def", "module_name", "import_def", "import_path", 
			"actor_def", "actor_name", "actor_list", "block", "statement", "send_stmt", 
			"msg_name", "receive_stmt", "receive_alts_stmt", "receive_case", "choose_stmt", 
			"repeat_stmt", "break_stmt", "parallel_stmt"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'module'", "'import'", "'actor'", "'interacts'", "'with'", "'send'", 
			"'to'", "'receive'", "'from'", "'alts'", "'choose'", "'or'", "'repeat'", 
			"'break'", "'parallel'", "'and'", "'{'", "'}'", "'['", "']'", "','", 
			"'.'", "'=>'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "MODULE", "IMPORT", "ACTOR", "INTERACTS", "WITH", "SEND", "TO", 
			"RECEIVE", "FROM", "ALTS", "CHOOSE", "OR", "REPEAT", "BREAK", "PARALLEL", 
			"AND", "LBRACE", "RBRACE", "LBRACK", "RBRACK", "COMMA", "DOT", "ARROW", 
			"IDENTIFIER", "NUMBER", "WS", "COMMENT", "BLOCK_COMMENT"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "TVL.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public TVLParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ProgramContext extends ParserRuleContext {
		public Module_defContext module_def() {
			return getRuleContext(Module_defContext.class,0);
		}
		public TerminalNode EOF() { return getToken(TVLParser.EOF, 0); }
		public ProgramContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_program; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof TVLVisitor ) return ((TVLVisitor<? extends T>)visitor).visitProgram(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ProgramContext program() throws RecognitionException {
		ProgramContext _localctx = new ProgramContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_program);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(38);
			module_def();
			setState(39);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Module_defContext extends ParserRuleContext {
		public TerminalNode MODULE() { return getToken(TVLParser.MODULE, 0); }
		public Module_nameContext module_name() {
			return getRuleContext(Module_nameContext.class,0);
		}
		public List<Import_defContext> import_def() {
			return getRuleContexts(Import_defContext.class);
		}
		public Import_defContext import_def(int i) {
			return getRuleContext(Import_defContext.class,i);
		}
		public List<Actor_defContext> actor_def() {
			return getRuleContexts(Actor_defContext.class);
		}
		public Actor_defContext actor_def(int i) {
			return getRuleContext(Actor_defContext.class,i);
		}
		public Module_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_module_def; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof TVLVisitor ) return ((TVLVisitor<? extends T>)visitor).visitModule_def(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Module_defContext module_def() throws RecognitionException {
		Module_defContext _localctx = new Module_defContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_module_def);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(41);
			match(MODULE);
			setState(42);
			module_name();
			setState(46);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==IMPORT) {
				{
				{
				setState(43);
				import_def();
				}
				}
				setState(48);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(52);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==ACTOR) {
				{
				{
				setState(49);
				actor_def();
				}
				}
				setState(54);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Module_nameContext extends ParserRuleContext {
		public TerminalNode IDENTIFIER() { return getToken(TVLParser.IDENTIFIER, 0); }
		public Module_nameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_module_name; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof TVLVisitor ) return ((TVLVisitor<? extends T>)visitor).visitModule_name(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Module_nameContext module_name() throws RecognitionException {
		Module_nameContext _localctx = new Module_nameContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_module_name);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(55);
			match(IDENTIFIER);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Import_defContext extends ParserRuleContext {
		public TerminalNode IMPORT() { return getToken(TVLParser.IMPORT, 0); }
		public Import_pathContext import_path() {
			return getRuleContext(Import_pathContext.class,0);
		}
		public Import_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_import_def; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof TVLVisitor ) return ((TVLVisitor<? extends T>)visitor).visitImport_def(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Import_defContext import_def() throws RecognitionException {
		Import_defContext _localctx = new Import_defContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_import_def);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(57);
			match(IMPORT);
			setState(58);
			import_path();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Import_pathContext extends ParserRuleContext {
		public List<TerminalNode> IDENTIFIER() { return getTokens(TVLParser.IDENTIFIER); }
		public TerminalNode IDENTIFIER(int i) {
			return getToken(TVLParser.IDENTIFIER, i);
		}
		public TerminalNode DOT() { return getToken(TVLParser.DOT, 0); }
		public Import_pathContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_import_path; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof TVLVisitor ) return ((TVLVisitor<? extends T>)visitor).visitImport_path(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Import_pathContext import_path() throws RecognitionException {
		Import_pathContext _localctx = new Import_pathContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_import_path);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(60);
			match(IDENTIFIER);
			setState(63);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==DOT) {
				{
				setState(61);
				match(DOT);
				setState(62);
				match(IDENTIFIER);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Actor_defContext extends ParserRuleContext {
		public TerminalNode ACTOR() { return getToken(TVLParser.ACTOR, 0); }
		public Actor_nameContext actor_name() {
			return getRuleContext(Actor_nameContext.class,0);
		}
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public TerminalNode INTERACTS() { return getToken(TVLParser.INTERACTS, 0); }
		public TerminalNode WITH() { return getToken(TVLParser.WITH, 0); }
		public Actor_listContext actor_list() {
			return getRuleContext(Actor_listContext.class,0);
		}
		public Actor_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_actor_def; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof TVLVisitor ) return ((TVLVisitor<? extends T>)visitor).visitActor_def(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Actor_defContext actor_def() throws RecognitionException {
		Actor_defContext _localctx = new Actor_defContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_actor_def);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(65);
			match(ACTOR);
			setState(66);
			actor_name();
			setState(70);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==INTERACTS) {
				{
				setState(67);
				match(INTERACTS);
				setState(68);
				match(WITH);
				setState(69);
				actor_list();
				}
			}

			setState(72);
			block();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Actor_nameContext extends ParserRuleContext {
		public TerminalNode IDENTIFIER() { return getToken(TVLParser.IDENTIFIER, 0); }
		public Actor_nameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_actor_name; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof TVLVisitor ) return ((TVLVisitor<? extends T>)visitor).visitActor_name(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Actor_nameContext actor_name() throws RecognitionException {
		Actor_nameContext _localctx = new Actor_nameContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_actor_name);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(74);
			match(IDENTIFIER);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Actor_listContext extends ParserRuleContext {
		public List<TerminalNode> IDENTIFIER() { return getTokens(TVLParser.IDENTIFIER); }
		public TerminalNode IDENTIFIER(int i) {
			return getToken(TVLParser.IDENTIFIER, i);
		}
		public List<TerminalNode> COMMA() { return getTokens(TVLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(TVLParser.COMMA, i);
		}
		public Actor_listContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_actor_list; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof TVLVisitor ) return ((TVLVisitor<? extends T>)visitor).visitActor_list(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Actor_listContext actor_list() throws RecognitionException {
		Actor_listContext _localctx = new Actor_listContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_actor_list);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(76);
			match(IDENTIFIER);
			setState(81);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(77);
				match(COMMA);
				setState(78);
				match(IDENTIFIER);
				}
				}
				setState(83);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class BlockContext extends ParserRuleContext {
		public TerminalNode LBRACE() { return getToken(TVLParser.LBRACE, 0); }
		public TerminalNode RBRACE() { return getToken(TVLParser.RBRACE, 0); }
		public List<StatementContext> statement() {
			return getRuleContexts(StatementContext.class);
		}
		public StatementContext statement(int i) {
			return getRuleContext(StatementContext.class,i);
		}
		public BlockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_block; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof TVLVisitor ) return ((TVLVisitor<? extends T>)visitor).visitBlock(this);
			else return visitor.visitChildren(this);
		}
	}

	public final BlockContext block() throws RecognitionException {
		BlockContext _localctx = new BlockContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_block);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(84);
			match(LBRACE);
			setState(88);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 59712L) != 0)) {
				{
				{
				setState(85);
				statement();
				}
				}
				setState(90);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(91);
			match(RBRACE);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class StatementContext extends ParserRuleContext {
		public Send_stmtContext send_stmt() {
			return getRuleContext(Send_stmtContext.class,0);
		}
		public Receive_stmtContext receive_stmt() {
			return getRuleContext(Receive_stmtContext.class,0);
		}
		public Receive_alts_stmtContext receive_alts_stmt() {
			return getRuleContext(Receive_alts_stmtContext.class,0);
		}
		public Choose_stmtContext choose_stmt() {
			return getRuleContext(Choose_stmtContext.class,0);
		}
		public Repeat_stmtContext repeat_stmt() {
			return getRuleContext(Repeat_stmtContext.class,0);
		}
		public Parallel_stmtContext parallel_stmt() {
			return getRuleContext(Parallel_stmtContext.class,0);
		}
		public Break_stmtContext break_stmt() {
			return getRuleContext(Break_stmtContext.class,0);
		}
		public StatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_statement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof TVLVisitor ) return ((TVLVisitor<? extends T>)visitor).visitStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StatementContext statement() throws RecognitionException {
		StatementContext _localctx = new StatementContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_statement);
		try {
			setState(100);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,6,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(93);
				send_stmt();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(94);
				receive_stmt();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(95);
				receive_alts_stmt();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(96);
				choose_stmt();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(97);
				repeat_stmt();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(98);
				parallel_stmt();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(99);
				break_stmt();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Send_stmtContext extends ParserRuleContext {
		public TerminalNode SEND() { return getToken(TVLParser.SEND, 0); }
		public Msg_nameContext msg_name() {
			return getRuleContext(Msg_nameContext.class,0);
		}
		public TerminalNode TO() { return getToken(TVLParser.TO, 0); }
		public Actor_nameContext actor_name() {
			return getRuleContext(Actor_nameContext.class,0);
		}
		public Send_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_send_stmt; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof TVLVisitor ) return ((TVLVisitor<? extends T>)visitor).visitSend_stmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Send_stmtContext send_stmt() throws RecognitionException {
		Send_stmtContext _localctx = new Send_stmtContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_send_stmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(102);
			match(SEND);
			setState(103);
			msg_name();
			setState(104);
			match(TO);
			setState(105);
			actor_name();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Msg_nameContext extends ParserRuleContext {
		public TerminalNode IDENTIFIER() { return getToken(TVLParser.IDENTIFIER, 0); }
		public Msg_nameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_msg_name; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof TVLVisitor ) return ((TVLVisitor<? extends T>)visitor).visitMsg_name(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Msg_nameContext msg_name() throws RecognitionException {
		Msg_nameContext _localctx = new Msg_nameContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_msg_name);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(107);
			match(IDENTIFIER);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Receive_stmtContext extends ParserRuleContext {
		public TerminalNode RECEIVE() { return getToken(TVLParser.RECEIVE, 0); }
		public Msg_nameContext msg_name() {
			return getRuleContext(Msg_nameContext.class,0);
		}
		public TerminalNode FROM() { return getToken(TVLParser.FROM, 0); }
		public Actor_nameContext actor_name() {
			return getRuleContext(Actor_nameContext.class,0);
		}
		public Receive_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_receive_stmt; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof TVLVisitor ) return ((TVLVisitor<? extends T>)visitor).visitReceive_stmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Receive_stmtContext receive_stmt() throws RecognitionException {
		Receive_stmtContext _localctx = new Receive_stmtContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_receive_stmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(109);
			match(RECEIVE);
			setState(110);
			msg_name();
			setState(111);
			match(FROM);
			setState(112);
			actor_name();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Receive_alts_stmtContext extends ParserRuleContext {
		public TerminalNode RECEIVE() { return getToken(TVLParser.RECEIVE, 0); }
		public TerminalNode ALTS() { return getToken(TVLParser.ALTS, 0); }
		public TerminalNode LBRACE() { return getToken(TVLParser.LBRACE, 0); }
		public TerminalNode RBRACE() { return getToken(TVLParser.RBRACE, 0); }
		public List<Receive_caseContext> receive_case() {
			return getRuleContexts(Receive_caseContext.class);
		}
		public Receive_caseContext receive_case(int i) {
			return getRuleContext(Receive_caseContext.class,i);
		}
		public Receive_alts_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_receive_alts_stmt; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof TVLVisitor ) return ((TVLVisitor<? extends T>)visitor).visitReceive_alts_stmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Receive_alts_stmtContext receive_alts_stmt() throws RecognitionException {
		Receive_alts_stmtContext _localctx = new Receive_alts_stmtContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_receive_alts_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(114);
			match(RECEIVE);
			setState(115);
			match(ALTS);
			setState(116);
			match(LBRACE);
			setState(118); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(117);
				receive_case();
				}
				}
				setState(120); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==IDENTIFIER );
			setState(122);
			match(RBRACE);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Receive_caseContext extends ParserRuleContext {
		public Msg_nameContext msg_name() {
			return getRuleContext(Msg_nameContext.class,0);
		}
		public TerminalNode FROM() { return getToken(TVLParser.FROM, 0); }
		public Actor_nameContext actor_name() {
			return getRuleContext(Actor_nameContext.class,0);
		}
		public TerminalNode ARROW() { return getToken(TVLParser.ARROW, 0); }
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public Receive_caseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_receive_case; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof TVLVisitor ) return ((TVLVisitor<? extends T>)visitor).visitReceive_case(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Receive_caseContext receive_case() throws RecognitionException {
		Receive_caseContext _localctx = new Receive_caseContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_receive_case);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(124);
			msg_name();
			setState(125);
			match(FROM);
			setState(126);
			actor_name();
			setState(127);
			match(ARROW);
			setState(128);
			block();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Choose_stmtContext extends ParserRuleContext {
		public TerminalNode CHOOSE() { return getToken(TVLParser.CHOOSE, 0); }
		public List<BlockContext> block() {
			return getRuleContexts(BlockContext.class);
		}
		public BlockContext block(int i) {
			return getRuleContext(BlockContext.class,i);
		}
		public List<TerminalNode> OR() { return getTokens(TVLParser.OR); }
		public TerminalNode OR(int i) {
			return getToken(TVLParser.OR, i);
		}
		public Choose_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_choose_stmt; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof TVLVisitor ) return ((TVLVisitor<? extends T>)visitor).visitChoose_stmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Choose_stmtContext choose_stmt() throws RecognitionException {
		Choose_stmtContext _localctx = new Choose_stmtContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_choose_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(130);
			match(CHOOSE);
			setState(131);
			block();
			setState(134); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(132);
				match(OR);
				setState(133);
				block();
				}
				}
				setState(136); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==OR );
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Repeat_stmtContext extends ParserRuleContext {
		public TerminalNode REPEAT() { return getToken(TVLParser.REPEAT, 0); }
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public TerminalNode NUMBER() { return getToken(TVLParser.NUMBER, 0); }
		public Repeat_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_repeat_stmt; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof TVLVisitor ) return ((TVLVisitor<? extends T>)visitor).visitRepeat_stmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Repeat_stmtContext repeat_stmt() throws RecognitionException {
		Repeat_stmtContext _localctx = new Repeat_stmtContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_repeat_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(138);
			match(REPEAT);
			setState(140);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NUMBER) {
				{
				setState(139);
				match(NUMBER);
				}
			}

			setState(142);
			block();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Break_stmtContext extends ParserRuleContext {
		public TerminalNode BREAK() { return getToken(TVLParser.BREAK, 0); }
		public Break_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_break_stmt; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof TVLVisitor ) return ((TVLVisitor<? extends T>)visitor).visitBreak_stmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Break_stmtContext break_stmt() throws RecognitionException {
		Break_stmtContext _localctx = new Break_stmtContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_break_stmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(144);
			match(BREAK);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Parallel_stmtContext extends ParserRuleContext {
		public TerminalNode PARALLEL() { return getToken(TVLParser.PARALLEL, 0); }
		public List<BlockContext> block() {
			return getRuleContexts(BlockContext.class);
		}
		public BlockContext block(int i) {
			return getRuleContext(BlockContext.class,i);
		}
		public List<TerminalNode> AND() { return getTokens(TVLParser.AND); }
		public TerminalNode AND(int i) {
			return getToken(TVLParser.AND, i);
		}
		public Parallel_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parallel_stmt; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof TVLVisitor ) return ((TVLVisitor<? extends T>)visitor).visitParallel_stmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Parallel_stmtContext parallel_stmt() throws RecognitionException {
		Parallel_stmtContext _localctx = new Parallel_stmtContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_parallel_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(146);
			match(PARALLEL);
			setState(147);
			block();
			setState(150); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(148);
				match(AND);
				setState(149);
				block();
				}
				}
				setState(152); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==AND );
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static final String _serializedATN =
		"\u0004\u0001\u001c\u009b\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001"+
		"\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004"+
		"\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007"+
		"\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b"+
		"\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007"+
		"\u000f\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007"+
		"\u0012\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0005\u0001-\b\u0001\n\u0001\f\u00010\t\u0001\u0001\u0001\u0005"+
		"\u00013\b\u0001\n\u0001\f\u00016\t\u0001\u0001\u0002\u0001\u0002\u0001"+
		"\u0003\u0001\u0003\u0001\u0003\u0001\u0004\u0001\u0004\u0001\u0004\u0003"+
		"\u0004@\b\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001"+
		"\u0005\u0003\u0005G\b\u0005\u0001\u0005\u0001\u0005\u0001\u0006\u0001"+
		"\u0006\u0001\u0007\u0001\u0007\u0001\u0007\u0005\u0007P\b\u0007\n\u0007"+
		"\f\u0007S\t\u0007\u0001\b\u0001\b\u0005\bW\b\b\n\b\f\bZ\t\b\u0001\b\u0001"+
		"\b\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0003\te\b"+
		"\t\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\u000b\u0001\u000b\u0001"+
		"\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001\r\u0001\r\u0001\r\u0001\r\u0004"+
		"\rw\b\r\u000b\r\f\rx\u0001\r\u0001\r\u0001\u000e\u0001\u000e\u0001\u000e"+
		"\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000f\u0001\u000f\u0001\u000f"+
		"\u0001\u000f\u0004\u000f\u0087\b\u000f\u000b\u000f\f\u000f\u0088\u0001"+
		"\u0010\u0001\u0010\u0003\u0010\u008d\b\u0010\u0001\u0010\u0001\u0010\u0001"+
		"\u0011\u0001\u0011\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0004"+
		"\u0012\u0097\b\u0012\u000b\u0012\f\u0012\u0098\u0001\u0012\u0000\u0000"+
		"\u0013\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012\u0014\u0016\u0018"+
		"\u001a\u001c\u001e \"$\u0000\u0000\u0097\u0000&\u0001\u0000\u0000\u0000"+
		"\u0002)\u0001\u0000\u0000\u0000\u00047\u0001\u0000\u0000\u0000\u00069"+
		"\u0001\u0000\u0000\u0000\b<\u0001\u0000\u0000\u0000\nA\u0001\u0000\u0000"+
		"\u0000\fJ\u0001\u0000\u0000\u0000\u000eL\u0001\u0000\u0000\u0000\u0010"+
		"T\u0001\u0000\u0000\u0000\u0012d\u0001\u0000\u0000\u0000\u0014f\u0001"+
		"\u0000\u0000\u0000\u0016k\u0001\u0000\u0000\u0000\u0018m\u0001\u0000\u0000"+
		"\u0000\u001ar\u0001\u0000\u0000\u0000\u001c|\u0001\u0000\u0000\u0000\u001e"+
		"\u0082\u0001\u0000\u0000\u0000 \u008a\u0001\u0000\u0000\u0000\"\u0090"+
		"\u0001\u0000\u0000\u0000$\u0092\u0001\u0000\u0000\u0000&\'\u0003\u0002"+
		"\u0001\u0000\'(\u0005\u0000\u0000\u0001(\u0001\u0001\u0000\u0000\u0000"+
		")*\u0005\u0001\u0000\u0000*.\u0003\u0004\u0002\u0000+-\u0003\u0006\u0003"+
		"\u0000,+\u0001\u0000\u0000\u0000-0\u0001\u0000\u0000\u0000.,\u0001\u0000"+
		"\u0000\u0000./\u0001\u0000\u0000\u0000/4\u0001\u0000\u0000\u00000.\u0001"+
		"\u0000\u0000\u000013\u0003\n\u0005\u000021\u0001\u0000\u0000\u000036\u0001"+
		"\u0000\u0000\u000042\u0001\u0000\u0000\u000045\u0001\u0000\u0000\u0000"+
		"5\u0003\u0001\u0000\u0000\u000064\u0001\u0000\u0000\u000078\u0005\u0018"+
		"\u0000\u00008\u0005\u0001\u0000\u0000\u00009:\u0005\u0002\u0000\u0000"+
		":;\u0003\b\u0004\u0000;\u0007\u0001\u0000\u0000\u0000<?\u0005\u0018\u0000"+
		"\u0000=>\u0005\u0016\u0000\u0000>@\u0005\u0018\u0000\u0000?=\u0001\u0000"+
		"\u0000\u0000?@\u0001\u0000\u0000\u0000@\t\u0001\u0000\u0000\u0000AB\u0005"+
		"\u0003\u0000\u0000BF\u0003\f\u0006\u0000CD\u0005\u0004\u0000\u0000DE\u0005"+
		"\u0005\u0000\u0000EG\u0003\u000e\u0007\u0000FC\u0001\u0000\u0000\u0000"+
		"FG\u0001\u0000\u0000\u0000GH\u0001\u0000\u0000\u0000HI\u0003\u0010\b\u0000"+
		"I\u000b\u0001\u0000\u0000\u0000JK\u0005\u0018\u0000\u0000K\r\u0001\u0000"+
		"\u0000\u0000LQ\u0005\u0018\u0000\u0000MN\u0005\u0015\u0000\u0000NP\u0005"+
		"\u0018\u0000\u0000OM\u0001\u0000\u0000\u0000PS\u0001\u0000\u0000\u0000"+
		"QO\u0001\u0000\u0000\u0000QR\u0001\u0000\u0000\u0000R\u000f\u0001\u0000"+
		"\u0000\u0000SQ\u0001\u0000\u0000\u0000TX\u0005\u0011\u0000\u0000UW\u0003"+
		"\u0012\t\u0000VU\u0001\u0000\u0000\u0000WZ\u0001\u0000\u0000\u0000XV\u0001"+
		"\u0000\u0000\u0000XY\u0001\u0000\u0000\u0000Y[\u0001\u0000\u0000\u0000"+
		"ZX\u0001\u0000\u0000\u0000[\\\u0005\u0012\u0000\u0000\\\u0011\u0001\u0000"+
		"\u0000\u0000]e\u0003\u0014\n\u0000^e\u0003\u0018\f\u0000_e\u0003\u001a"+
		"\r\u0000`e\u0003\u001e\u000f\u0000ae\u0003 \u0010\u0000be\u0003$\u0012"+
		"\u0000ce\u0003\"\u0011\u0000d]\u0001\u0000\u0000\u0000d^\u0001\u0000\u0000"+
		"\u0000d_\u0001\u0000\u0000\u0000d`\u0001\u0000\u0000\u0000da\u0001\u0000"+
		"\u0000\u0000db\u0001\u0000\u0000\u0000dc\u0001\u0000\u0000\u0000e\u0013"+
		"\u0001\u0000\u0000\u0000fg\u0005\u0006\u0000\u0000gh\u0003\u0016\u000b"+
		"\u0000hi\u0005\u0007\u0000\u0000ij\u0003\f\u0006\u0000j\u0015\u0001\u0000"+
		"\u0000\u0000kl\u0005\u0018\u0000\u0000l\u0017\u0001\u0000\u0000\u0000"+
		"mn\u0005\b\u0000\u0000no\u0003\u0016\u000b\u0000op\u0005\t\u0000\u0000"+
		"pq\u0003\f\u0006\u0000q\u0019\u0001\u0000\u0000\u0000rs\u0005\b\u0000"+
		"\u0000st\u0005\n\u0000\u0000tv\u0005\u0011\u0000\u0000uw\u0003\u001c\u000e"+
		"\u0000vu\u0001\u0000\u0000\u0000wx\u0001\u0000\u0000\u0000xv\u0001\u0000"+
		"\u0000\u0000xy\u0001\u0000\u0000\u0000yz\u0001\u0000\u0000\u0000z{\u0005"+
		"\u0012\u0000\u0000{\u001b\u0001\u0000\u0000\u0000|}\u0003\u0016\u000b"+
		"\u0000}~\u0005\t\u0000\u0000~\u007f\u0003\f\u0006\u0000\u007f\u0080\u0005"+
		"\u0017\u0000\u0000\u0080\u0081\u0003\u0010\b\u0000\u0081\u001d\u0001\u0000"+
		"\u0000\u0000\u0082\u0083\u0005\u000b\u0000\u0000\u0083\u0086\u0003\u0010"+
		"\b\u0000\u0084\u0085\u0005\f\u0000\u0000\u0085\u0087\u0003\u0010\b\u0000"+
		"\u0086\u0084\u0001\u0000\u0000\u0000\u0087\u0088\u0001\u0000\u0000\u0000"+
		"\u0088\u0086\u0001\u0000\u0000\u0000\u0088\u0089\u0001\u0000\u0000\u0000"+
		"\u0089\u001f\u0001\u0000\u0000\u0000\u008a\u008c\u0005\r\u0000\u0000\u008b"+
		"\u008d\u0005\u0019\u0000\u0000\u008c\u008b\u0001\u0000\u0000\u0000\u008c"+
		"\u008d\u0001\u0000\u0000\u0000\u008d\u008e\u0001\u0000\u0000\u0000\u008e"+
		"\u008f\u0003\u0010\b\u0000\u008f!\u0001\u0000\u0000\u0000\u0090\u0091"+
		"\u0005\u000e\u0000\u0000\u0091#\u0001\u0000\u0000\u0000\u0092\u0093\u0005"+
		"\u000f\u0000\u0000\u0093\u0096\u0003\u0010\b\u0000\u0094\u0095\u0005\u0010"+
		"\u0000\u0000\u0095\u0097\u0003\u0010\b\u0000\u0096\u0094\u0001\u0000\u0000"+
		"\u0000\u0097\u0098\u0001\u0000\u0000\u0000\u0098\u0096\u0001\u0000\u0000"+
		"\u0000\u0098\u0099\u0001\u0000\u0000\u0000\u0099%\u0001\u0000\u0000\u0000"+
		"\u000b.4?FQXdx\u0088\u008c\u0098";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}