---------------- MODULE TreeWave_nonparam ----------------
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANT K
Nodes == {"A", "B", "C"}
Links == {e \in Nodes \X Nodes: e[1] # e[2]}

(* --algorithm TreeWave_nonparam {
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

  fair process (Leaf_A = "A") {
  leaf_send_A:
    Send("A", "B", "TOK");
  leaf_receive_A:
    Receive("A", "B", "TOK");
  }

  fair process (Leaf_C = "C") {
  leaf_send_C:
    Send("C", "B", "TOK");
  leaf_receive_C:
    Receive("C", "B", "TOK");
  }

  fair process (Controller_B = "B")
  variables first_B = "", second_B = "";
  {
  first_receive_B:
    with (n \in {"A", "C"}) {
      Receive("B", n, "TOK");
      first_B := n;
      second_B := IF n = "A" THEN "C" ELSE "A";
    };
  second_receive_B:
    Receive("B", second_B, "TOK");
  first_reply_B:
    Send("B", first_B, "TOK");
  second_reply_B:
    Send("B", second_B, "TOK");
  }

} *)
=============================================================================
