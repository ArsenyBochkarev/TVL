---------------- MODULE TreeWave_param ----------------
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANT K
Nodes == {"A", "B", "C"}
Links == {e \in Nodes \X Nodes: e[1] # e[2]}

(* --algorithm TreeWave_param {
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

  fair process (Leaf \in {"A", "C"}) {
  leaf_send:
    Send(self, "B", "TOK");
  leaf_receive:
    Receive(self, "B", "TOK");
  }

  fair process (Controller \in {"B"})
  variables first = "", second = "";
  {
  first_receive:
    with (n \in {"A", "C"}) {
      Receive(self, n, "TOK");
      first := n;
      second := IF n = "A" THEN "C" ELSE "A";
    };
  second_receive:
    Receive(self, second, "TOK");
  first_reply:
    Send(self, first, "TOK");
  second_reply:
    Send(self, second, "TOK");
  }
} *)
=============================================================================
