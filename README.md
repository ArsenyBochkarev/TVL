# TVL
TVL is a small DSL designed to create models of communication protocols. Each actor of this protocol is described in an independent procedure. The language semantics is based on Communicating Automata with bounded channels.

The state of the system is primarily characterized by message queues between actors, with messages acting as atomic tokens. Operations like sending and receiving a message mutate the global state of these queues.

TVL also provides means for property verification through [specifications block](docs/specifications). It supports custom **`ltl`** and **`ctl`** formulas using user-defined labels, as well as template-based properties and implicitly generated label-based specifications.

Models in TVL are first compiled into TVL IR, which is a bit lower-level intermediate representation. Next, the generated IR is translated into a target model checker language to perform the actual verification. Also, there is an ongoing work on a TVL-specific model checker called [Curtis](https://github.com/ArsenyBochkarev/Curtis).

There are two formal semantics for TVL language:
- [Operational semantics](docs/semantics/operational_en.md)
- [Kripke structures](docs/semantics/kripke_en.md)

### Prerequisites
- Java
- ANTLRv4
- SBT
- Target model checker
  - For TLA+, make sure you have `pcal` and `tlc` set, e.g.
    - `~/.local/bin/tlc`:
      ```
      #!/bin/bash
      java -cp /path/to/tla2tools.jar tlc2.TLC "$@"
      ```
    - `~/.local/bin/pcal`:
      ```
      #!/bin/bash
      java -cp /path/to/tla2tools.jar pcal.trans "$@"
      ```

### Using Docker (Recommended)
You can use the provided Dockerfile to easily set up an environment with all prerequisites installed.

To build the Docker image:

```shell
docker build --build-arg UID=$(id -u) --build-arg GID=$(id -g) -t tvl-env .
```

To run the container interactively with your local repository mounted:

```shell
docker run -it --user dev -v $(pwd):/app tvl-env
```

Inside the container, you will have access to `java`, `sbt`, `spin`, and TLA+ tools (`tlc`, `pcal`). The ANTLR jar is available at `$ANTLR_JAR`.

### Building from scratch for the first time
```shell
java -jar <ANTLR .jar> -visitor -no-listener -Dlanguage=Java ./src/main/scala/Grammar/TVL.g4
sbt compile
```

### Usage
```
translate <input file> <target> [--dump-ir=<path>] [--channel-size=...] [--trace-size=...]
```
- `<target>` is `tla` or `spin`, or `ir` (no verification is run)
- `--dump-ir=<path>` additionally dumps the TVL IR to the given path (works with any target)
- `--channel-size` / `--trace-size` set the channel size and counterexample size limits
- The output file is always written next to the input file

Altough it is highly recommended to use [VS Code plugin](https://github.com/ArsenyBochkarev/tvl-vscode).

### Supported targets
- TLA+ (initial translation made to PlusCal)
- SPIN
- TVL IR

#### TVL IR output
The TVL IR is a standalone, serializable artifact decoupled from the TVL source. It can be dumped in the `.tvir` text format either frontend-only:

```bash
./translate src/model.tvl ir  # writes src/model.tvir
```

or alongside a normal target run:

```bash
./translate src/model.tvl tla --dump-ir=out/model.tvir
```

This is the intended entry point for external backends such as [Curtis](https://github.com/ArsenyBochkarev/Curtis).

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
   *Note: In case of IR generation modifications or new examples addition, one can re-generate the golden `.tvir` output files by setting the `UPDATE_GOLDEN_FILES` environment variable. E.g.:*
   ```
   UPDATE_GOLDEN_FILES=1 sbt correctness:test
   ```

3. **Unit IR Generation Tests** (verifies explicit mapping of TVL constructs to their resulting IR structures):
   ```
   sbt unit:test
   ```
