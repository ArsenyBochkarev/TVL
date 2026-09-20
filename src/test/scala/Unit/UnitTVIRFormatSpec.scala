package Unit

import Translator.FrontendPipeline
import org.antlr.v4.runtime.CharStreams
import org.scalatest.funsuite.AnyFunSuite

/**
 * Unit tests for the shared .tvir serializer
 */
class UnitTVIRFormatSpec extends AnyFunSuite {

  test("no specs/labels: extended dump equals actors-only dump") {
    val tvl =
      """module Test
        |
        |actor B {}
        |actor A { send Msg to B }""".stripMargin

    val res = FrontendPipeline.run(CharStreams.fromString(tvl), debug = false)

    assert(res.toTVIRString == res.toActorsTVIRString,
      "Extended .tvir dump must be byte-identical to the actors-only dump when there are no specs and no labels")
  }

  test("sections are appended in fixed order, sorted, blank-line separated") {
    val tvl =
      """module Test
        |
        |actor B {
        |    BStart: receive Msg from A
        |}
        |
        |actor A {
        |    sent: send Msg to B
        |}
        |
        |specs {
        |    MsgDeliveredProperty;
        |    ltl Delivered: "[] (A.sent -> <> B.BStart)";
        |}""".stripMargin

    val res = FrontendPipeline.run(CharStreams.fromString(tvl), debug = false)

    val expectedSuffix =
      "\nTemplate specs:\n  MsgDeliveredProperty\n" +
      "\nUser specs:\n  ltl Delivered: [] (A.sent -> <> B.BStart)\n" +
      "\nLabels:\n  A.sent: 3\n  B.BStart: 1\n"

    assert(res.toTVIRString == res.toActorsTVIRString + expectedSuffix,
      s"""|Extended .tvir dump must append the sections after the unchanged actors part.
          |
          |Generated:
          |${res.toTVIRString}""".stripMargin)
  }
}
