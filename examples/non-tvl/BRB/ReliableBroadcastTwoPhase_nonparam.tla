---------------- MODULE ReliableBroadcastTwoPhase_nonparam ----------------
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANT K
Nodes == 1..4
Links == {e \in Nodes \X Nodes: e[1] # e[2]}

(* --algorithm ReliableBroadcastTwoPhase_nonparam {
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

  fair process (Node_N1 = 1)
  variables targets_N1 = <<>>, out_N1 = "", j_N1 = 1, count_N1 = 0;
  {
  start_N1:
    targets_N1 := <<2, 3, 4>>;
    send_init_N1:
      while (j_N1 <= Len(targets_N1)) {
        Send(1, targets_N1[j_N1], "INIT");
        j_N1 := j_N1 + 1;
      };
  choose_path_N1:
    either {
      goto echo_phase_N1;
    } or {
      goto vote_fallback_N1;
    };
  echo_phase_N1:
    count_N1 := 0;
  collect_echo_N1:
    while (count_N1 < 2) {
      with (n \in Nodes \ {1}) { Receive(1, n, "ECHO"); };
      count_N1 := count_N1 + 1;
    };
    out_N1 := "VOTE";
    goto broadcast_vote_N1;
  broadcast_vote_N1:
    targets_N1 := SelectSeq(<<1, 2, 3, 4>>, LAMBDA n: n # 1);
    j_N1 := 1;
  send_vote_N1:
    while (j_N1 <= Len(targets_N1)) {
      Send(1, targets_N1[j_N1], out_N1);
      j_N1 := j_N1 + 1;
    };
    count_N1 := 0;
  collect_vote_N1:
    while (count_N1 < 2) {
      with (n \in Nodes \ {1}) { Receive(1, n, "VOTE"); };
      count_N1 := count_N1 + 1;
    };
    goto Done;
  vote_fallback_N1:
    with (n \in Nodes \ {1}) { Receive(1, n, "VOTE"); };
  fallback_loop_N1:
    while (TRUE) {
      with (n \in Nodes \ {1}) { Receive(1, n, "VOTE"); };
    fallback_broadcast_N1:
      targets_N1 := SelectSeq(<<1, 2, 3, 4>>, LAMBDA n: n # 1);
      j_N1 := 1;
    fallback_send_N1:
      while (j_N1 <= Len(targets_N1)) {
        Send(1, targets_N1[j_N1], "VOTE");
        j_N1 := j_N1 + 1;
      };
      skip;

    };
  }

  fair process (Node_N2 = 2)
  variables targets_N2 = <<>>, out_N2 = "", j_N2 = 1, count_N2 = 0;
  {
  start_N2:
    skip;

  choose_path_N2:
    either {
      skip;
    receive_init_N2:
        Receive(2, 1, "INIT");
        out_N2 := "ECHO";
        goto broadcast_echo_N2;
    } or {
      goto vote_fallback_N2;
    };
  broadcast_echo_N2:
    targets_N2 := SelectSeq(<<1, 2, 3, 4>>, LAMBDA n: n # 2);
    j_N2 := 1;
  send_echo_N2:
    while (j_N2 <= Len(targets_N2)) {
      Send(2, targets_N2[j_N2], out_N2);
      j_N2 := j_N2 + 1;
    };
  echo_phase_N2:
    count_N2 := 0;
  collect_echo_N2:
    while (count_N2 < 2) {
      with (n \in Nodes \ {2}) { Receive(2, n, "ECHO"); };
      count_N2 := count_N2 + 1;
    };
    out_N2 := "VOTE";
    goto broadcast_vote_N2;
  broadcast_vote_N2:
    targets_N2 := SelectSeq(<<1, 2, 3, 4>>, LAMBDA n: n # 2);
    j_N2 := 1;
  send_vote_N2:
    while (j_N2 <= Len(targets_N2)) {
      Send(2, targets_N2[j_N2], out_N2);
      j_N2 := j_N2 + 1;
    };
    count_N2 := 0;
  collect_vote_N2:
    while (count_N2 < 2) {
      with (n \in Nodes \ {2}) { Receive(2, n, "VOTE"); };
      count_N2 := count_N2 + 1;
    };
    goto Done;
  vote_fallback_N2:
    with (n \in Nodes \ {2}) { Receive(2, n, "VOTE"); };
  fallback_loop_N2:
    while (TRUE) {
      with (n \in Nodes \ {2}) { Receive(2, n, "VOTE"); };
    fallback_broadcast_N2:
      targets_N2 := SelectSeq(<<1, 2, 3, 4>>, LAMBDA n: n # 2);
      j_N2 := 1;
    fallback_send_N2:
      while (j_N2 <= Len(targets_N2)) {
        Send(2, targets_N2[j_N2], "VOTE");
        j_N2 := j_N2 + 1;
      };
      goto Done;
    };
  }

  fair process (Node_N3 = 3)
  variables targets_N3 = <<>>, out_N3 = "", j_N3 = 1, count_N3 = 0;
  {
  start_N3:
    skip;

  choose_path_N3:
    either {
      skip;
    receive_init_N3:
        Receive(3, 1, "INIT");
        out_N3 := "ECHO";
        goto broadcast_echo_N3;
    } or {
      goto vote_fallback_N3;
    };
  broadcast_echo_N3:
    targets_N3 := SelectSeq(<<1, 2, 3, 4>>, LAMBDA n: n # 3);
    j_N3 := 1;
  send_echo_N3:
    while (j_N3 <= Len(targets_N3)) {
      Send(3, targets_N3[j_N3], out_N3);
      j_N3 := j_N3 + 1;
    };
  echo_phase_N3:
    count_N3 := 0;
  collect_echo_N3:
    while (count_N3 < 2) {
      with (n \in Nodes \ {3}) { Receive(3, n, "ECHO"); };
      count_N3 := count_N3 + 1;
    };
    out_N3 := "VOTE";
    goto broadcast_vote_N3;
  broadcast_vote_N3:
    targets_N3 := SelectSeq(<<1, 2, 3, 4>>, LAMBDA n: n # 3);
    j_N3 := 1;
  send_vote_N3:
    while (j_N3 <= Len(targets_N3)) {
      Send(3, targets_N3[j_N3], out_N3);
      j_N3 := j_N3 + 1;
    };
    count_N3 := 0;
  collect_vote_N3:
    while (count_N3 < 2) {
      with (n \in Nodes \ {3}) { Receive(3, n, "VOTE"); };
      count_N3 := count_N3 + 1;
    };
    goto Done;
  vote_fallback_N3:
    with (n \in Nodes \ {3}) { Receive(3, n, "VOTE"); };
  fallback_loop_N3:
    while (TRUE) {
      with (n \in Nodes \ {3}) { Receive(3, n, "VOTE"); };
    fallback_broadcast_N3:
      targets_N3 := SelectSeq(<<1, 2, 3, 4>>, LAMBDA n: n # 3);
      j_N3 := 1;
    fallback_send_N3:
      while (j_N3 <= Len(targets_N3)) {
        Send(3, targets_N3[j_N3], "VOTE");
        j_N3 := j_N3 + 1;
      };
      goto Done;
    };
  }

  fair process (Node_N4 = 4)
  variables targets_N4 = <<>>, out_N4 = "", j_N4 = 1, count_N4 = 0;
  {
  start_N4:
    skip;

  choose_path_N4:
    either {
      skip;
    receive_init_N4:
        Receive(4, 1, "INIT");
        out_N4 := "ECHO";
        goto broadcast_echo_N4;
    } or {
      goto vote_fallback_N4;
    };
  broadcast_echo_N4:
    targets_N4 := SelectSeq(<<1, 2, 3, 4>>, LAMBDA n: n # 4);
    j_N4 := 1;
  send_echo_N4:
    while (j_N4 <= Len(targets_N4)) {
      Send(4, targets_N4[j_N4], out_N4);
      j_N4 := j_N4 + 1;
    };
  echo_phase_N4:
    count_N4 := 0;
  collect_echo_N4:
    while (count_N4 < 2) {
      with (n \in Nodes \ {4}) { Receive(4, n, "ECHO"); };
      count_N4 := count_N4 + 1;
    };
    out_N4 := "VOTE";
    goto broadcast_vote_N4;
  broadcast_vote_N4:
    targets_N4 := SelectSeq(<<1, 2, 3, 4>>, LAMBDA n: n # 4);
    j_N4 := 1;
  send_vote_N4:
    while (j_N4 <= Len(targets_N4)) {
      Send(4, targets_N4[j_N4], out_N4);
      j_N4 := j_N4 + 1;
    };
    count_N4 := 0;
  collect_vote_N4:
    while (count_N4 < 2) {
      with (n \in Nodes \ {4}) { Receive(4, n, "VOTE"); };
      count_N4 := count_N4 + 1;
    };
    goto Done;
  vote_fallback_N4:
    with (n \in Nodes \ {4}) { Receive(4, n, "VOTE"); };
  fallback_loop_N4:
    while (TRUE) {
      with (n \in Nodes \ {4}) { Receive(4, n, "VOTE"); };
    fallback_broadcast_N4:
      targets_N4 := SelectSeq(<<1, 2, 3, 4>>, LAMBDA n: n # 4);
      j_N4 := 1;
    fallback_send_N4:
      while (j_N4 <= Len(targets_N4)) {
        Send(4, targets_N4[j_N4], "VOTE");
        j_N4 := j_N4 + 1;
      };
      goto Done;
    };
  }

} *)
=============================================================================
