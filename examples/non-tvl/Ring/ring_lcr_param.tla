---------------- MODULE ring_lcr_param ----------------
EXTENDS Naturals, Sequences, FiniteSets

Nodes == 1..4
Succ(n) == IF n = 4 THEN 1 ELSE n + 1

(* --algorithm ring_lcr_param {
  variables msgs = [n \in Nodes |-> {}],
            initiator \in {f \in [Nodes -> BOOLEAN]: f[1]};

  macro Forward(who, incoming, outgoing) {
    msgs := [msgs EXCEPT ![who] = @ \ {incoming},
                        ![Succ(who)] = @ \cup {outgoing}];
  }

  fair process (Participant \in Nodes)
  variables state = IF initiator[self] THEN "cand" ELSE "lost",
            myId = self;
  {
  init:
    if (initiator[self]) {
      msgs[Succ(self)] := msgs[Succ(self)] \cup {myId};
    };
  loop:
    while (TRUE) {
      await msgs[self] /= {};
      with (msg \in msgs[self]) {
        if (msg = 0) {
          Forward(self, msg, 0);
          goto Done;
        } else if (state = "lost") {
          Forward(self, msg, msg);
        } else if (msg > myId) {
          state := "lost";
          Forward(self, msg, msg);
        } else if (msg = myId) {
          state := "won";
          Forward(self, msg, 0);
          goto Done;
        } else {
          msgs[self] := msgs[self] \ {msg};
        };
      };
    };
  }
} *)

SingleLeader == [](Cardinality({n \in Nodes: state[n] = "won"}) <= 1)
AllTerminated == (\E n \in Nodes: initiator[n]) ~> <>(\A n \in Nodes: pc[n] = "Done")
=============================================================================
