type tConfig = (next: machine, rank: int);
event eConfigure: tConfig;

event eOne: int;
event eTwo: int;
event eWin: int;

machine Participant {
    var next: machine;
    var first: int;
    var rank: int;

    start state Init {
        defer eOne, eTwo, eWin;
        on eConfigure do (input: tConfig) {
            next = input.next;
            rank = input.rank;
            send next, eOne, rank;
            goto Active;
        }
    }

    state Active {
        defer eTwo;
        on eOne do (candidate: int) {
            if (candidate == rank) {
                send next, eWin, candidate;
                goto Relay;
            }
            first = candidate;
            send next, eTwo, first;
            goto AwaitSecond;
        }
        on eWin do (winner: int) {
            send next, eWin, winner;
            goto Relay;
        }
    }

    state AwaitSecond {
        defer eOne, eWin;
        on eTwo do (second: int) {
            if (first > rank && second < first) {
                send next, eOne, first;
                if (first == 4) {
                    goto AwaitMaximum;
                }
                goto Active;
            }
            goto Relay;
        }
    }

    state AwaitMaximum {
        defer eTwo, eWin;
        on eOne do (candidate: int) {
            if (candidate == 4) {
                send next, eWin, 4;
                goto Relay;
            }
        }
    }

    state Relay {
        on eOne do (candidate: int) {
            send next, eOne, candidate;
        }
        on eTwo do (candidate: int) {
            send next, eTwo, candidate;
        }
        on eWin do (winner: int) {
            send next, eWin, winner;
            goto Done;
        }
    }

    state Done {
        ignore eOne, eTwo, eWin;
    }
}

machine Main {
    var r1: machine;
    var r2: machine;
    var r3: machine;
    var r4: machine;

    start state Init {
        entry {
            r1 = new Participant();
            r2 = new Participant();
            r3 = new Participant();
            r4 = new Participant();
            send r1, eConfigure, (next = r2, rank = 1);
            send r2, eConfigure, (next = r3, rank = 2);
            send r3, eConfigure, (next = r4, rank = 3);
            send r4, eConfigure, (next = r1, rank = 4);
        }
    }
}
