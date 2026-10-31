---------------- MODULE USB_param ----------------
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANT K
Nodes == {"UDev", "Automount", "FileManager"}
Links == {e \in Nodes \X Nodes: e[1] # e[2]}

(* --algorithm USB_param {
  variables q = [e \in Links |-> <<>>];

  macro Send(who, dst, msg) {
    await Len(q[<<who, dst>>]) < K;
    q[<<who, dst>>] := Append(q[<<who, dst>>], msg);
  }

  macro Receive(who, src, msg) {
    await Len(q[<<src, who>>]) > 0;
    await Head(q[<<src, who>>]) = msg;
    q[<<src, who>>] := Tail(q[<<src, who>>]);
  }

  fair process (UDev \in {"UDev"}) {
  device_loop:
    while (TRUE) {
      either {
      detected:
        Send(self, "Automount", "DeviceAdded");
      } or {
      no_device:
        skip;
      };
    };
  }

  fair process (Automount \in {"Automount"}) {
  mount_loop:
    while (TRUE) {
      Receive(self, "UDev", "DeviceAdded");
    choose_mount:
      either {
      mount_ok:
        skip;
      send_notification:
        Send(self, "FileManager", "DeviceMounted");
      } or {
      ignore:
        skip;
      };
    };
  }

  fair process (FileManager \in {"FileManager"}) {
  notification_loop:
    while (TRUE) {
      Receive(self, "Automount", "DeviceMounted");
    gui_notify:
      skip;
    };
  }
} *)
=============================================================================
