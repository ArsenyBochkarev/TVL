package Translator.CEGAR

/** Minimal JSON parser/printer (objects, arrays, strings, integers, booleans,
  * null). The abstraction sidecar (tvl-abstraction/1) is the only consumer, so
  * integers suffice for numbers; no external dependencies are introduced. */
object Json {

  sealed trait JValue {
    def field(name: String): Option[JValue] = this match
      case JObj(fields) => fields.collectFirst { case (n, v) if n == name => v }
      case _            => None

    def items: List[JValue] = this match
      case JArr(xs) => xs
      case _        => Nil

    def asInt: Option[Int] = this match
      case JNum(n) => Some(n)
      case _       => None

    def asString: Option[String] = this match
      case JStr(s) => Some(s)
      case _       => None

    def asBool: Option[Boolean] = this match
      case JBool(b) => Some(b)
      case _        => None
  }

  case class JObj(fields: List[(String, JValue)]) extends JValue
  case class JArr(values: List[JValue]) extends JValue
  case class JStr(s: String) extends JValue
  case class JNum(n: Int) extends JValue
  case class JBool(b: Boolean) extends JValue
  case object JNull extends JValue

  // ---------- parsing ----------

  def parse(text: String): JValue =
    val p = new Parser(text)
    val v = p.parseValue()
    p.skipWs()
    if !p.atEnd then p.fail("trailing content after JSON value")
    v

  private class Parser(s: String) {
    var i = 0

    def atEnd: Boolean = i >= s.length
    def fail(msg: String): Nothing =
      throw new AbstractionException(s"invalid JSON at offset $i: $msg")

    def skipWs(): Unit =
      while i < s.length && (s(i) == ' ' || s(i) == '\t' || s(i) == '\n' || s(i) == '\r') do i += 1

    def expect(c: Char): Unit =
      if atEnd || s(i) != c then fail(s"expected '$c'")
      i += 1

    def parseValue(): JValue =
      skipWs()
      if atEnd then fail("unexpected end of input")
      s(i) match
        case '{' => parseObj()
        case '[' => parseArr()
        case '"' => JStr(parseString())
        case 't' => lit("true", JBool(true))
        case 'f' => lit("false", JBool(false))
        case 'n' => lit("null", JNull)
        case c if c == '-' || c.isDigit => parseNum()
        case c => fail(s"unexpected character '$c'")

    private def lit(word: String, v: JValue): JValue =
      if !s.startsWith(word, i) then fail(s"expected '$word'")
      i += word.length
      v

    private def parseNum(): JNum =
      val start = i
      if !atEnd && s(i) == '-' then i += 1
      while !atEnd && s(i).isDigit do i += 1
      val tok = s.substring(start, i)
      tok.toIntOption.map(JNum.apply).getOrElse(fail(s"expected integer, got \"$tok\""))

    private def sep(end: Char): Boolean =
      // true -> continue parsing elements; false -> collection closed
      skipWs()
      if !atEnd && s(i) == ',' then { i += 1; true }
      else { expect(end); false }

    private def parseObj(): JObj =
      expect('{')
      val fields = List.newBuilder[(String, JValue)]
      skipWs()
      if !atEnd && s(i) == '}' then i += 1
      else
        var more = true
        while more do
          skipWs()
          val name = parseString()
          skipWs()
          expect(':')
          fields += (name -> parseValue())
          more = sep('}')
      JObj(fields.result())

    private def parseArr(): JArr =
      expect('[')
      val values = List.newBuilder[JValue]
      skipWs()
      if !atEnd && s(i) == ']' then i += 1
      else
        var more = true
        while more do
          values += parseValue()
          more = sep(']')
      JArr(values.result())

    private def parseString(): String =
      expect('"')
      val sb = new StringBuilder
      while !atEnd && s(i) != '"' do
        if s(i) == '\\' then
          i += 1
          if atEnd then fail("unterminated escape")
          s(i) match
            case '"'  => sb += '"'
            case '\\' => sb += '\\'
            case '/'  => sb += '/'
            case 'b'  => sb += '\b'
            case 'f'  => sb += '\f'
            case 'n'  => sb += '\n'
            case 'r'  => sb += '\r'
            case 't'  => sb += '\t'
            case 'u'  =>
              if i + 4 >= s.length then fail("truncated \\u escape")
              val hex = s.substring(i + 1, i + 5)
              val cp =
                try Integer.parseInt(hex, 16)
                catch case _: NumberFormatException => fail(s"malformed \\u escape \"$hex\"")
              if cp < 0 || cp > 0xFFFF then fail(s"\\u escape out of range \"$hex\"")
              sb += cp.toChar
              i += 4
            case c => fail(s"unknown escape '\\$c'")
        else sb += s(i)
        i += 1
      expect('"')
      sb.result()
  }

  // ---------- printing ----------

  def print(v: JValue): String = {
    val sb = new StringBuilder
    write(v, sb)
    sb.result()
  }

  private def write(v: JValue, sb: StringBuilder): Unit = v match
    case JObj(fields) =>
      sb += '{'
      fields.zipWithIndex.foreach { (f, idx) =>
        if idx > 0 then sb += ','
        writeString(f._1, sb); sb += ':'; write(f._2, sb)
      }
      sb += '}'
    case JArr(values) =>
      sb += '['
      values.zipWithIndex.foreach { (x, idx) =>
        if idx > 0 then sb += ','
        write(x, sb)
      }
      sb += ']'
    case JStr(s)  => writeString(s, sb)
    case JNum(n)  => sb ++= n.toString
    case JBool(b) => sb ++= (if b then "true" else "false")
    case JNull    => sb ++= "null"

  private def writeString(s: String, sb: StringBuilder): Unit =
    sb += '"'
    s.foreach {
      case '"'  => sb ++= "\\\""
      case '\\' => sb ++= "\\\\"
      case '\n' => sb ++= "\\n"
      case '\r' => sb ++= "\\r"
      case '\t' => sb ++= "\\t"
      case c    => sb += c
    }
    sb += '"'
}
