# TVL
Telecom protocols Verification Language

### Prerequisites
- Java
- ANTLRv4
- SBT
- Target model checker
  - For TLA+, make sure you have `pcal` and `tlc` set, e.g.
    - ```
      pcal() { java -cp <path to tla2tools.jar> -XX:+UseParallelGC -DTLA-Library= pcal.trans "$@"; }
      export -f pcal
      ```
    - ```
      tlc() { java -cp <path to tla2tools.jar> -XX:+UseParallelGC -DTLA-Library= tlc2.TLC "$@"; }
      export -f tlc
      ```

### Building from scratch for the first time
```shell
java -jar <ANTLR .jar> -visitor -no-listener -Dlanguage=Java ./src/main/scala/Grammar/TVL.g4
sbt compile
```

### Usage
```
translate <input file> <output file> <target>
```

### Supported targets
- TLA+ (initial translation made to PlusCal)
- SPIN