# TVL
Telecom protocols Verification Language

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
You can run all tests using:
```
sbt test
```

If you want to run specific test suites, you can use the separate configurations:

1. **Translation Correctness Tests** (verifies SPN/TLA+ generation from IR):
   ```
   sbt correctness:test
   ```

2. **IR Generation Tests** (verifies TVL language to IR translation, and examples integration tests against `src/test/resources/tvir/` golden files):
   ```
   sbt ir:test
   ```
   *Note: If you modify the IR generation or add new examples, you can re-generate the golden `.tvir` output files by setting the `UPDATE_GOLDEN_FILES` environment variable. E.g.:*
   ```
   UPDATE_GOLDEN_FILES=1 sbt ir:test
   ```