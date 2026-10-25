type tConfig = (next: machine, rank: int);
event eConfigure: tConfig;

event eClaim: int;
event eStop;

machine Participant {
    var next: machine;
    var active: bool;
    var rank: int;

    start state Init {
        defer eClaim, eStop;
        on eConfigure do (input: tConfig) {
            next = input.next;
            rank = input.rank;
            active = choose();
            if (active) {
                send next, eClaim, rank;
            }
            goto Running;
        }
    }

    state Running {
        on eClaim do (candidate: int) {

            if (candidate == rank) {
                send next, eStop;
                goto AwaitStop;
            }
            if (!active || candidate > rank) {
                send next, eClaim, candidate;
            }
        }
        on eStop do {
            send next, eStop;
            goto Done;
        }
    }

    state AwaitStop {
        defer eClaim;
        on eStop goto Done;
    }

    state Done {
        ignore eClaim, eStop;
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
