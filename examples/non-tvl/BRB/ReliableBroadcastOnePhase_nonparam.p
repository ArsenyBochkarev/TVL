event eConfigure: seq[machine];
event eInit;
event eEcho;

machine N1 {
    var peers: seq[machine];
    var received: int;

    start state Init {
        defer eEcho, eInit;
        on eConfigure do (input: seq[machine]) {
            peers = input;

            if (choose()) {
                Broadcast(eInit);
                Broadcast(eEcho);

            }
            goto Waiting;
        }
    }

    state Waiting {
        ignore eInit;
        on eEcho do {
            received = received + 1;
            if (received == 2) {
                goto Done;
            }
        }
    }

    state Done {
        ignore eInit, eEcho;
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

machine N2 {
    var peers: seq[machine];
    var received: int;

    start state Init {
        defer eEcho, eInit;
        on eConfigure do (input: seq[machine]) {
            peers = input;
            if (choose()) {
                goto AwaitInit;
            }
            goto Fallback;
        }
    }

    state AwaitInit {
        defer eEcho;
        on eInit do {
            Broadcast(eEcho);
            goto AwaitEchoes;
        }
    }

    state AwaitEchoes {
        ignore eInit;
        on eEcho do CountEcho;
    }

    state Fallback {
        on eInit do CountFallback;
        on eEcho do CountFallback;
    }

    state Done {
        ignore eInit, eEcho;
    }

    fun Broadcast(kind: event) {
        var peer: machine;
        foreach (peer in peers) {
            if (peer != this) {
                send peer, kind;
            }
        }
    }

    fun CountEcho() {
        received = received + 1;
        if (received == 2) {
            goto Done;
        }
    }

    fun CountFallback() {
        received = received + 1;
        if (received == 2) {
            Broadcast(eEcho);
            goto Done;
        }
    }
}

machine N3 {
    var peers: seq[machine];
    var received: int;

    start state Init {
        defer eEcho, eInit;
        on eConfigure do (input: seq[machine]) {
            peers = input;
            if (choose()) {
                goto AwaitInit;
            }
            goto Fallback;
        }
    }

    state AwaitInit {
        defer eEcho;
        on eInit do {
            Broadcast(eEcho);
            goto AwaitEchoes;
        }
    }

    state AwaitEchoes {
        ignore eInit;
        on eEcho do CountEcho;
    }

    state Fallback {
        on eInit do CountFallback;
        on eEcho do CountFallback;
    }

    state Done {
        ignore eInit, eEcho;
    }

    fun Broadcast(kind: event) {
        var peer: machine;
        foreach (peer in peers) {
            if (peer != this) {
                send peer, kind;
            }
        }
    }

    fun CountEcho() {
        received = received + 1;
        if (received == 2) {
            goto Done;
        }
    }

    fun CountFallback() {
        received = received + 1;
        if (received == 2) {
            Broadcast(eEcho);
            goto Done;
        }
    }
}

machine N4 {
    var peers: seq[machine];
    var received: int;

    start state Init {
        defer eEcho, eInit;
        on eConfigure do (input: seq[machine]) {
            peers = input;
            if (choose()) {
                goto AwaitInit;
            }
            goto Fallback;
        }
    }

    state AwaitInit {
        defer eEcho;
        on eInit do {
            Broadcast(eEcho);
            goto AwaitEchoes;
        }
    }

    state AwaitEchoes {
        ignore eInit;
        on eEcho do CountEcho;
    }

    state Fallback {
        on eInit do CountFallback;
        on eEcho do CountFallback;
    }

    state Done {
        ignore eInit, eEcho;
    }

    fun Broadcast(kind: event) {
        var peer: machine;
        foreach (peer in peers) {
            if (peer != this) {
                send peer, kind;
            }
        }
    }

    fun CountEcho() {
        received = received + 1;
        if (received == 2) {
            goto Done;
        }
    }

    fun CountFallback() {
        received = received + 1;
        if (received == 2) {
            Broadcast(eEcho);
            goto Done;
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
            n1 = new N1();
            n2 = new N2();
            n3 = new N3();
            n4 = new N4();
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
