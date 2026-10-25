type tConfig = (neighbors: seq[machine], initiator: bool);
event eConfigure: tConfig;
event eToken: machine;

machine Participant {
    var remaining: seq[machine];
    var pending: set[machine];
    var parent: machine;
    var awaited: machine;
    var initiator: bool;

    start state Init {
        defer eToken;
        on eConfigure do (input: tConfig) {
            remaining = input.neighbors;
            initiator = input.initiator;
            if (initiator) {
                goto Selecting;
            }
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
                if (!initiator) {
                    send parent, eToken, this;
                }
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

machine Main {
    var neighbors: seq[machine];
    var a: machine;
    var b: machine;
    var c: machine;
    var d: machine;

    start state Init {
        entry {
            a = new Participant();
            b = new Participant();
            c = new Participant();
            d = new Participant();
            neighbors = default(seq[machine]);
            neighbors += (sizeof(neighbors), b);
            neighbors += (sizeof(neighbors), c);
            neighbors += (sizeof(neighbors), d);
            send a, eConfigure, (neighbors = neighbors, initiator = true);
            neighbors = default(seq[machine]);
            neighbors += (sizeof(neighbors), a);
            neighbors += (sizeof(neighbors), c);
            send b, eConfigure, (neighbors = neighbors, initiator = false);
            neighbors = default(seq[machine]);
            neighbors += (sizeof(neighbors), a);
            neighbors += (sizeof(neighbors), b);
            neighbors += (sizeof(neighbors), d);
            send c, eConfigure, (neighbors = neighbors, initiator = false);
            neighbors = default(seq[machine]);
            neighbors += (sizeof(neighbors), a);
            neighbors += (sizeof(neighbors), c);
            send d, eConfigure, (neighbors = neighbors, initiator = false);
        }
    }
}
