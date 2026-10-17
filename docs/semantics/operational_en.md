We need to introduce the configuration of the program. Configuration is a pair `<T, Q>`, where
- `T` -- is the set of threads in the program. Each thread may contain the current state of the thread. A thread state is a pair `{A, P}`, where
    - `A` -- the current actor
    - `P` -- the remaining sequence of commands that the actor must execute
- `Q` -- a dictionary (map) of message queue dictionaries to each of the actors existing in the program. A message is characterized by its name `M`. Accessing elements:
    - `Q[A]` -- dictionary of queues to actor `A` from all actors
    - `Q[A][B]` -- message queue to actor `A` from actor `B`

There is exactly one thread per actor. In `<{A, P} ∪ T, Q>`, `T` contains the other actors, which are not taking steps at this moment. The completed program `ε` has no step. Initially the actors contain their source programs and all queues are empty.

*Notation:*
- *Sequential execution: symbol `;`*
- *Empty command: symbol `ε`*
- *Queue: `head :: tail`, where `head` is the first element and `tail` is the remainder. Empty queue: `[]`. The notation `q <- q :: M` denotes appending message `M` to the tail of queue `q` and replacing the original queue with the updated one. The notation `|q|` denotes the length of queue `q`*
- *Channel size limit (message queue to some actor from another actor): `MAX_QUEUE_SIZE`*
- *The symbol `-->` denotes an actor step*
- *The symbol `#` denotes comments within a rule*

An actor is enabled exactly when it has a step. Termination means all residual actor programs are `ε`, even if messages remain queued. A deadlock is a state with no enabled actors and at least one unfinished actor.

All the rules below attempt to apply nondeterministically at each state of the configuration.

1. Message sending
    - Message `M` is added to the tail of the message queue to actor `B` from actor `A`
    - ```
		|Q[B][A]| < MAX_QUEUE_SIZE   Q[B][A] == Q_b_a
		————————————————————————————————————————————————————————————————————————————————————————————————————————————
	    <{A, send M to B; P} ∪ T, Q ∪ Q[B][A]> --> <{A, P} ∪ T, Q ∪ Q[B][A](Q_b_a <- Q_b_a :: M)> `
	  ```
   - If there is not enough space in the message queue, wait until space becomes available.
   - For a nonnegative integer `N`, `send N M to B` means `N` sequential primitive sends. These are separate, interleavable steps, each checking channel capacity. The group of sends is not atomic. `send 0 M to B` means `skip`. An omitted count means one send.
2. Message receiving
    - Single message reception:
        - The head of the message queue to actor `A` from actor `B` contains the message `M`
        - ```
			Q[A][B] == M :: Q_a_b
			————————————————————————————————————————————————————————————————————————————————————————————————————————————
			<{A, receive M from B; P} ∪ T, Q ∪ Q[A][B]> --> <{A, P} ∪ T, Q ∪ Q[A][B](M :: Q_a_b <- Q_a_b)>
	      ```
        - Only a message at the head of the queue can be received; otherwise, the execution branch blocks. Messages deeper in the queue are not inspected.
        - For a nonnegative integer `N`, `receive N M from B` means `N` sequential primitive receives, each of which can block and interleave with other steps; the group is not atomic. `receive 0 M from B` means `skip`. An omitted count means one receive.
    - Receiving one of the possible messages:
        - The branches for receiving one of several possible messages, `M_i from B_i => { P_i }`, have indices `i ∈ I = {0, ..., N}` from a finite set. The alternative receive command `receive alts` is denoted by `alts(I)`, or by `alts(I, P_other)` when it includes `otherwise => { P_other }`.
        - The set of indices of branches with messages available for reception is `E(A,Q,I) = { i ∈ I | ∃q: Q[A][B_i] == M_i :: q }`. Any enabled branch can be chosen, with no priority by source order or sender. Matching the sender and message, consuming the head, and selecting the branch form one atomic step. The branch body may start executing on the next step; `P` may execute afterward.
        - ```
            i ∈ E(A,Q,I)   Q[A][B_i] == M_i :: q
            ————————————————————————————————————————————————————————————————————————————————————————————————————————————
            <{A, alts(I) ∪ P_i; P} ∪ T, Q ∪ Q[A][B_i]> --> <{A, P_i; P} ∪ T, Q ∪ Q[A][B_i](M_i :: q <- q)>
          ```
        - ```
            i ∈ E(A,Q,I)   Q[A][B_i] == M_i :: q
            ————————————————————————————————————————————————————————————————————————————————————————————————————————————
            <{A, alts(I, P_other) ∪ P_i; P} ∪ T, Q ∪ Q[A][B_i]> --> <{A, P_i; P} ∪ T, Q ∪ Q[A][B_i](M_i :: q <- q)>
          ```
        - `otherwise`: take this branch exactly when no message branch is enabled at the instant this step executes. Empty queues and nonmatching heads both count as unavailable. The check and branch selection are atomic. Without `otherwise`, an empty enabled set blocks this execution branch.
        - ```
            E(A,Q,I) == ∅
            ————————————————————————————————————————————————————————————————————————————————————————————————————————————
            <{A, alts(I, P_other); P} ∪ T, Q> --> <{A, P_other; P} ∪ T, Q>
          ```
3. Empty command
  - A completely empty action
  - `<{A, skip; P} ∪ T, Q> --> <{A, P} ∪ T, Q>`
4. Nondeterministic choice
    - `∀i=0..N <{A, choose { P_0 } or ... or { P_i } or ... or { P_N }; P } ∪ T, Q> --> <{A, P_i; P} ∪ T, Q>`
5. Loop
    - "Infinite" loop:
      - Wait for `break` to exit
      - On first entering the loop body, it is saved as a parameter in `repeat`. All subsequent loop iterations use the saved parameter. The bracketed forms denote this parameter during execution and are not part of the source language syntax.
      - ```
          <{A, P_0} ∪ T, Q> --> <{A, P_1 } ∪ T, Q'>     # Internal computation step from P_0 to P_1
          ————————————————————————————————————————————————————————————————————————————————————————————————————————————
          <{A, repeat { P_0 }; P} ∪ T, Q> --> <{A, repeat [P_0] { P_1 }; P} ∪ T, Q>
        ```
      - The original body parameter is preserved during the computation step in the body
      - ```
            <{A, P_1} ∪ T, Q> --> <{A, P_2} ∪ T, Q'>     # Internal computation step from P_1 to P_2
            ————————————————————————————————————————————————————————————————————————————————————————————————————————————
            <{A, repeat [P_0] { P_1 }; P} ∪ T, Q> --> <{A, repeat [P_0] { P_2 }; P} ∪ T, Q'>
        ```
      - Restart the loop, taking the body from the parameter: `<{A, repeat [P] { ε }; P} ∪ T, Q> --> <{A, repeat [P] { P }; P} ∪ T, Q>`
   - Finite loop:
      - Similar to the previous variant, but with a counter
      - The source count is a nonnegative integer. In `repeat [P_0] N { P_1 }`, `N > 0` counts remaining iterations including the current one. Body steps preserve it, and only completing an iteration decreases it.
      - Not just syntactic sugar for `N` consecutive commands, since it supports `break`
      - Initial iteration:
      - ```
            N > 0   <{A, P_0} ∪ T, Q> --> <{A, P_1 } ∪ T, Q'>     # Internal computation step from P_0 to P_1
            ————————————————————————————————————————————————————————————————————————————————————————————————————————————
            <{A, repeat N { P_0 }; P} ∪ T, Q> --> <{A, repeat [P_0] N { P_1 }; P} ∪ T, Q>
        ```
      - An iteration with several repetitions remaining (the parameter is preserved):
      - ```
            N > 0   <{A, P_1} ∪ T, Q> --> <{A, P_2} ∪ T, Q'>     # Internal computation step from P_1 to P_2
            ————————————————————————————————————————————————————————————————————————————————————————————————————————————
            <{A, repeat [P_0] N { P_1 }; P} ∪ T, Q> --> <{A, repeat [P_0] N { P_1 }; P} ∪ T, Q'>
        ```
      - Entering a loop with zero requested iterations is semantically equivalent to `skip`:
      - ```
            N == 0
            ————————————————————————————————————————————————————————————————————————————————————————————————————————————
            <{A, repeat [P_0] N { ε }; P} ∪ T, Q> --> <{A, P} ∪ T, Q>
        ```
      - On completing an iteration, restore the saved body, or exit after the last iteration:
      - ```
            N > 1
            ————————————————————————————————————————————————————————————————————————————————————————————————————————————
            <{A, repeat [P_0] N { ε }; P} ∪ T, Q> --> <{A, repeat [P_0] (N-1) { P_0 }; P} ∪ T, Q>
        ```
      - `<{A, repeat [P_0] 1 { ε }; P} ∪ T, Q> --> <{A, P} ∪ T, Q>`
   - `break`:
      - Should ensure exit from the loop without executing code after itself
      - Exits the nearest enclosing `repeat` or `parallel`. The constructs `choose` and `receive alts` do not introduce a scope for `break`. A `break` without either enclosing construct is invalid and has no step.
      - ```
            N > 1
            ————————————————————————————————————————————————————————————————————————————————————————————————————————————
            <{A, repeat [P_1] N { break; P_2 }; P} ∪ T, Q> --> <{A, P} ∪ T, Q>`
        ```
      - `<{A, repeat [P_1] { break; P_2 }; P} ∪ T, Q> --> <{A, P} ∪ T, Q>`
6. Parallelism
    - Whatever is available for execution is launched
    - The `parallel` construct waits until each branch finishes its work (reaches `ε`)
    - `parallel` inside `parallel` is forbidden
    - This is a static restriction, including nesting through loops or choices. Only the selected branch needs a step; blocked or completed siblings do not prevent it.
    - ```
		i ∈ 0..N   <{A, P_i} ∪ T, Q> --> <{A, P_i'} ∪ T, Q_i'>     # Internal computation step from P_i to P_i'
		————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————
		<{A, parallel { P_0 } and ... and { P_i } and ... and { P_N }; P } ∪ T, Q> --> <{A, parallel { P_0 } and ... and { P_i' } and ... and { P_N }; P} ∪ T, Q_i'>
	  ```
    - `∀i=0..N <{A, parallel { ε } and ... and { ε } and .. and { ε }; P } ∪ T, Q> --> <{A, P} ∪ T, Q>`
    - `break`:
        - Aborts all execution branches
        - `∀i=0..N <{A, parallel { P_0 } and ... and { break; P_i } and ... and { P_N }; P} ∪ T, Q> --> <{A, P} ∪ T, Q>`
        - This exits the nearest parallel scope, cancels its other branches and resumes its continuation. The break has no priority over enabled sibling steps.
