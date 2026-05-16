------------------------ MODULE ChangRobertsUnrolled ------------------------
EXTENDS Naturals, Sequences

(* --algorithm ChangRobertsUnrolled {
  variables
    msgsR1 = {}, msgsR2 = {}, msgsR3 = {}, msgsR4 = {};

  process (ActorR1 = "R1")
  variables
    initiator \in BOOLEAN,
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
            if (msg = "Stop") {
                msgsR2 := msgsR2 \cup {"Stop"};
                break;
            } else if (state = "lost") {
                msgsR2 := msgsR2 \cup {msg};
            } else if (msg < myId) {
                state := "lost";
                msgsR2 := msgsR2 \cup {msg};
            } else if (msg = myId) {
                state := "won";
                msgsR2 := msgsR2 \cup {"Stop"};
                break;
            }
        }
    }
  }

  process (ActorR2 = "R2")
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
            if (msg = "Stop") {
                msgsR3 := msgsR3 \cup {"Stop"};
                break;
            } else if (state = "lost") {
                msgsR3 := msgsR3 \cup {msg};
            } else if (msg < myId) {
                state := "lost";
                msgsR3 := msgsR3 \cup {msg};
            } else if (msg = myId) {
                state := "won";
                msgsR3 := msgsR3 \cup {"Stop"};
                break;
            }
        }
    }
  }

  process (ActorR3 = "R3")
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
            if (msg = "Stop") {
                msgsR4 := msgsR4 \cup {"Stop"};
                break;
            } else if (state = "lost") {
                msgsR4 := msgsR4 \cup {msg};
            } else if (msg < myId) {
                state := "lost";
                msgsR4 := msgsR4 \cup {msg};
            } else if (msg = myId) {
                state := "won";
                msgsR4 := msgsR4 \cup {"Stop"};
                break;
            }
        }
    }
  }

  process (ActorR4 = "R4")
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
            if (msg = "Stop") {
                msgsR1 := msgsR1 \cup {"Stop"};
                break;
            } else if (state = "lost") {
                msgsR1 := msgsR1 \cup {msg};
            } else if (msg < myId) {
                state := "lost";
                msgsR1 := msgsR1 \cup {msg};
            } else if (msg = myId) {
                state := "won";
                msgsR1 := msgsR1 \cup {"Stop"};
                break;
            }
        }
    }
  }
} *)

SingleLeader ==
  [](
    (state["R1"] = "won" => (state["R2"] /= "won" /\ state["R3"] /= "won" /\ state["R4"] /= "won")) /\
    (state["R2"] = "won" => (state["R1"] /= "won" /\ state["R3"] /= "won" /\ state["R4"] /= "won")) /\
    (state["R3"] = "won" => (state["R1"] /= "won" /\ state["R2"] /= "won" /\ state["R4"] /= "won")) /\
    (state["R4"] = "won" => (state["R1"] /= "won" /\ state["R2"] /= "won" /\ state["R3"] /= "won"))
  )

AllTerminated == 
    (\E n \in {"R1", "R2", "R3", "R4"} : initiator[n]) ~>  <>(\A p \in {"R1", "R2", "R3", "R4"} : pc[p] = "Done")
