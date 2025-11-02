# TVL
Telecom protocols Verification Language


### Building from scratch
```shell
java -jar <ANTLR .jar> -visitor -no-listener -Dlanguage=Java ./src/main/scala/Grammar/TVL.g4
sbt compile
translate <input file> <output file>
```