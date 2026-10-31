---------------- MODULE BRB_param ----------------
EXTENDS Naturals, FiniteSets

Corr == {"N1", "N2", "N3"}
Faulty == {"N4"}
M == {"ECHO"}
ByzMsgs == Faulty \X M

(* --algorithm BRB_param {
  variables phase \in [Corr -> {"V0", "V1"}],
            rcvd = [i \in Corr |-> {}],
            sent = {};

  fair process (Node \in Corr) {
  step:
    while (TRUE) {
      with (newMessages \in SUBSET (sent \cup ByzMsgs)) {
        rcvd[self] := rcvd[self] \cup newMessages;
        either {
          await phase[self] = "V1";
          phase[self] := "SE";
          sent := sent \cup {<<self, "ECHO">>};
        } or {
          await phase[self] \in {"V0", "V1"};
          await Cardinality(rcvd[self]) >= 2;
          await Cardinality(rcvd[self]) < 3;
          phase[self] := "SE";
          sent := sent \cup {<<self, "ECHO">>};
        } or {
          await phase[self] \in {"V0", "V1"};
          await Cardinality(rcvd[self]) >= 3;
          phase[self] := "AC";
          sent := sent \cup {<<self, "ECHO">>};
        } or {
          await phase[self] = "SE";
          await Cardinality(rcvd[self]) >= 3;
          phase[self] := "AC";
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
