### TVL label-based specifications
TVL has some pre-defined which require user help to be tested. If a label with reserved name was faced in the protocol, it will be transformed into some property.
Usually it's a pair of labels, e.g.
```
...
choose {
    fail_crash: skip
    start_recovery: send Ping to Supervisor
} or {
    ...
}
...
```

Here, labels with reserved names are `fail_crash` and `start_recovery` (actually, only first part of their names are reserved, see below).
TVL translator will internally generate some properties based on these labels.

##### Recovery property
Labels names: **`fail_*`** and **`start_*`**. Exact name to use in `specs` block: **`RecoveryProperty`**.

Recovering from some error. It can be used to check jump to the "otherwise" branch if acknowledge message has not arrived.
Mostly relevant for infinite protocols.

Temporal formula: `G(fail_i -> F start_i)`.

##### Loss detection property
Labels names: **`expired_msg_*`** and **`*_loss_detected`**. Exact name to use in `specs` block: **`LossDetectionProperty`**.

Check if protocol supports detection of message loss. Message name will be remembered and used for formula generation.
It may seem similar to `RecoveryProperty`, but labels in this property should be on a different actors. 
Mostly relevant for infinite protocols.

Temporal formula: `G(expired_msg_MSG -> F MSG_loss_detected)`.
