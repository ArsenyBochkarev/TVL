event eConfigure: machine;

event eOne: int;
event eTwo: int;
event eWin: int;

machine R1 {
    var next: machine;
    var first: int;

    start state Init {
        defer eOne, eTwo, eWin;
        on eConfigure do (input: machine) {
            next = input;
            send next, eOne, 1;
            goto Active;
        }
    }

    state Active {
        defer eTwo;
        on eOne do (candidate: int) {
            if (candidate == 1) {
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
            if (first > 1 && second < first) {
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

machine R2 {
    var next: machine;
    var first: int;

    start state Init {
        defer eOne, eTwo, eWin;
        on eConfigure do (input: machine) {
            next = input;
            send next, eOne, 2;
            goto Active;
        }
    }

    state Active {
        defer eTwo;
        on eOne do (candidate: int) {
            if (candidate == 2) {
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
            if (first > 2 && second < first) {
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

machine R3 {
    var next: machine;
    var first: int;

    start state Init {
        defer eOne, eTwo, eWin;
        on eConfigure do (input: machine) {
            next = input;
            send next, eOne, 3;
            goto Active;
        }
    }

    state Active {
        defer eTwo;
        on eOne do (candidate: int) {
            if (candidate == 3) {
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
            if (first > 3 && second < first) {
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

machine R4 {
    var next: machine;
    var first: int;

    start state Init {
        defer eOne, eTwo, eWin;
        on eConfigure do (input: machine) {
            next = input;
            send next, eOne, 4;
            goto Active;
        }
    }

    state Active {
        defer eTwo;
        on eOne do (candidate: int) {
            if (candidate == 4) {
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
            if (first > 4 && second < first) {
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
