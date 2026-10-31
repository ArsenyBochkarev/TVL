---------------- MODULE RingDKR_nonparam ----------------
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANT K
Nodes == 1..4
Links == {e \in Nodes \X Nodes: e[1] # e[2]}
Succ(n) == IF n = 4 THEN 1 ELSE n + 1
Pred(n) == IF n = 1 THEN 4 ELSE n - 1

(* --algorithm RingDKR_nonparam {
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

  fair process (Participant_N1 = 1)
  variables msg_N1 = <<"", 0>>, first_N1 = 0;
  {
  initial_send_N1:
    Send(1, Succ(1), <<"One", 1>>);
  active_N1:
    while (TRUE) {
      with (m \in {<<"One", n>> : n \in Nodes} \cup {<<"Win", n>> : n \in Nodes}) {
        Receive(1, Pred(1), m);
        msg_N1 := m;
      };
    classify_N1:
      if (msg_N1[1] = "Win") {
        if (msg_N1[2] = 1) {
        active_me_N1:
          Send(1, Succ(1), msg_N1);
          goto relay_N1;
        } else {
        active_other_N1:
          Send(1, Succ(1), msg_N1);
          goto relay_N1;
        };
      } else {
        if (msg_N1[2] = 1) {
        elected_me_N1:
          Send(1, Succ(1), <<"Win", 1>>);
          goto relay_N1;
        } else {
          first_N1 := msg_N1[2];
        forward_first_N1:
          Send(1, Succ(1), <<"Two", first_N1>>);
        second_receive_N1:
          with (n \in Nodes) {
            Receive(1, Pred(1), <<"Two", n>>);
            msg_N1 := <<"Two", n>>;
          };
        compare_N1:
          if (first_N1 > 1 /\ msg_N1[2] < first_N1) {
          new_candidate_N1:
            Send(1, Succ(1), <<"One", first_N1>>);
            if (first_N1 = 4) {
            await_maximum_N1:
              Receive(1, Pred(1), <<"One", 4>>);
            announce_maximum_N1:
              Send(1, Succ(1), <<"Win", 4>>);
              goto relay_N1;
            };
          } else {
            goto relay_N1;
          };
        };
      };
    };
  relay_N1:
    while (TRUE) {
      with (m \in {<<k, n>> : k \in {"One", "Two", "Win"}, n \in Nodes}) {
        Receive(1, Pred(1), m);
        msg_N1 := m;
      };
    relay_classify_N1:
      if (msg_N1[1] = "Win" /\ msg_N1[2] = 1) {
      relay_me_N1:
        Send(1, Succ(1), msg_N1);
        goto Done;
      } else {
      relay_forward_N1:
        Send(1, Succ(1), msg_N1);
        if (msg_N1[1] = "Win") { goto Done; };
      };
    };
  }

  fair process (Participant_N2 = 2)
  variables msg_N2 = <<"", 0>>, first_N2 = 0;
  {
  initial_send_N2:
    Send(2, Succ(2), <<"One", 2>>);
  active_N2:
    while (TRUE) {
      with (m \in {<<"One", n>> : n \in Nodes} \cup {<<"Win", n>> : n \in Nodes}) {
        Receive(2, Pred(2), m);
        msg_N2 := m;
      };
    classify_N2:
      if (msg_N2[1] = "Win") {
        if (msg_N2[2] = 2) {
        active_me_N2:
          Send(2, Succ(2), msg_N2);
          goto relay_N2;
        } else {
        active_other_N2:
          Send(2, Succ(2), msg_N2);
          goto relay_N2;
        };
      } else {
        if (msg_N2[2] = 2) {
        elected_me_N2:
          Send(2, Succ(2), <<"Win", 2>>);
          goto relay_N2;
        } else {
          first_N2 := msg_N2[2];
        forward_first_N2:
          Send(2, Succ(2), <<"Two", first_N2>>);
        second_receive_N2:
          with (n \in Nodes) {
            Receive(2, Pred(2), <<"Two", n>>);
            msg_N2 := <<"Two", n>>;
          };
        compare_N2:
          if (first_N2 > 2 /\ msg_N2[2] < first_N2) {
          new_candidate_N2:
            Send(2, Succ(2), <<"One", first_N2>>);
            if (first_N2 = 4) {
            await_maximum_N2:
              Receive(2, Pred(2), <<"One", 4>>);
            announce_maximum_N2:
              Send(2, Succ(2), <<"Win", 4>>);
              goto relay_N2;
            };
          } else {
            goto relay_N2;
          };
        };
      };
    };
  relay_N2:
    while (TRUE) {
      with (m \in {<<k, n>> : k \in {"One", "Two", "Win"}, n \in Nodes}) {
        Receive(2, Pred(2), m);
        msg_N2 := m;
      };
    relay_classify_N2:
      if (msg_N2[1] = "Win" /\ msg_N2[2] = 2) {
      relay_me_N2:
        Send(2, Succ(2), msg_N2);
        goto Done;
      } else {
      relay_forward_N2:
        Send(2, Succ(2), msg_N2);
        if (msg_N2[1] = "Win") { goto Done; };
      };
    };
  }

  fair process (Participant_N3 = 3)
  variables msg_N3 = <<"", 0>>, first_N3 = 0;
  {
  initial_send_N3:
    Send(3, Succ(3), <<"One", 3>>);
  active_N3:
    while (TRUE) {
      with (m \in {<<"One", n>> : n \in Nodes} \cup {<<"Win", n>> : n \in Nodes}) {
        Receive(3, Pred(3), m);
        msg_N3 := m;
      };
    classify_N3:
      if (msg_N3[1] = "Win") {
        if (msg_N3[2] = 3) {
        active_me_N3:
          Send(3, Succ(3), msg_N3);
          goto relay_N3;
        } else {
        active_other_N3:
          Send(3, Succ(3), msg_N3);
          goto relay_N3;
        };
      } else {
        if (msg_N3[2] = 3) {
        elected_me_N3:
          Send(3, Succ(3), <<"Win", 3>>);
          goto relay_N3;
        } else {
          first_N3 := msg_N3[2];
        forward_first_N3:
          Send(3, Succ(3), <<"Two", first_N3>>);
        second_receive_N3:
          with (n \in Nodes) {
            Receive(3, Pred(3), <<"Two", n>>);
            msg_N3 := <<"Two", n>>;
          };
        compare_N3:
          if (first_N3 > 3 /\ msg_N3[2] < first_N3) {
          new_candidate_N3:
            Send(3, Succ(3), <<"One", first_N3>>);
            if (first_N3 = 4) {
            await_maximum_N3:
              Receive(3, Pred(3), <<"One", 4>>);
            announce_maximum_N3:
              Send(3, Succ(3), <<"Win", 4>>);
              goto relay_N3;
            };
          } else {
            goto relay_N3;
          };
        };
      };
    };
  relay_N3:
    while (TRUE) {
      with (m \in {<<k, n>> : k \in {"One", "Two", "Win"}, n \in Nodes}) {
        Receive(3, Pred(3), m);
        msg_N3 := m;
      };
    relay_classify_N3:
      if (msg_N3[1] = "Win" /\ msg_N3[2] = 3) {
      relay_me_N3:
        Send(3, Succ(3), msg_N3);
        goto Done;
      } else {
      relay_forward_N3:
        Send(3, Succ(3), msg_N3);
        if (msg_N3[1] = "Win") { goto Done; };
      };
    };
  }

  fair process (Participant_N4 = 4)
  variables msg_N4 = <<"", 0>>, first_N4 = 0;
  {
  initial_send_N4:
    Send(4, Succ(4), <<"One", 4>>);
  active_N4:
    while (TRUE) {
      with (m \in {<<"One", n>> : n \in Nodes} \cup {<<"Win", n>> : n \in Nodes}) {
        Receive(4, Pred(4), m);
        msg_N4 := m;
      };
    classify_N4:
      if (msg_N4[1] = "Win") {
        if (msg_N4[2] = 4) {
        active_me_N4:
          Send(4, Succ(4), msg_N4);
          goto relay_N4;
        } else {
        active_other_N4:
          Send(4, Succ(4), msg_N4);
          goto relay_N4;
        };
      } else {
        if (msg_N4[2] = 4) {
        elected_me_N4:
          Send(4, Succ(4), <<"Win", 4>>);
          goto relay_N4;
        } else {
          first_N4 := msg_N4[2];
        forward_first_N4:
          Send(4, Succ(4), <<"Two", first_N4>>);
        second_receive_N4:
          with (n \in Nodes) {
            Receive(4, Pred(4), <<"Two", n>>);
            msg_N4 := <<"Two", n>>;
          };
        compare_N4:
          if (first_N4 > 4 /\ msg_N4[2] < first_N4) {
          new_candidate_N4:
            Send(4, Succ(4), <<"One", first_N4>>);
            if (first_N4 = 4) {
            await_maximum_N4:
              Receive(4, Pred(4), <<"One", 4>>);
            announce_maximum_N4:
              Send(4, Succ(4), <<"Win", 4>>);
              goto relay_N4;
            };
          } else {
            goto relay_N4;
          };
        };
      };
    };
  relay_N4:
    while (TRUE) {
      with (m \in {<<k, n>> : k \in {"One", "Two", "Win"}, n \in Nodes}) {
        Receive(4, Pred(4), m);
        msg_N4 := m;
      };
    relay_classify_N4:
      if (msg_N4[1] = "Win" /\ msg_N4[2] = 4) {
      relay_me_N4:
        Send(4, Succ(4), msg_N4);
        goto Done;
      } else {
      relay_forward_N4:
        Send(4, Succ(4), msg_N4);
        if (msg_N4[1] = "Win") { goto Done; };
      };
    };
  }

} *)
=============================================================================
