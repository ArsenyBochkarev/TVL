---------------- MODULE TarryTraversal_param ----------------
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANT K
Nodes == {"A", "B", "C", "D"}
Links == {e \in Nodes \X Nodes: e[1] # e[2]}
Neighbours == [n \in Nodes |->
  IF n \in {"A", "C"} THEN Nodes \ {n} ELSE {"A", "C"}]

(* --algorithm TarryTraversal_param {
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

  fair process (Node \in Nodes)
  variables parent = "", remaining = Neighbours[self], target = "";
  {
  start:
    if (self # "A") {
    first_token:
      with (n \in remaining) {
        Receive(self, n, "TOK");
        parent := n;
        remaining := remaining \ {n};
      };
    };
  explore:
    while (remaining # {}) {
      with (n \in remaining) {
        target := n;
        remaining := remaining \ {n};
      };
    send_token:
      Send(self, target, "TOK");
    await_return:
      Receive(self, target, "TOK");
    };
  return_to_parent:
    if (self # "A") {
      Send(self, parent, "TOK");
      goto Done;
    };
  decide:
    skip;
  }
} *)
=============================================================================
