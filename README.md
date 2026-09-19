# TVL
Telecom protocols Verification Language

TVL is a small DSL designed to create models of communication protocols. Each actor of this protocol is described in an independent procedure. The language semantics is based on Communicating Automata with bounded channels. Programs are compiled into TVL IR, an intermediate representation that is a flat control flow graph where complex constructs like loops and branching are replaced with direct transitions (Jump, Branch, Choice).

The state of the system is primarily characterized by message queues between actors, with messages acting as atomic tokens. Operations like sending (`send`) and receiving (`receive`) mutate the global state of these queues, while ensuring properties such as `MAX_QUEUE_SIZE` invariants are met.

TVL also provides means for property verification through specifications block (`specs`). It supports custom `ltl` and `ctl` formulas using labels, as well as template-based properties (like `FinishingProperty` or `MsgDeliveredProperty`) and implicitly generated label-based specifications.

The generated TVL IR is translated into a target model checker language (like TLA+ or Spin) to perform the actual verification. Also, there is an ongoing work to build a native TVL-specific model checker called [Curtis](https://github.com/ArsenyBochkarev/Curtis).

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

### Using Docker (Recommended)
You can use the provided Dockerfile to easily set up an environment with all prerequisites installed.

To build the Docker image:
```shell
docker build -t tvl-env .
```

To run the container interactively with your local repository mounted:
```shell
docker run -it -v $(pwd):/app tvl-env
```
Inside the container, you will have access to `java`, `sbt`, `spin`, and TLA+ tools (`tlc`, `pcal`). The ANTLR jar is available at `$ANTLR_JAR`.

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
You can run all tests using:
```
sbt test
```

If you want to run specific test suites, you can use the separate configurations:

1. **Target Model Generation Tests** (verifies SPN/TLA+ generation from IR):
   ```
   sbt target:test
   ```

2. **Integration Correctness Tests** (verifies TVL language to IR translation against `src/test/tvir/examples/` golden files):
   ```
   sbt correctness:test
   ```
   *Note: If you modify the IR generation or add new examples, you can re-generate the golden `.tvir` output files by setting the `UPDATE_GOLDEN_FILES` environment variable. E.g.:*
   ```
   UPDATE_GOLDEN_FILES=1 sbt correctness:test
   ```

3. **Unit IR Generation Tests** (verifies explicit mapping of TVL constructs to their resulting IR structures):
   ```
   sbt unit:test
   ```
