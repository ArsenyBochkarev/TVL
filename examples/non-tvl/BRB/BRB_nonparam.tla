---------------- MODULE BRB_nonparam ----------------
EXTENDS Naturals, FiniteSets

Corr == {"N1", "N2", "N3"}
Faulty == {"N4"}
M == {"ECHO"}
ByzMsgs == Faulty \X M

(* --algorithm BRB_nonparam {
  variables phase \in [Corr -> {"V0", "V1"}],
            rcvd = [i \in Corr |-> {}],
            sent = {};

  fair process (Node_N1 = "N1") {
  step_N1:
    while (TRUE) {
      with (newMessages \in SUBSET (sent \cup ByzMsgs)) {
        rcvd["N1"] := rcvd["N1"] \cup newMessages;
        either {
          await phase["N1"] = "V1";
          phase["N1"] := "SE";
          sent := sent \cup {<<"N1", "ECHO">>};
        } or {
          await phase["N1"] \in {"V0", "V1"};
          await Cardinality(rcvd["N1"]) >= 2;
          await Cardinality(rcvd["N1"]) < 3;
          phase["N1"] := "SE";
          sent := sent \cup {<<"N1", "ECHO">>};
        } or {
          await phase["N1"] \in {"V0", "V1"};
          await Cardinality(rcvd["N1"]) >= 3;
          phase["N1"] := "AC";
          sent := sent \cup {<<"N1", "ECHO">>};
        } or {
          await phase["N1"] = "SE";
          await Cardinality(rcvd["N1"]) >= 3;
          phase["N1"] := "AC";
        };
      };
    };
  }

  fair process (Node_N2 = "N2") {
  step_N2:
    while (TRUE) {
      with (newMessages \in SUBSET (sent \cup ByzMsgs)) {
        rcvd["N2"] := rcvd["N2"] \cup newMessages;
        either {
          await phase["N2"] = "V1";
          phase["N2"] := "SE";
          sent := sent \cup {<<"N2", "ECHO">>};
        } or {
          await phase["N2"] \in {"V0", "V1"};
          await Cardinality(rcvd["N2"]) >= 2;
          await Cardinality(rcvd["N2"]) < 3;
          phase["N2"] := "SE";
          sent := sent \cup {<<"N2", "ECHO">>};
        } or {
          await phase["N2"] \in {"V0", "V1"};
          await Cardinality(rcvd["N2"]) >= 3;
          phase["N2"] := "AC";
          sent := sent \cup {<<"N2", "ECHO">>};
        } or {
          await phase["N2"] = "SE";
          await Cardinality(rcvd["N2"]) >= 3;
          phase["N2"] := "AC";
        };
      };
    };
  }

  fair process (Node_N3 = "N3") {
  step_N3:
    while (TRUE) {
      with (newMessages \in SUBSET (sent \cup ByzMsgs)) {
        rcvd["N3"] := rcvd["N3"] \cup newMessages;
        either {
          await phase["N3"] = "V1";
          phase["N3"] := "SE";
          sent := sent \cup {<<"N3", "ECHO">>};
        } or {
          await phase["N3"] \in {"V0", "V1"};
          await Cardinality(rcvd["N3"]) >= 2;
          await Cardinality(rcvd["N3"]) < 3;
          phase["N3"] := "SE";
          sent := sent \cup {<<"N3", "ECHO">>};
        } or {
          await phase["N3"] \in {"V0", "V1"};
          await Cardinality(rcvd["N3"]) >= 3;
          phase["N3"] := "AC";
          sent := sent \cup {<<"N3", "ECHO">>};
        } or {
          await phase["N3"] = "SE";
          await Cardinality(rcvd["N3"]) >= 3;
          phase["N3"] := "AC";
        };
      };
    };
  }

} *)

RelayLtl == []((\E i \in Corr: phase[i] = "AC") => <>(\A i \in Corr: phase[i] = "AC"))
CorrLtl == (\A i \in Corr: phase[i] = "V1") => <>(\A i \in Corr: phase[i] = "AC")
InitNoBcast == Init /\ phase = [i \in Corr |-> "V0"]
SpecNoBcast == InitNoBcast /\ [][Next]_vars
Unforg == (\A i \in Corr: phase[i] /= "AC")
=============================================================================
