---------------- MODULE AMQP_nonparam ----------------
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANT K
Nodes == {"Client1", "Client2", "Gateway", "Broker", "PayProcessor"}
Links == {e \in Nodes \X Nodes: e[1] # e[2]}

(* --algorithm AMQP_nonparam {
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

  fair process (Client_Client1 = "Client1") {
  request_Client1:
    Send("Client1", "Gateway", <<"", "PayReq">>);
  ack_Client1:
    Receive("Client1", "Broker", <<"", "Ack">>);
  client_loop_Client1:
    while (TRUE) {
      either {
        Receive("Client1", "Broker", <<"", "Processed">>);
      thanks_Client1:
        Send("Client1", "Gateway", <<"", "Thanks">>);
        goto Done;
      } or {
        Receive("Client1", "Broker", <<"", "Lost">>);
      recovery_loss_detected_Client1:
        Send("Client1", "Gateway", <<"", "PayReq">>);
      } or {
        Receive("Client1", "Broker", <<"", "Ack">>);
      repeated_ack_Client1:
        skip;
      };
    };
  }

  fair process (Client_Client2 = "Client2") {
  request_Client2:
    Send("Client2", "Gateway", <<"", "PayReq">>);
  ack_Client2:
    Receive("Client2", "Broker", <<"", "Ack">>);
  client_loop_Client2:
    while (TRUE) {
      either {
        Receive("Client2", "Broker", <<"", "Processed">>);
      thanks_Client2:
        Send("Client2", "Gateway", <<"", "Thanks">>);
        goto Done;
      } or {
        Receive("Client2", "Broker", <<"", "Lost">>);
      recovery_loss_detected_Client2:
        Send("Client2", "Gateway", <<"", "PayReq">>);
      } or {
        Receive("Client2", "Broker", <<"", "Ack">>);
      repeated_ack_Client2:
        skip;
      };
    };
  }

  fair process (Gateway_Gateway = "Gateway")
  variables client_Gateway = "", confirmations_Gateway = 0;
  {
  gateway_request_Gateway:
    with (c \in {"Client1", "Client2"}) {
      Receive("Gateway", c, <<"", "PayReq">>);
      client_Gateway := c;
    };
  forward_request_Gateway:
    Send("Gateway", "Broker", <<client_Gateway, "PayReq">>);
  confirmations_loop_Gateway:
    while (confirmations_Gateway < 2) {
      with (c \in {"Client1", "Client2"}) {
        Receive("Gateway", c, <<"", "Thanks">>);
      };
      confirmations_Gateway := confirmations_Gateway + 1;
    };
  gateway_thanks_Gateway:
    Send("Gateway", "Broker", <<"", "Thanks">>);
  }

  fair process (Broker_Broker = "Broker")
  variables client_Broker = "", kind_Broker = "";
  {
  broker_request_Broker:
    with (c \in {"Client1", "Client2"}) {
      Receive("Broker", "Gateway", <<c, "PayReq">>);
      client_Broker := c;
    };
  processor_request_Broker:
    Send("Broker", "PayProcessor", <<client_Broker, "PayReq">>);
  broker_ack_Broker:
    Send("Broker", client_Broker, <<"", "Ack">>);
  broker_loop_Broker:
    while (TRUE) {
      either {
        with (c \in {"Client1", "Client2"}, k \in {"Lost", "Processed", "TryAgain"}) {
          Receive("Broker", "PayProcessor", <<c, k>>);
          client_Broker := c;
          kind_Broker := k;
        };
      broker_reply_Broker:
        if (kind_Broker = "TryAgain") {
          Send("Broker", "PayProcessor", <<client_Broker, "PayReq">>);
        } else {
          Send("Broker", client_Broker, <<"", kind_Broker>>);
        };
      } or {
        Receive("Broker", "Gateway", <<"", "Thanks">>);
      processor_thanks_Broker:
        Send("Broker", "PayProcessor", <<"", "Thanks">>);
      };
    };
  }

  fair process (PayProcessor_PayProcessor = "PayProcessor")
  variables client_PayProcessor = "";
  {
  processor_loop_PayProcessor:
    while (TRUE) {
      either {
        with (c \in {"Client1", "Client2"}) {
          Receive("PayProcessor", "Broker", <<c, "PayReq">>);
          client_PayProcessor := c;
        };
      processor_choose_PayProcessor:
        either {
        expired_msg_recovery_PayProcessor:
          Send("PayProcessor", "Broker", <<client_PayProcessor, "Lost">>);
        } or {
        retry_PayProcessor:
          Send("PayProcessor", "Broker", <<client_PayProcessor, "TryAgain">>);
        } or {
        processed_PayProcessor:
          Send("PayProcessor", "Broker", <<client_PayProcessor, "Processed">>);
        };
      } or {
        Receive("PayProcessor", "Broker", <<"", "Thanks">>);
        goto Done;
      };
    };
  }

} *)
=============================================================================
