---------------- MODULE TarryTraversal_nonparam ----------------
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANT K
Nodes == {"A", "B", "C", "D"}
Links == {e \in Nodes \X Nodes: e[1] # e[2]}
Neighbours == [n \in Nodes |->
  IF n \in {"A", "C"} THEN Nodes \ {n} ELSE {"A", "C"}]

(* --algorithm TarryTraversal_nonparam {
  variables q = [e \in Links |-> <<>>];

  macro Send(who, dst, msg) {
    await Len(q[<<who, dst>>]) < K;
    q[<<who, dst>>] := Append(q[<<who, dst>>], msg);
  }

  macro Receive(who, src, msg) {
    await Len(q[<<src, who>>]) > 0;
    await Head(q[<<src, who>>]) = msg;
    q[<<src, who>>] := Tail(q[<<src, who>>]);
  }

  fair process (Node_A = "A")
  variables parent_A = "", remaining_A = Neighbours["A"], target_A = "";
  {
  start_A:
    skip;

  explore_A:
    while (remaining_A # {}) {
      with (n \in remaining_A) {
        target_A := n;
        remaining_A := remaining_A \ {n};
      };
    send_token_A:
      Send("A", target_A, "TOK");
    await_return_A:
      Receive("A", target_A, "TOK");
    };
  return_to_parent_A:
    skip;

  decide_A:
    skip;
  }

  fair process (Node_B = "B")
  variables parent_B = "", remaining_B = Neighbours["B"], target_B = "";
  {
  start_B:
    skip;
    first_token_B:
      with (n \in remaining_B) {
        Receive("B", n, "TOK");
        parent_B := n;
        remaining_B := remaining_B \ {n};
      };
  explore_B:
    while (remaining_B # {}) {
      with (n \in remaining_B) {
        target_B := n;
        remaining_B := remaining_B \ {n};
      };
    send_token_B:
      Send("B", target_B, "TOK");
    await_return_B:
      Receive("B", target_B, "TOK");
    };
  return_to_parent_B:
    Send("B", parent_B, "TOK");
      goto Done;
  decide_B:
    skip;
  }

  fair process (Node_C = "C")
  variables parent_C = "", remaining_C = Neighbours["C"], target_C = "";
  {
  start_C:
    skip;
    first_token_C:
      with (n \in remaining_C) {
        Receive("C", n, "TOK");
        parent_C := n;
        remaining_C := remaining_C \ {n};
      };
  explore_C:
    while (remaining_C # {}) {
      with (n \in remaining_C) {
        target_C := n;
        remaining_C := remaining_C \ {n};
      };
    send_token_C:
      Send("C", target_C, "TOK");
    await_return_C:
      Receive("C", target_C, "TOK");
    };
  return_to_parent_C:
    Send("C", parent_C, "TOK");
      goto Done;
  decide_C:
    skip;
  }

  fair process (Node_D = "D")
  variables parent_D = "", remaining_D = Neighbours["D"], target_D = "";
  {
  start_D:
    skip;
    first_token_D:
      with (n \in remaining_D) {
        Receive("D", n, "TOK");
        parent_D := n;
        remaining_D := remaining_D \ {n};
      };
  explore_D:
    while (remaining_D # {}) {
      with (n \in remaining_D) {
        target_D := n;
        remaining_D := remaining_D \ {n};
      };
    send_token_D:
      Send("D", target_D, "TOK");
    await_return_D:
      Receive("D", target_D, "TOK");
    };
  return_to_parent_D:
    Send("D", parent_D, "TOK");
      goto Done;
  decide_D:
    skip;
  }

} *)
=============================================================================
