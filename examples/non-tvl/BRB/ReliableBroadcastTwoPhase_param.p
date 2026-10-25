event eConfigure: seq[machine];
event eInit;
event eEcho;
event eVote;

machine Initiator {
    var peers: seq[machine];
    var received: int;

    start state Init {
        defer eEcho, eInit, eVote;
        on eConfigure do (input: seq[machine]) {
            peers = input;
            Broadcast(eInit);
            if (choose()) {
                goto AwaitEchoes;
            }
            goto Fallback;
        }
    }

    state AwaitEchoes {
        ignore eInit;
        defer eVote;
        on eEcho do {
            received = received + 1;
            if (received == 2) {
                Broadcast(eVote);
                received = 0;
                goto AwaitVotes;
            }
        }
    }

    state AwaitVotes {
        ignore eInit, eEcho;
        on eVote do {
            received = received + 1;
            if (received == 2) {
                goto Done;
            }
        }
    }

    state Fallback {
        ignore eInit, eEcho;
        on eVote do {

            if (received == 1) {
                Broadcast(eVote);
            } else {
                received = 1;
            }
        }
    }

    state Done {
        ignore eInit, eEcho, eVote;
    }

    fun Broadcast(kind: event) {
        var peer: machine;
        foreach (peer in peers) {
            if (peer != this) {
                send peer, kind;
            }
        }
    }
}

machine Receiver {
    var peers: seq[machine];
    var received: int;

    start state Init {
        defer eEcho, eInit, eVote;
        on eConfigure do (input: seq[machine]) {
            peers = input;
            if (choose()) {
                goto AwaitInit;
            }
            goto Fallback;
        }
    }

    state AwaitInit {
        defer eEcho, eVote;
        on eInit do {
            Broadcast(eEcho);
            goto AwaitEchoes;
        }
    }
    state AwaitEchoes {
        ignore eInit;
        defer eVote;
        on eEcho do {
            received = received + 1;
            if (received == 2) {
                Broadcast(eVote);
                received = 0;
                goto AwaitVotes;
            }
        }
    }

    state AwaitVotes {
        ignore eInit, eEcho;
        on eVote do {
            received = received + 1;
            if (received == 2) {
                goto Done;
            }
        }
    }

    state Fallback {
        ignore eInit, eEcho;
        on eVote do {
            received = received + 1;
            if (received == 2) {
                Broadcast(eVote);
                goto Done;
            }
        }
    }

    state Done {
        ignore eInit, eEcho, eVote;
    }

    fun Broadcast(kind: event) {
        var peer: machine;
        foreach (peer in peers) {
            if (peer != this) {
                send peer, kind;
            }
        }
    }
}

machine Main {
    var peers: seq[machine];
    var n1: machine;
    var n2: machine;
    var n3: machine;
    var n4: machine;

    start state Init {
        entry {
            n1 = new Initiator();
            n2 = new Receiver();
            n3 = new Receiver();
            n4 = new Receiver();
            peers = default(seq[machine]);
            peers += (sizeof(peers), n1);
            peers += (sizeof(peers), n2);
            peers += (sizeof(peers), n3);
            peers += (sizeof(peers), n4);
            send n1, eConfigure, peers;
            send n2, eConfigure, peers;
            send n3, eConfigure, peers;
            send n4, eConfigure, peers;
        }
    }
}
