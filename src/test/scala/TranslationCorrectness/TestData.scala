package TranslationCorrectness

case class TargetExpectation(targetName: String, expectedPatterns: List[String])
case class TranslationTestCase(constructName: String, tvlCode: String, expectations: List[TargetExpectation])

object TestData {
  val rules: List[TranslationTestCase] = List(
    TranslationTestCase(
      constructName = "actor",
      tvlCode = "module Test\nactor A { skip }",
      expectations = List(
        TargetExpectation("spin", List("""proctype A\(\)""")),
        TargetExpectation("tla", List("""fair process A = "A"""")),
      )
    ),

    TranslationTestCase(
      constructName = "send",
      tvlCode = "module Test\nactor B {}\nactor A { send Msg to B }",
      expectations = List(
        TargetExpectation("tla", List("""channels\["Q_B_A"\] := Append\(channels\["Q_B_A"\], "MSG_Msg"\)""")),
        TargetExpectation("spin", List("""Q_B_A ! MSG_Msg""")),
      )
    ),

    TranslationTestCase(
      constructName = "receive",
      tvlCode = "module Test\nactor B {}\nactor A { receive Msg from B }",
      expectations = List(
        TargetExpectation("tla", List(
          """await Len\(channels\["Q_A_B"\]\) > 0 /\\ Head\(channels\["Q_A_B"\]\) = "MSG_Msg"""",
          """channels\["Q_A_B"\] := Tail\(channels\["Q_A_B"\]\)"""
        )),
        TargetExpectation("spin", List("""Q_A_B \? MSG_Msg""")),
      )
    ),

    TranslationTestCase(
      constructName = "receive alts",
      tvlCode = """
        module Test
        actor B {} actor C {}
        actor A {
          receive alts {
              MSG1 from B => { skip }
              MSG2 from C => { skip }
          }
        }""",
      expectations = List(
        TargetExpectation("tla", List("""either""", """await Len\(channels\["Q_A_B"\]\).+MSG1""", """or""", """await Len\(channels\["Q_A_C"\]\).+MSG2""", """end either""")),
        TargetExpectation("spin", List("""if""", """:: Q_A_B \? MSG_MSG1 ->""", """:: Q_A_C \? MSG_MSG2 ->""", """fi""")),
      )
    ),

    TranslationTestCase(
      constructName = "choose",
      tvlCode = "module Test\nactor A { choose { skip } or { skip } }",
      expectations = List(
        TargetExpectation("tla", List("""either goto L_\d+;""", """or goto L_\d+;""", """end either;""")),
        TargetExpectation("spin", List("""if""", """:: true -> goto L_\d+""", """:: true -> goto L_\d+""", """fi""")),
      )
    ),

    TranslationTestCase(
      constructName = "bounded repeat",
      tvlCode = "module Test\nactor A { repeat 5 { skip } }",
      expectations = List(
        TargetExpectation("tla", List(
          """if guard_\w+ > 0 then guard_\w+ := guard_\w+ - 1; goto L_\d+; else goto L_\d+; end if;"""
        )),
        TargetExpectation("spin", List(
          """if :: guard_\w+ > 0 -> guard_\w+ = guard_\w+ - 1; goto L_\d+; :: else -> goto L_\d+; fi;"""
        ))
      )
    ),

    TranslationTestCase(
      constructName = "infinite repeat",
      tvlCode = "module Test\nactor A { repeat { skip } }",
      expectations = List(
        // Just a jumps to first labels
        TargetExpectation("tla", List(
          """skip; goto L_\d+;"""
        )),
        TargetExpectation("spin", List(
          """skip; goto L_\d+"""
        ))
      )
    ),

    TranslationTestCase(
      constructName = "break",
      tvlCode = "module Test\nactor A { repeat { break } }",
      expectations = List(
        // Just a jumps to ending labels
        TargetExpectation("tla", List(
          """goto L_\d+;"""
        )),
        TargetExpectation("spin", List(
          """goto L_\d+;"""
        ))
      )
    ),

    TranslationTestCase(
      constructName = "parallel",
      tvlCode = """
        module Test
        actor A {
          parallel {
            skip
          } and {
            skip
          }
        }""",
      expectations = List(
        TargetExpectation("tla", List(
          """either await sched_block\d+_branch\d+ /= 0; either await sched_block\d+_branch\d+ = 1; goto L_\d+;""",
          """or await sched_block\d+_branch\d+ = 0 /\\ sched_block\d+_branch\d+ = 0 /\\ TRUE; goto L_\d+; end either;"""
        )),
        TargetExpectation("spin", List(
          """if :: sched_block\d+_branch\d+ != 0 -> if :: sched_block\d+_branch\d+ == 1 -> goto L_\d+""",
          """:: else -> goto L_\d+ fi;"""
        ))
      )
    )
  )
}