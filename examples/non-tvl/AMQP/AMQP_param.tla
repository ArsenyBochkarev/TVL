---------------- MODULE AMQP_param ----------------
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANT K
Nodes == {"Client1", "Client2", "Gateway", "Broker", "PayProcessor"}
Links == {e \in Nodes \X Nodes: e[1] # e[2]}

(* --algorithm AMQP_param {
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

  fair process (Client \in {"Client1", "Client2"}) {
  request:
    Send(self, "Gateway", <<"", "PayReq">>);
  ack:
    Receive(self, "Broker", <<"", "Ack">>);
  client_loop:
    while (TRUE) {
      either {
        Receive(self, "Broker", <<"", "Processed">>);
      thanks:
        Send(self, "Gateway", <<"", "Thanks">>);
        goto Done;
      } or {
        Receive(self, "Broker", <<"", "Lost">>);
      recovery_loss_detected:
        Send(self, "Gateway", <<"", "PayReq">>);
      } or {
        Receive(self, "Broker", <<"", "Ack">>);
      repeated_ack:
        skip;
      };
    };
  }

  fair process (Gateway \in {"Gateway"})
  variables client = "", confirmations = 0;
  {
  gateway_request:
    with (c \in {"Client1", "Client2"}) {
      Receive(self, c, <<"", "PayReq">>);
      client := c;
    };
  forward_request:
    Send(self, "Broker", <<client, "PayReq">>);
  confirmations_loop:
    while (confirmations < 2) {
      with (c \in {"Client1", "Client2"}) {
        Receive(self, c, <<"", "Thanks">>);
      };
      confirmations := confirmations + 1;
    };
  gateway_thanks:
    Send(self, "Broker", <<"", "Thanks">>);
  }

  fair process (Broker \in {"Broker"})
  variables client = "", kind = "";
  {
  broker_request:
    with (c \in {"Client1", "Client2"}) {
      Receive(self, "Gateway", <<c, "PayReq">>);
      client := c;
    };
  processor_request:
    Send(self, "PayProcessor", <<client, "PayReq">>);
  broker_ack:
    Send(self, client, <<"", "Ack">>);
  broker_loop:
    while (TRUE) {
      either {
        with (c \in {"Client1", "Client2"}, k \in {"Lost", "Processed", "TryAgain"}) {
          Receive(self, "PayProcessor", <<c, k>>);
          client := c;
          kind := k;
        };
      broker_reply:
        if (kind = "TryAgain") {
          Send(self, "PayProcessor", <<client, "PayReq">>);
        } else {
          Send(self, client, <<"", kind>>);
        };
      } or {
        Receive(self, "Gateway", <<"", "Thanks">>);
      processor_thanks:
        Send(self, "PayProcessor", <<"", "Thanks">>);
      };
    };
  }

  fair process (PayProcessor \in {"PayProcessor"})
  variables client = "";
  {
  processor_loop:
    while (TRUE) {
      either {
        with (c \in {"Client1", "Client2"}) {
          Receive(self, "Broker", <<c, "PayReq">>);
          client := c;
        };
      processor_choose:
        either {
        expired_msg_recovery:
          Send(self, "Broker", <<client, "Lost">>);
        } or {
        retry:
          Send(self, "Broker", <<client, "TryAgain">>);
        } or {
        processed:
          Send(self, "Broker", <<client, "Processed">>);
        };
      } or {
        Receive(self, "Broker", <<"", "Thanks">>);
        goto Done;
      };
    };
  }
} *)
=============================================================================
