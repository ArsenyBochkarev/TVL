# TVL
Telecom protocols Verification Language

TVL is designed as an instruction-based control flow language where a distributed system is divided into independent procedures for each actor. The language semantics is based on Communicating Automata with bounded channels. Programs are compiled into an Intermediate Representation, a flat control flow graph where complex constructs like loops and branching are replaced with direct transitions (Jump, Branch, Choice).

The state of the system is primarily characterized by message queues between actors, with messages acting as atomic tokens. Operations like sending (`send`) and receiving (`receive`) mutate the global state of these queues, while ensuring properties such as `MAX_QUEUE_SIZE` invariants are met.

TVL also provides means for property verification through specifications block (`specs`). It supports custom `ltl` and `ctl` formulas using labels, as well as template-based properties (like `FinishingProperty` or `MsgDeliveredProperty`) and implicitly generated label-based specifications.

For more deep and formal theoretical background on how execution and configurations are defined, check out the documentation on semantics in `docs/semantics`:
- [Operational semantics](docs/semantics/operational_en.md)
- [Kripke structures](docs/semantics/kripke_en.md)

### Prerequisites
- Java
- ANTLRv4
- SBT
- Target model checker
  - For TLA+, make sure you have `pcal` and `tlc` set, e.g.
    - ~/.local/bin/tlc:
      ```
      #!/bin/bash
      java -cp /path/to/tla2tools.jar tlc2.TLC "$@"
      ```
    - ~/.local/bin/pcal:
      ```
      #!/bin/bash
      java -cp /path/to/tla2tools.jar pcal.trans "$@"
      ```

### Building from scratch for the first time
```shell
java -jar <ANTLR .jar> -visitor -no-listener -Dlanguage=Java ./src/main/scala/Grammar/TVL.g4
sbt compile
```

### Usage
```
translate <input file> <target>
```
Altough it is highly recommended to use [VS Code plugin](https://github.com/ArsenyBochkarev/tvl-vscode).

### Supported targets
- TLA+ (initial translation made to PlusCal)
- SPIN

### Run tests
```
sbt test
```
