package Unit

case class IRTestCase(constructName: String, tvlCode: String, expectedPatterns: List[String])

object UnitTestData {
  val rules: List[IRTestCase] = List(
    IRTestCase(
      constructName = "actor",
      tvlCode = "module Test\nactor A { skip }",
      expectedPatterns = List(
        """Actor: A""",
        """IRSkip\(.+?\)""",
        """IREnd\(.+?\)"""
      )
    ),

    IRTestCase(
      constructName = "send",
      tvlCode = "module Test\nactor B {}\nactor A { send Msg to B }",
      expectedPatterns = List(
        """Actor: A""",
        """IRQueuePush\(.+?,Q\[B\]\[A\],Msg\)"""
      )
    ),

    IRTestCase(
      constructName = "receive",
      tvlCode = "module Test\nactor B {}\nactor A { receive Msg from B }",
      expectedPatterns = List(
        """Actor: A""",
        """IRQueuePop\(.+?,Q\[A\]\[B\],Msg\)"""
      )
    ),

    IRTestCase(
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
      expectedPatterns = List(
        """Actor: A""",
        """IRBranch\(.+?\)""",
        """QueueCondition\(Q\[A\]\[B\],MSG1,.+?\)""",
        """QueueCondition\(Q\[A\]\[C\],MSG2,.+?\)"""
      )
    ),

    IRTestCase(
      constructName = "choose",
      tvlCode = "module Test\nactor A { choose { skip } or { skip } }",
      expectedPatterns = List(
        """Actor: A""",
        """IRChoice\(.+?\)"""
      )
    ),

    IRTestCase(
      constructName = "bounded repeat",
      tvlCode = "module Test\nactor A { repeat 5 { skip } }",
      expectedPatterns = List(
        """Actor: A""",
        """IRJumpGuard\(.+?\)"""
      )
    ),

    IRTestCase(
      constructName = "infinite repeat",
      tvlCode = "module Test\nactor A { repeat { skip } }",
      expectedPatterns = List(
        """Actor: A""",
        """IRJump\(.+?\)"""
      )
    ),

    IRTestCase(
      constructName = "break",
      tvlCode = "module Test\nactor A { repeat { break } }",
      expectedPatterns = List(
        """Actor: A""",
        """IRJump\(.+?\)"""
      )
    ),

    IRTestCase(
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
      expectedPatterns = List(
        """Actor: A""",
        """IRParallelExec\(.+?\)""",
        """IRParallelEnd\(.+?\)"""
      )
    )
  )
}
