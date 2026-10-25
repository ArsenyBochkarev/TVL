event eTick;
event eDeviceAdded;
event eDeviceMounted;

machine UDev {
    var automount: machine;

    start state Running {
        entry (target: machine) {
            automount = target;
            send this, eTick;
        }
        on eTick do {
            if (choose()) {
                send automount, eDeviceAdded;
            }
            send this, eTick;
        }
    }
}

machine Automount {
    var fileManager: machine;

    start state Running {
        entry (target: machine) {
            fileManager = target;
        }
        on eDeviceAdded do {
            if (choose()) {
                send fileManager, eDeviceMounted;
            }
        }
    }
}

machine FileManager {
    start state Running {
        on eDeviceMounted do {
        }
    }
}

machine Main {
    start state Init {
        entry {
            var manager: machine;
            var automount: machine;
            manager = new FileManager();
            automount = new Automount(manager);
            new UDev(automount);
        }
    }
}
