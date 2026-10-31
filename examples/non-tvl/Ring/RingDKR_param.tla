---------------- MODULE RingDKR_param ----------------
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANT K
Nodes == 1..4
Links == {e \in Nodes \X Nodes: e[1] # e[2]}
Succ(n) == IF n = 4 THEN 1 ELSE n + 1
Pred(n) == IF n = 1 THEN 4 ELSE n - 1

(* --algorithm RingDKR_param {
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

  fair process (Participant \in Nodes)
  variables msg = <<"", 0>>, first = 0;
  {
  initial_send:
    Send(self, Succ(self), <<"One", self>>);
  active:
    while (TRUE) {
      with (m \in {<<"One", n>> : n \in Nodes} \cup {<<"Win", n>> : n \in Nodes}) {
        Receive(self, Pred(self), m);
        msg := m;
      };
    classify:
      if (msg[1] = "Win") {
        if (msg[2] = self) {
        active_me:
          Send(self, Succ(self), msg);
          goto relay;
        } else {
        active_other:
          Send(self, Succ(self), msg);
          goto relay;
        };
      } else {
        if (msg[2] = self) {
        elected_me:
          Send(self, Succ(self), <<"Win", self>>);
          goto relay;
        } else {
          first := msg[2];
        forward_first:
          Send(self, Succ(self), <<"Two", first>>);
        second_receive:
          with (n \in Nodes) {
            Receive(self, Pred(self), <<"Two", n>>);
            msg := <<"Two", n>>;
          };
        compare:
          if (first > self /\ msg[2] < first) {
          new_candidate:
            Send(self, Succ(self), <<"One", first>>);
            if (first = 4) {
            await_maximum:
              Receive(self, Pred(self), <<"One", 4>>);
            announce_maximum:
              Send(self, Succ(self), <<"Win", 4>>);
              goto relay;
            };
          } else {
            goto relay;
          };
        };
      };
    };
  relay:
    while (TRUE) {
      with (m \in {<<k, n>> : k \in {"One", "Two", "Win"}, n \in Nodes}) {
        Receive(self, Pred(self), m);
        msg := m;
      };
    relay_classify:
      if (msg[1] = "Win" /\ msg[2] = self) {
      relay_me:
        Send(self, Succ(self), msg);
        goto Done;
      } else {
      relay_forward:
        Send(self, Succ(self), msg);
        if (msg[1] = "Win") { goto Done; };
      };
    };
  }
} *)
=============================================================================
