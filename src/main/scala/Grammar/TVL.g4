grammar TVL;

@header {
    package Grammar;
}

program: module_def EOF;
module_def: MODULE module_name (import_def)* (actor_def)* (spec_def)*;
module_name: IDENTIFIER;

// 'import <module>'
// 'import <module>.<actor>'
import_def: IMPORT import_path;
import_path: IDENTIFIER ('.' IDENTIFIER)?;

// actor <actor name> { ... }
// actor <actor name> interacts with <actor name>, <actor name>,... { ... }
actor_def: ACTOR actor_name (INTERACTS WITH actor_list)? block;

actor_name: IDENTIFIER;
actor_list: IDENTIFIER (',' IDENTIFIER)*;

spec_def: SPECS '{' (formula_def)* '}';
// E.g. `ltl MyProp: "[] (A -> <> B)";`
formula_def: logic_type formula_name ':' STRING ';';
logic_type: LTL | CTL;
formula_name: IDENTIFIER;

block: '{' (labeled_statement)* '}';

labeled_statement: label_def? statement;
label_def: IDENTIFIER ':';
statement:
      send_stmt
    | receive_stmt
    | receive_alts_stmt
    | choose_stmt
    | repeat_stmt
    | parallel_stmt
    | break_stmt
    | skip_stmt
    ;

// send <msg name> to <actor name>
send_stmt: SEND msg_name TO actor_name;
msg_name: IDENTIFIER;

// receive <msg name> from <actor name>
receive_stmt: RECEIVE msg_name FROM actor_name;
// receive alts { <msg name> from <actor name> => { ... }... }
receive_alts_stmt: RECEIVE ALTS '{' (receive_case)+ (otherwise_case)? '}';
receive_case: msg_name FROM actor_name '=>' block;
otherwise_case: OTHERWISE '=>' block;

// choose { ... } or { ... }...
choose_stmt: CHOOSE block (OR block)+;

// repeat { ... }
// repeat <number> { ... }
repeat_stmt: REPEAT (NUMBER)? block;
break_stmt: BREAK;
skip_stmt: SKIP_RULE;

// Parallel
parallel_stmt: PARALLEL block (AND block)+;


MODULE: 'module';
IMPORT: 'import';
ACTOR: 'actor';
INTERACTS: 'interacts';
WITH: 'with';
SEND: 'send';
TO: 'to';
RECEIVE: 'receive';
FROM: 'from';
ALTS: 'alts';
OTHERWISE: 'otherwise';
CHOOSE: 'choose';
OR: 'or';
REPEAT: 'repeat';
BREAK: 'break';
PARALLEL: 'parallel';
AND: 'and';
SKIP_RULE: 'skip';

SPECS: 'specs';
LTL: 'ltl';
CTL: 'ctl';

LBRACE: '{';
RBRACE: '}';
LBRACK: '[';
RBRACK: ']';
COMMA: ',';
DOT: '.';
ARROW: '=>';

IDENTIFIER: [a-zA-Z_] [a-zA-Z0-9_]*;
NUMBER: [0-9]+;
STRING: '"' (~["\\] | '\\' .)* '"';
WS: [ \t\r\n]+ -> skip;
COMMENT: '//' .*? '\n' -> skip;
BLOCK_COMMENT: '/*' .*? '*/' -> skip;