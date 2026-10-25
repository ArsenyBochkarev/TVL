event eConfigure: seq[machine];
event eToken: machine;

machine A {
    var remaining: seq[machine];
    var pending: set[machine];

    var awaited: machine;

    start state Init {
        defer eToken;
        on eConfigure do (input: seq[machine]) {
            remaining = input;
            goto Selecting;
        }
    }

    state AwaitReturn {
        on eToken do (sender: machine) {
            pending += (sender);
            if (awaited in pending) {
                pending -= (awaited);
                goto Selecting;
            }
        }
    }

    state Selecting {
        entry {
            var chosen: int;
            if (sizeof(remaining) == 0) {
                goto Done;
            }
            chosen = choose(sizeof(remaining));
            awaited = remaining[chosen];
            remaining -= chosen;
            send awaited, eToken, this;
            if (awaited in pending) {
                pending -= (awaited);
                goto Selecting;
            }
            goto AwaitReturn;
        }
    }

    state Done {
        ignore eToken;
    }
}

machine B {
    var first: machine;
    var second: machine;
    var parent: machine;
    var awaited: machine;

    start state Init {
        defer eToken;
        on eConfigure do (input: seq[machine]) {
            first = input[0];
            second = input[1];
            goto AwaitParent;
        }
    }

    state AwaitParent {
        on eToken do (sender: machine) {
            parent = sender;
            if (parent == first) {
                awaited = second;
            } else {
                awaited = first;
            }
            send awaited, eToken, this;
            goto AwaitReturn;
        }
    }

    state AwaitReturn {
        on eToken do (sender: machine) {
            if (sender == awaited) {
                send parent, eToken, this;
                goto Done;
            }
        }
    }

    state Done {
        ignore eToken;
    }
}

machine C {
    var remaining: seq[machine];
    var pending: set[machine];
    var parent: machine;
    var awaited: machine;

    start state Init {
        defer eToken;
        on eConfigure do (input: seq[machine]) {
            remaining = input;
            goto AwaitParent;
        }
    }

    state AwaitParent {
        on eToken do (sender: machine) {
            var i: int;
            parent = sender;
            while (i < sizeof(remaining)) {
                if (remaining[i] == parent) {
                    remaining -= i;
                    break;
                }
                i = i + 1;
            }
            goto Selecting;
        }
    }

    state AwaitReturn {
        on eToken do (sender: machine) {
            pending += (sender);
            if (awaited in pending) {
                pending -= (awaited);
                goto Selecting;
            }
        }
    }

    state Selecting {
        entry {
            var chosen: int;
            if (sizeof(remaining) == 0) {
                send parent, eToken, this;
                goto Done;
            }
            chosen = choose(sizeof(remaining));
            awaited = remaining[chosen];
            remaining -= chosen;
            send awaited, eToken, this;
            if (awaited in pending) {
                pending -= (awaited);
                goto Selecting;
            }
            goto AwaitReturn;
        }
    }

    state Done {
        ignore eToken;
    }
}

machine D {
    var first: machine;
    var second: machine;
    var parent: machine;
    var awaited: machine;

    start state Init {
        defer eToken;
        on eConfigure do (input: seq[machine]) {
            first = input[0];
            second = input[1];
            goto AwaitParent;
        }
    }

    state AwaitParent {
        on eToken do (sender: machine) {
            parent = sender;
            if (parent == first) {
                awaited = second;
            } else {
                awaited = first;
            }
            send awaited, eToken, this;
            goto AwaitReturn;
        }
    }

    state AwaitReturn {
        on eToken do (sender: machine) {
            if (sender == awaited) {
                send parent, eToken, this;
                goto Done;
            }
        }
    }

    state Done {
        ignore eToken;
    }
}

machine Main {
    var neighbors: seq[machine];
    var a: machine;
    var b: machine;
    var c: machine;
    var d: machine;

    start state Init {
        entry {
            a = new A();
            b = new B();
            c = new C();
            d = new D();
            neighbors = default(seq[machine]);
            neighbors += (sizeof(neighbors), b);
            neighbors += (sizeof(neighbors), c);
            neighbors += (sizeof(neighbors), d);
            send a, eConfigure, neighbors;
            neighbors = default(seq[machine]);
            neighbors += (sizeof(neighbors), a);
            neighbors += (sizeof(neighbors), c);
            send b, eConfigure, neighbors;
            neighbors = default(seq[machine]);
            neighbors += (sizeof(neighbors), a);
            neighbors += (sizeof(neighbors), b);
            neighbors += (sizeof(neighbors), d);
            send c, eConfigure, neighbors;
            neighbors = default(seq[machine]);
            neighbors += (sizeof(neighbors), a);
            neighbors += (sizeof(neighbors), c);
            send d, eConfigure, neighbors;
        }
    }
}
