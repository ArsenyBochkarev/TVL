event eConfigure: machine;

event eClaim: int;
event eStop;

machine R1 {
    var next: machine;
    var active: bool;

    start state Init {
        defer eClaim, eStop;
        on eConfigure do (input: machine) {
            next = input;
            active = choose();
            if (active) {
                send next, eClaim, 1;
            }
            goto Running;
        }
    }

    state Running {
        on eClaim do (candidate: int) {

            if (candidate == 1) {
                send next, eStop;
                goto AwaitStop;
            }
            if (!active || candidate > 1) {
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

machine R2 {
    var next: machine;
    var active: bool;

    start state Init {
        defer eClaim, eStop;
        on eConfigure do (input: machine) {
            next = input;
            active = choose();
            if (active) {
                send next, eClaim, 2;
            }
            goto Running;
        }
    }

    state Running {
        on eClaim do (candidate: int) {
            if (candidate == 4) {
                return;
            }
            if (candidate == 2) {
                send next, eStop;
                goto AwaitStop;
            }
            if (!active || candidate > 2) {
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

machine R3 {
    var next: machine;
    var active: bool;

    start state Init {
        defer eClaim, eStop;
        on eConfigure do (input: machine) {
            next = input;
            active = choose();
            if (active) {
                send next, eClaim, 3;
            }
            goto Running;
        }
    }

    state Running {
        on eClaim do (candidate: int) {

            if (candidate == 3) {
                send next, eStop;
                goto AwaitStop;
            }
            if (!active || candidate > 3) {
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

machine R4 {
    var next: machine;
    var active: bool;

    start state Init {
        defer eClaim, eStop;
        on eConfigure do (input: machine) {
            next = input;
            active = choose();
            if (active) {
                send next, eClaim, 4;
            }
            goto Running;
        }
    }

    state Running {
        on eClaim do (candidate: int) {

            if (candidate == 4) {
                send next, eStop;
                goto AwaitStop;
            }
            if (!active || candidate > 4) {
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
            r1 = new R1();
            r2 = new R2();
            r3 = new R3();
            r4 = new R4();
            send r1, eConfigure, r2;
            send r2, eConfigure, r3;
            send r3, eConfigure, r4;
            send r4, eConfigure, r1;
        }
    }
}
