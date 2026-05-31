------------------------ MODULE ring_lcr_nonparam ------------------------
EXTENDS Naturals, Sequences

(* --algorithm ring_lcr_nonparam {
  variables
    msgsR1 = {}, msgsR2 = {}, msgsR3 = {}, msgsR4 = {};

  fair process (ActorR1 = "R1")
  variables
    initiator = TRUE,
    state = IF initiator THEN "cand" ELSE "lost",
    myId = 1;
  {
  a1_init:
    if (initiator) {
        msgsR2 := msgsR2 \cup {myId};
    };
  a1_loop:
    while (TRUE) {
        await msgsR1 /= {};
        with (msg \in msgsR1) {
            msgsR1 := msgsR1 \ {msg};
            if (msg = 0) {
                msgsR2 := msgsR2 \cup {0};
                goto Done;
            } else if (state = "lost") {
                msgsR2 := msgsR2 \cup {msg};
            } else if (msg > myId) {
                state := "lost";
                msgsR2 := msgsR2 \cup {msg};
            } else if (msg = myId) {
                state := "won";
                msgsR2 := msgsR2 \cup {0};
                goto Done;
            }
        }
    }
  }

  fair process (ActorR2 = "R2")
  variables
    initiator \in BOOLEAN,
    state = IF initiator THEN "cand" ELSE "lost",
    myId = 2;
  {
  a2_init:
    if (initiator) {
        msgsR3 := msgsR3 \cup {myId};
    };
  a2_loop:
    while (TRUE) {
        await msgsR2 /= {};
        with (msg \in msgsR2) {
            msgsR2 := msgsR2 \ {msg};
            if (msg = 0) {
                msgsR3 := msgsR3 \cup {0};
                goto Done;
            } else if (state = "lost") {
                msgsR3 := msgsR3 \cup {msg};
            } else if (msg > myId) {
                state := "lost";
                msgsR3 := msgsR3 \cup {msg};
            } else if (msg = myId) {
                state := "won";
                msgsR3 := msgsR3 \cup {0};
                goto Done;
            }
        }
    }
  }

  fair process (ActorR3 = "R3")
  variables
    initiator \in BOOLEAN,
    state = IF initiator THEN "cand" ELSE "lost",
    myId = 3;
  {
  a3_init:
    if (initiator) {
        msgsR4 := msgsR4 \cup {myId};
    };
  a3_loop:
    while (TRUE) {
        await msgsR3 /= {};
        with (msg \in msgsR3) {
            msgsR3 := msgsR3 \ {msg};
            if (msg = 0) {
                msgsR4 := msgsR4 \cup {0};
                goto Done;
            } else if (state = "lost") {
                msgsR4 := msgsR4 \cup {msg};
            } else if (msg > myId) {
                state := "lost";
                msgsR4 := msgsR4 \cup {msg};
            } else if (msg = myId) {
                state := "won";
                msgsR4 := msgsR4 \cup {0};
                goto Done;
            }
        }
    }
  }

  fair process (ActorR4 = "R4")
  variables
    initiator \in BOOLEAN,
    state = IF initiator THEN "cand" ELSE "lost",
    myId = 4;
  {
  a4_init:
    if (initiator) {
        msgsR1 := msgsR1 \cup {myId};
    };
  a4_loop:
    while (TRUE) {
        await msgsR4 /= {};
        with (msg \in msgsR4) {
            msgsR4 := msgsR4 \ {msg};
            if (msg = 0) {
                msgsR1 := msgsR1 \cup {0};
                goto Done;
            } else if (state = "lost") {
                msgsR1 := msgsR1 \cup {msg};
            } else if (msg > myId) {
                state := "lost";
                msgsR1 := msgsR1 \cup {msg};
            } else if (msg = myId) {
                state := "won";
                msgsR1 := msgsR1 \cup {0};
                goto Done;
            }
        }
    }
  }
} *)

SingleLeader ==
  [](
    (state_ = "won" => (state_A /= "won" /\ state_Ac /= "won" /\ state /= "won")) /\
    (state_A = "won" => (state_ /= "won" /\ state_Ac /= "won" /\ state /= "won")) /\
    (state_Ac = "won" => (state_ /= "won" /\ state_A /= "won" /\ state /= "won")) /\
    (state = "won" => (state_ /= "won" /\ state_A /= "won" /\ state_Ac /= "won"))
  )

AllTerminated == 
    (initiator_ \/ initiator_A \/ initiator_Ac \/ initiator) ~>  <>(\A p \in {"R1", "R2", "R3", "R4"} : pc[p] = "Done")
====
