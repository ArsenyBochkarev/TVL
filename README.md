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
```
sbt test
```