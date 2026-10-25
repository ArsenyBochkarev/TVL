event eToken: machine;

machine A {
    start state Waiting {
        entry (parent: machine) {
            send parent, eToken, this;
        }
        on eToken goto Done;
    }

    state Done {
        ignore eToken;
    }
}

machine C {
    start state Waiting {
        entry (parent: machine) {
            send parent, eToken, this;
        }
        on eToken goto Done;
    }

    state Done {
        ignore eToken;
    }
}

machine B {
    var first: machine;
    var received: int;
    start state Waiting {
        on eToken do (sender: machine) {
            received = received + 1;
            if (received == 1) {
                first = sender;
            } else {
                send first, eToken, this;
                send sender, eToken, this;
                goto Done;
            }
        }
    }

    state Done {
        ignore eToken;
    }
}

machine Main {
    start state Init {
        entry {
            var controller: machine;
            controller = new B();
            new A(controller);
            new C(controller);
        }
    }
}
