# Intermediate Representation

## General
*   **IR Type**: Instruction-based control flow graph. The program is divided into independent procedures for each actor.
*   **Abstraction Level**: Medium. The IR sits between the AST and target model checkers' input languages. Complex constructs (like loops or branching) are represented as a flat graph with explicit transitions (Jump, Branch, Choice). The IR is stripped of syntactic sugar.
*   **Format**: Object data structure in Scala. Represented as a map linking an actor's name to its instruction graph: `mutable.Map[String, mutable.Map[Int, IRInstruction]]`. Each instruction has a unique integer identifier. Separation of the IR from the source code is a TODO.
*   **Differences from TVL**: To simplify the translation process, the IR explicitly introduces the abstraction of message queues, replaces structured control flow with direct jumps, and creates explicit variable counters for countable loops.

## IR instructions
The base element is the `IRInstruction` class. Each instruction contains its id, source code line number, scheduler context required for `parallel` blocks, and a list of successors.

### `IRQueuePush` (Message Sending)
*   **Description**: Adds a message to the recipient's queue.
*   **Operands**:
    *   `next` (int): Identifier of the next instruction.
    *   `qName` (String): Name of the destination queue (channel).
    *   `msgName` (String): Name of the message being sent.
*   **Result**: Transition to `next`.
*   **Semantics**: Places the `msgName` message at the end of the `qName` queue.

### `IRQueuePop` (Message Receiving)
*   **Description**: Extracts a specific message from a queue.
*   **Operands**:
    *   `next` (int): Identifier of the next instruction.
    *   `qName` (String): Name of the current actor's queue.
    *   `msgName` (String): Name of the expected message.
*   **Result**: Transition to `next`.
*   **Semantics**: Blocks execution until `msgName` is at the head of the `qName` queue. Upon a match, extracts the message and transitions to `next`.

### `IRJump` (Unconditional Jump)
*   **Description**: Unconditional transition to another instruction in the graph.
*   **Operands**:
    *   `target` (int): Identifier of the target instruction.
*   **Result**: Transition to `target`.
*   **Semantics**: Moves the actor's execution flow to the `target` node.

### `IRJumpGuard` (Conditional Jump for Countable Loops)
*   **Description**: A transition depending on a counter variable (used for countable loops like `repeat N`).
*   **Operands**:
    *   `next` (int): Identifier of the instruction for loop exit.
    *   `guardVar` (String): Name of the iteration counter variable.
    *   `target` (int): Identifier of the loop body's starting instruction.
    *   `iterations` (int): Initial number of iterations.
*   **Result**: Transition to `target` or `next`.
*   **Semantics**: If `guardVar > 0`, the counter is decremented by 1, and a transition to `target` occurs. Otherwise, it transitions to `next`.

### `IRChoice` (Nondeterministic Choice)
*   **Description**: The `choose` instruction, which nondeterministically selects one of the execution branches.
*   **Operands**:
    *   `branches` (List[Int]): List of identifiers for the starting instructions of the branches.
*   **Result**: Transition to one of the nodes from `branches`.
*   **Semantics**: Nondeterministically selects and transitions to one of the specified branches.

### `IRBranch` (Message Branching)
*   **Description**: Waits for one of several messages, and jump to the execution path for which the message arrived.
*   **Operands**:
    *   `cases` (List[QueueCondition]): List of receiving conditions. Each condition contains `queueName` (String), `msg` (String), and `bodyStart` (int) -- the starting identifier of the corresponding branch.
    *   `otherwise` (Option[Int]): Identifier of the `otherwise` branch's start (if specified).
*   **Result**: Transition to one of the branches depending on the queue states.
*   **Semantics**: Checks the heads of the specified queues. If a matching message is received, it is extracted, and execution transitions to the corresponding `bodyStart` branch. If no messages match, halts, unless the `otherwise` branch is defined. If it is present, it transitions there.

### `IRParallelExec` (Start of a Parallel Block)
*   **Description**: The starting instruction of a `parallel` execution block.
*   **Operands**:
    *   `branchStarts` (List[Int]): List of starting instruction identifiers for each parallel branch.
    *   `breakExit` (int): Identifier of the instruction to transition to upon a `break` or completion.
*   **Result**: Initialization and transition into the parallel context.
*   **Semantics**: Initiates interleaving execution of all branches from `branchStarts`.

### `IRParallelEnd` (End of a Parallel Branch)
*   **Description**: A node indicating the completion of an individual parallel branch.
*   **Operands**:
    *   `joinPc` (int): Identifier of the exit from the blocks executed in parallel.
*   **Result**: Transition to `joinPc`.
*   **Semantics**: Signals the scheduler that the current branch has finished execution. Waits until all other branches reach a similar state.

### `IRSkip` (No-Op)
*   **Description**: No-op. Skips to the next instruction.
*   **Operands**:
    *   `next` (int): Identifier of the next instruction.
*   **Result**: Transition to `next`.
*   **Semantics**: Does nothing; used as a placeholder.

### `IREnd` (End of Actor)
*   **Description**: The terminating instruction of an actor's procedure.
*   **Operands**: None.
*   **Result**: Termination of the actor's execution.
*   **Semantics**: Marks the successful completion of the actor's code execution. Targets should set the completion flag here(e.g. `actor_finished = true`).

## Type System
The IR type system is restricted to distributed system entities:
*   **Graph Nodes**: Instruction identifiers are represented by the `int` type.
*   **Queues**: Queue names are represented by strings (e.g., `Q[A][B]`).
*   **Messages**: Message names are represented by strings. There is no payload. A message is an atomic token.
*   **Loop Counters**: Integer variables whose names are generated dynamically (`guard_ActorName_ID`). These are represented by the `int` type.
*   **Scheduler**: Scheduling state for parallel blocks is tracked by a `(int, int)` tuple containing the start of parallel block and number of branch it represents.

## Structure and Relationships
*   **Control Flow Graph**: The program compiles into a set of graphs, one for each actor. Each graph is a dictionary `mutable.Map[Int, IRInstruction]`.
*   **Control Flow**: Defined by explicit references. Most nodes have a single successor. The `IRJump`, `IRChoice`, `IRBranch`, and `IRJumpGuard` nodes define graph branches.
*   **Absence of SSA**: Message values are not assigned to registers or local variables in the traditional SSA sense. Instead, they mutate the global state of the modeled system (message queues `Q`). The only state variables are loop `guard` counters.
*   **Context**:
    *   Each instruction carries a source AST `lineNumber`, which is used for generating source-maps.
    *   The `scheduler` attribute allows determining the start of a parallel block for correct code generation in target languages.

## Semantics and Invariants
The following verification and correctness invariants apply within the IR:
*   **Queue sizes**: The `IRQueuePush` operation implicitly depends on the channel size invariant (`MAX_QUEUE_SIZE`). If a queue is overflowing, the execution halts.
*   **`parallel` nesting**: In accordance with the language semantics, a `parallel` instruction inside another `parallel` construct is **forbidden**. The `scheduler` attribute does not support a stack of concurrency scopes; it only stores a flat tuple for the current block.
*   **Scheduler creation**:  Targets should support scheduler creation at the PC of `IRParallelExec` instruction.
