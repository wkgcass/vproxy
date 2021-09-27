package vjson.ex

open class ParserException : RuntimeException {
  constructor(msg: String) : super(msg)
  constructor(msg: String, cause: Throwable) : super(msg, cause)
}
