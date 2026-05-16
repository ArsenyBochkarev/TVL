--------------------------- MODULE bcastByzFixed ---------------------------
EXTENDS Naturals, FiniteSets

Corr == {"N1", "N2", "N3"}
Faulty == {"N4"}
Proc == Corr \cup Faulty
M == {"ECHO"}

VARIABLES pc, rcvd, sent
vars == << pc, rcvd, sent >>

Init == 
  /\ sent = {}
  /\ pc \in [ Corr -> {"V0", "V1"} ] 
  /\ rcvd = [ i \in Corr |-> {} ]

ByzMsgs == Faulty \X M

Receive(self) ==
  \E newMessages \in SUBSET ( sent \cup ByzMsgs ) :
    rcvd' = [ i \in Corr |-> IF i = self THEN rcvd[self] \cup newMessages ELSE rcvd[i] ]

UponV1(self) ==
  /\ pc[self] = "V1"
  /\ pc' = [pc EXCEPT ![self] = "SE"]
  /\ sent' = sent \cup { <<self, "ECHO">> }

UponNonFaulty(self) ==
  /\ pc[self] \in { "V0", "V1" }
  /\ Cardinality(rcvd'[self]) >= 2
  /\ Cardinality(rcvd'[self]) < 3
  /\ pc' = [ pc EXCEPT ![self] = "SE" ]
  /\ sent' = sent \cup { <<self, "ECHO">> }

UponAcceptNotSentBefore(self) ==
  /\ pc[self] \in { "V0", "V1" }
  /\ Cardinality(rcvd'[self]) >= 3
  /\ pc' = [ pc EXCEPT ![self] = "AC" ]
  /\ sent' = sent \cup { <<self, "ECHO">> }

UponAcceptSentBefore(self) ==
  /\ pc[self] = "SE"
  /\ Cardinality(rcvd'[self]) >= 3
  /\ pc' = [pc EXCEPT ![self] = "AC"]
  /\ sent' = sent

Step(self) == 
  /\ Receive(self)
  /\ \/ UponV1(self)
     \/ UponNonFaulty(self)
     \/ UponAcceptNotSentBefore(self)
     \/ UponAcceptSentBefore(self)

Next == 
  \/ Step("N1")
  \/ Step("N2")
  \/ Step("N3")
  \/ UNCHANGED vars

Spec == Init /\ [][Next]_vars
             /\ WF_vars(Step("N1"))
             /\ WF_vars(Step("N2"))
             /\ WF_vars(Step("N3"))

RelayLtl == []((\E i \in Corr: pc[i] = "AC") => <>(\A i \in Corr: pc[i] = "AC"))
CorrLtl == (\A i \in Corr: pc[i] = "V1") => <>(\A i \in Corr: pc[i] = "AC")
InitNoBcast == pc = [ i \in Corr |-> "V0" ] /\ sent = {} /\ rcvd = [ i \in Corr |-> {} ]
SpecNoBcast == InitNoBcast /\ [][Next]_vars
Unforg == (\A i \in Corr: pc[i] /= "AC")
