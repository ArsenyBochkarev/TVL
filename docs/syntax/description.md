# TVL Language Description

## Program Structure
### `module`
Every TVL program begins with a module declaration.
```
module MyProtocol
```
Implementing module system is a TODO. Currently it is not used.

### `import`
Used to import definitions from other modules.
```
import OtherModule
import OtherModule.SomeActor
```
Implementing module system is a TODO. Currently it is not used.

### `actor`
An independent entity representing a single process or node in a distributed system. An actor can optionally declare which other actors it interacts with.
```
actor Sender interacts with Receiver {
    // actor behavior
}
```
Checking correctness of actors usage is a TODO. Currently it is not used.

### `specs`
A dedicated block to define verification properties. Supports user-defined `ltl` and `ctl` formulas, as well as template properties (e.g., `FinishingProperty`).
```
specs {
    ltl Progress: "[] (Sender.start -> <> Receiver.done)";
    FinishingProperty;
}
```
For a detailed description of specifications see [specifications](../specifications/).

## Statements
### Labels
Any statement can be preceded by a label, which is used in verification properties to check the actor's program counter.
```
start_label: send Msg to Receiver;
```
Some labels' names can be used to construct a template-based specifications, see [label-based.md](../specifications/label-based.md) document.

### `send`
Asynchronously sends a message (which acts as a simple token without payload) to the target actor's message queue. You can optionally specify the number of times to send the message sequentially.
```
send Msg to Receiver;
send 3 Msg to Receiver; // Sends 'Msg' 3 times sequentially
```

### `receive`
Blocks the actor's execution until the specified message arrives at the head of the queue from the specified actor. Optionally supports repeating the receive operation `N` times sequentially.
```
receive Msg from Sender;
receive 2 Msg from Sender; // Waits to receive 'Msg' twice sequentially
```

### `receive alts`
A branching construct that waits for one of several possible messages. The first matching message at the head of a queue triggers its corresponding block. If no messages match and the `otherwise` block is provided, it is executed instead. If `otherwise` is omitted, the actor blocks until one of the specified messages arrives.
```
receive alts {
    MsgA from Sender1 => {
        // Handle MsgA
    }
    MsgB from Sender2 => {
        // Handle MsgB
    }
    otherwise => {
        // Fallback action
    }
}
```

### `choose`
Nondeterministically selects one of the provided execution branches.
```
choose {
    send Success to Node;
} or {
    send Failure to Node;
}
```

### `repeat`
Defines a loop. It can be an countable or uncountable.
```
// Infinite loop
repeat {
    send Ping to Node;
}

// Countable loop
repeat 5 {
    send Ping to Node;
}
```

### `parallel`
Executes multiple branches concurrently using interleaving semantics. The actor will nondeterministically interleave steps from all branches until they all complete. Nested `parallel` blocks are forbidden.
```
parallel {
    send MsgA to Node1;
    receive AckA from Node1;
} and {
    send MsgB to Node2;
    receive AckB from Node2;
}
```

### `break`
Exits the current innermost `repeat` loop or `parallel` block.
```
repeat {
    receive alts {
        StopMsg from Controller => { break; }
        otherwise => { skip; }
    }
}
```

If `parallel` block is exited, all branches stop, and execution is transferred to the successor of the blocks.

### `skip`
A no-op instruction. It does nothing and simply passes control to the next instruction.
```
skip;
```