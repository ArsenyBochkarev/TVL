---------------- MODULE ReliableBroadcastTwoPhase_param ----------------
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANT K
Nodes == 1..4
Links == {e \in Nodes \X Nodes: e[1] # e[2]}

(* --algorithm ReliableBroadcastTwoPhase_param {
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
  variables targets = <<>>, out = "", j = 1, count = 0;
  {
  start:
    if (self = 1) {
      targets := <<2, 3, 4>>;
    send_init:
      while (j <= Len(targets)) {
        Send(self, targets[j], "INIT");
        j := j + 1;
      };
    };
  choose_path:
    either {
      if (self # 1) {
      receive_init:
        Receive(self, 1, "INIT");
        out := "ECHO";
        goto broadcast_echo;
      } else { goto echo_phase; };
    } or {
      goto vote_fallback;
    };
  broadcast_echo:
    targets := SelectSeq(<<1, 2, 3, 4>>, LAMBDA n: n # self);
    j := 1;
  send_echo:
    while (j <= Len(targets)) {
      Send(self, targets[j], out);
      j := j + 1;
    };
  echo_phase:
    count := 0;
  collect_echo:
    while (count < 2) {
      with (n \in Nodes \ {self}) { Receive(self, n, "ECHO"); };
      count := count + 1;
    };
    out := "VOTE";
    goto broadcast_vote;
  broadcast_vote:
    targets := SelectSeq(<<1, 2, 3, 4>>, LAMBDA n: n # self);
    j := 1;
  send_vote:
    while (j <= Len(targets)) {
      Send(self, targets[j], out);
      j := j + 1;
    };
    count := 0;
  collect_vote:
    while (count < 2) {
      with (n \in Nodes \ {self}) { Receive(self, n, "VOTE"); };
      count := count + 1;
    };
    goto Done;
  vote_fallback:
    with (n \in Nodes \ {self}) { Receive(self, n, "VOTE"); };
  fallback_loop:
    while (TRUE) {
      with (n \in Nodes \ {self}) { Receive(self, n, "VOTE"); };
    fallback_broadcast:
      targets := SelectSeq(<<1, 2, 3, 4>>, LAMBDA n: n # self);
      j := 1;
    fallback_send:
      while (j <= Len(targets)) {
        Send(self, targets[j], "VOTE");
        j := j + 1;
      };
      if (self # 1) { goto Done; };
    };
  }
} *)
=============================================================================
