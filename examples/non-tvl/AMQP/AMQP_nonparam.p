event eConfigure: machine;
event ePay: machine;
event eAck;
event eProcessed;
event eLost;
event eClientLost: machine;
event eClientProcessed: machine;
event eTryAgain: machine;
event eThanks;

machine Client1 {
    var gateway: machine;

    start state Init {
        defer eAck, eLost, eProcessed;
        on eConfigure do (input: machine) {
            gateway = input;
            send gateway, ePay, this;
            goto AwaitAck;
        }
    }

    state AwaitAck {
        defer eLost, eProcessed;
        on eAck goto AwaitResult;
    }

    state AwaitResult {
        ignore eAck;
        on eLost do {
            send gateway, ePay, this;
        }
        on eProcessed do {
            send gateway, eThanks;
            goto Done;
        }
    }

    state Done {
        ignore eAck, eLost, eProcessed;
    }
}

machine Client2 {
    var gateway: machine;

    start state Init {
        defer eAck, eLost, eProcessed;
        on eConfigure do (input: machine) {
            gateway = input;
            send gateway, ePay, this;
            goto AwaitAck;
        }
    }

    state AwaitAck {
        defer eLost, eProcessed;
        on eAck goto AwaitResult;
    }

    state AwaitResult {
        ignore eAck;
        on eLost do {
            send gateway, ePay, this;
        }
        on eProcessed do {
            send gateway, eThanks;
            goto Done;
        }
    }

    state Done {
        ignore eAck, eLost, eProcessed;
    }
}

machine Gateway {
    var broker: machine;
    var thanks: int;

    start state Init {
        defer ePay, eThanks;
        on eConfigure do (input: machine) {
            broker = input;
            goto AwaitRequest;
        }
    }

    state AwaitRequest {
        defer eThanks;
        on ePay do (client: machine) {
            send broker, ePay, client;
            goto AwaitThanks;
        }
    }

    state AwaitThanks {
        defer ePay;
        on eThanks do {
            thanks = thanks + 1;
            if (thanks == 2) {
                send broker, eThanks;
                goto Done;
            }
        }
    }

    state Done {
        ignore ePay, eThanks;
    }
}

machine Broker {
    var processor: machine;

    start state Init {
        defer eClientLost, eClientProcessed, ePay, eThanks, eTryAgain;
        on eConfigure do (input: machine) {
            processor = input;
            goto AwaitRequest;
        }
    }

    state AwaitRequest {
        defer eClientLost, eClientProcessed, eTryAgain, eThanks;
        on ePay do (client: machine) {
            send processor, ePay, client;
            send client, eAck;
            goto Running;
        }
    }

    state Running {
        defer ePay;
        on eClientLost do (client: machine) {
            send client, eLost;
        }
        on eClientProcessed do (client: machine) {
            send client, eProcessed;
        }
        on eTryAgain do (client: machine) {
            send processor, ePay, client;
        }
        on eThanks do {
            send processor, eThanks;
        }
    }
}

machine PayProcessor {
    var broker: machine;

    start state Init {
        defer ePay, eThanks;
        on eConfigure do (input: machine) {
            broker = input;
            goto Running;
        }
    }

    state Running {
        on ePay do (client: machine) {
            var outcome: int;
            outcome = choose(3);
            if (outcome == 0) {
                send broker, eClientLost, client;
            } else if (outcome == 1) {
                send broker, eTryAgain, client;
            } else {
                send broker, eClientProcessed, client;
            }
        }
        on eThanks goto Done;
    }

    state Done {
        ignore ePay, eThanks;
    }
}

machine Main {
    var c1: machine;
    var c2: machine;
    var gateway: machine;
    var broker: machine;
    var processor: machine;

    start state Init {
        entry {
            c1 = new Client1();
            c2 = new Client2();
            gateway = new Gateway();
            broker = new Broker();
            processor = new PayProcessor();
            send c1, eConfigure, gateway;
            send c2, eConfigure, gateway;
            send gateway, eConfigure, broker;
            send broker, eConfigure, processor;
            send processor, eConfigure, broker;
        }
    }
}
