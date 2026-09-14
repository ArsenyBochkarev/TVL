We need to introduce the configuration of the program. Configuration is a pair `<T, Q>`, where
- `T` -- is the set of threads in the program. Each thread may contain the current state of the thread. A thread state is a pair `{A, P}`, where
    - `A` -- the current actor
    - `P` -- the set of commands that the actor must execute
- `Q` -- a dictionary (map) of message queue dictionaries to each of the actors existing in the program. A message is characterized by its name `M`. Accessing elements:
    - `Q[A]` -- dictionary of queues to actor `A` from all actors
    - `Q[A][B]` -- message queue to actor `A` from actor `B`

*Notation:*
- *Sequential execution: symbol `;`*
- *Empty command: symbol `ε`*
- *List: `head :: tail`*
- *Channel size limit (message queue to some actor from another actor): `MAX_QUEUE_SIZE`*

All the rules below attempt to apply nondeterministically at each state of the configuration.

1. Message sending
    - Message `M` is added to the head of the message queue to actor `B` from actor `A`
    - ```
		Q[B][A] < MAX_QUEUE_SIZE   Q[B][A] == Q_b_a
		————————————————————————————————————————————————————————————————————————————————————————————————————————————
	    <{A, send M to B; P} ∪ T, Q ∪ Q[B][A]> --> <{A, P} ∪ T, Q ∪ Q[B][A](Q_b_a <- Q_b_a :: M)> `
	  ```
   - If there is not enough space in the message queue, wait until space becomes available.
2. Message receiving
    - Single message reception:
        - The head of the message queue to actor `A` from actor `B` contains the message `M`
        - ```
			Q[A][B] == M :: Q_a_b
			————————————————————————————————————————————————————————————————————————————————————————————————————————————
			<{A, receive M from B; P} ∪ T, Q ∪ Q[A][B]> --> <{A, P} ∪ T, Q ∪ Q[A][B](M :: Q_a_b <- Q_a_b)>
	      ```
    - Receiving one of possible messages:
        - The head of the message queue to actor `A` from actor `B_i` contains one of `N` possible messages `M_i`
        - ```
			∃B_b: Q[A][B_b] == M :: Q_a_b_j   M == M_j_b
			————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————
			∀b=0..N, ∀i=0..I, ∀j=0..J∀, k=0..K: <{A, receive alts { M_0_0 from B_0 => { P_0_0 } ... M_i_0 from B_0 => { P_i_0 } ... M_I_0 from B_0 => { P_I_0 } ... M_0_b from B_b => { P_0_b } ... M_j_b from B_b => { P_j_b } ... M_J_b from B_b => { P_J_b } ... M_0_N from B_N => { P_0_N } ... M_k_N from B_N => { P_k_N } ... M_K_N from B_N => { P_K_N }; P } ∪ T, Q ∪ Q[A][B_i]> --> <{A, P_j_b; P} ∪ T, Q ∪ Q[A][B_j](M :: Q_a_b_j <- Q_a_b_j)>
		  ```
          - `otherwise`: if at the time of reaching `receive alts` there is no matching message in the message queues, then we take this branch
          - ```
            ∀j=0..N Q[A][B_0] == K_0 :: Q_a_b_0 ... Q[A][B_j] == K_i :: Q_a_b_j ... Q[A][B_N] == K_N :: Q_a_b_N   ∀t=0..N, ∀b=0..N, ∀i=0..I, ∀j=0..J∀, k=0..K K_t != M_0_0 ... K_t != M_i_0 ... K_t != M_I_0 ... K_t != M_0_b ... K_t != M_j_b ... K_t != M_J_b ... K_t != M_0_N ... K_t != M_k_N ... K_t != M_K_N
            ————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————
            ∀b=0..N, ∀i=0..I, ∀j=0..J∀, k=0..K: <{A, receive alts { M_0_0 from B_0 => { P_0_0 } ... M_i_0 from B_0 => { P_i_0 } ... M_I_0 from B_0 => { P_I_0 } ... M_0_b from B_b => { P_0_b } ... M_j_b from B_b => { P_j_b } ... M_J_b from B_b => { P_J_b } ... M_0_N from B_N => { P_0_N } ... M_k_N from B_N => { P_k_N } ... M_K_N from B_N => { P_K_N } otherwise => { P_other }; P } ∪ T, Q> --> <{A, P_other; P} ∪ T, Q>
            ```
3. Nondeterministic choice
    - `∀i=0..N <{A, choose { P_0 } or ... or { P_i } or ... or { P_N }; P } ∪ T, Q> --> <{A, P_i; P} ∪ T, Q>`
4. Loop
    - "Infinite" loop:
      - Wait for `break` to exit
      - We do a computation step in the body, and also save the loop body as a parameter in `repeat`
      - ```
          <{A, P_1} ∪ T, Q> --> <{A, P_1'} ∪ T, Q'>
          ————————————————————————————————————————————————————————————————————————————————————————————————————————————
          <{A, repeat { P_1 }; P} ∪ T, Q> --> <{A, repeat [P_1] { P_1' }; P} ∪ T, Q'>
        ```
      - The original body parameter is preserved during the computation step in the body
      - ```
            <{A, P_1} ∪ T, Q> --> <{A, P_1'} ∪ T, Q'>
            ————————————————————————————————————————————————————————————————————————————————————————————————————————————
            <{A, repeat [P_0] { P_1 }; P} ∪ T, Q> --> <{A, repeat [P_0] { P_1' }; P} ∪ T, Q'>
        ```
      - Restart the loop, taking the body from the parameter
      - `<{A, repeat [P] { ε }; P} ∪ T, Q> --> <{A, repeat { P }; P} ∪ T, Q'>`
   - Finite loop:
      - Similar to the previous variant, but with a counter
      - Not just syntactic sugar, since we want to support `break` for it
      - ```
            N > 0   <{A, P_1} ∪ T, Q> --> <{A, P_1'} ∪ T, Q'>
            ————————————————————————————————————————————————————————————————————————————————————————————————————————————
            <{A, repeat N { P_1 }; P} ∪ T, Q> --> <{A, repeat [P_1] N-1 { P_1' }; P} ∪ T, Q'>
        ```
      - ```
            N > 0   <{A, P_1} ∪ T, Q> --> <{A, P_1'} ∪ T, Q'>
            ————————————————————————————————————————————————————————————————————————————————————————————————————————————
            <{A, repeat [P_0] N { P_1 }; P} ∪ T, Q> --> <{A, repeat [P_0] N-1 { P_1' }; P} ∪ T, Q'>
        ```
      - ```
            N == 0
            ————————————————————————————————————————————————————————————————————————————————————————————————————————————
            <{A, repeat N { P_1 }; P} ∪ T, Q> --> <{A, P} ∪ T, Q>
        ```
   - `break`:
      - Should ensure exit from the loop without executing code after itself
      - Should not work without `repeat`
      - `<{A, repeat N { break; P_2 }; P} ∪ T, Q> --> <{A, P} ∪ T, Q>`
      - `<{A, repeat N [P_1] { break; P_2 }; P} ∪ T, Q> --> <{A, P} ∪ T, Q>`
      - `<{A, repeat { break; P_2 }; P} ∪ T, Q> --> <{A, P} ∪ T, Q>`
      - `<{A, repeat [P_1] { break; P_2 }; P} ∪ T, Q> --> <{A, P} ∪ T, Q>`
5. Parallelism
    - Whatever is available for execution is launched
    - We wait until each of the branches finishes its work (reaches `ε`)
    - `parallel` inside `parallel` is forbidden
    - ```
		∀j=0..N <{A, P_0} ∪ T, Q> --> <{A, P_0'} ∪ T, Q_0'> ... <{A, P_j} ∪ T, Q> --> <{A, P_j'} ∪ T, Q_j'> ... <{A, P_N} ∪ T, Q> --> <{A, P_N'} ∪ T, Q_N'>   P_j != parallel; P_i_next
		————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————
		∀i=0..N <{A, parallel { P_0 } and ... and { P_i } and ... and { P_N }; P } ∪ T, Q> --> <{A, parallel { P_0 } and ... and { P_i' } and ... and { P_N }; P} ∪ T, Q>
	  ```
    - `∀i=0..N <{A, parallel { ε } and ... and { ε } and .. and { ε }; P } ∪ T, Q> --> <{A, P} ∪ T, Q>`
    - `skip`:
        - A completely empty action
        - `∀i=0..N <{A, parallel { P_0 } and ... and { skip; P_i } and ... and { P_N }; P } ∪ T, Q> --> <{A, parallel { P_0 } and ... and { P_i } and ... and { P_N }; P } ∪ T, Q>`
    - `break`:
        - Emergency aborts all execution branches
        - `∀i=0..N <{A, parallel { P_0 } and ... and { break; P_i } and ... and { P_N }; P} ∪ T, Q> --> <{A, P} ∪ T, Q>`
