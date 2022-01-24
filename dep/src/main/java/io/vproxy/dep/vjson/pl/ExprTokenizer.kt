/*
 * The MIT License
 *
 * Copyright 2021 wkgcass (https://github.com/wkgcass)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.vproxy.dep.vjson.pl

import io.vproxy.dep.vjson.CharStream
import io.vproxy.dep.vjson.cs.LineCol
import io.vproxy.dep.vjson.cs.LineColCharStream
import io.vproxy.dep.vjson.ex.ParserException
import io.vproxy.dep.vjson.parser.ParserOptions
import io.vproxy.dep.vjson.parser.StringParser
import io.vproxy.dep.vjson.pl.token.*
import io.vproxy.dep.vjson.simple.SimpleBool
import io.vproxy.dep.vjson.simple.SimpleString
import io.vproxy.dep.vjson.util.collection.VList

class ExprTokenizer(cs: CharStream, val offset: LineCol) {
  private val cs: LineColCharStream = LineColCharStream(cs, offset.filename, offset)

  constructor(str: String, offset: LineCol) : this(CharStream.from(str), offset)

  private val handlers: List<TokenHandler> = listOf(
    VariableNameTokenHandler(),
    IntTokenHandler(),
    FloatTokenHandler(),
    FullMatchTokenHandler(TokenType.BOOL_TRUE, "true", precedence = 1, value = SimpleBool(true)),
    FullMatchTokenHandler(TokenType.BOOL_FALSE, "false", precedence = 1, value = SimpleBool(false)),
    FullMatchTokenHandler(TokenType.KEY_NULL, "null", precedence = 1),
    FullMatchTokenHandler(TokenType.KEY_NEW, "new", precedence = 1),
    FullMatchTokenHandler(TokenType.LEFT_PAR, "("),
    FullMatchTokenHandler(TokenType.RIGHT_PAR, ")"),
    FullMatchTokenHandler(TokenType.LEFT_BRACKET, "["),
    FullMatchTokenHandler(TokenType.RIGHT_BRACKET, "]"),
    FullMatchTokenHandler(TokenType.PLUS, "+"),
    FullMatchTokenHandler(TokenType.MINUS, "-"),
    FullMatchTokenHandler(TokenType.MULTIPLY, "*"),
    FullMatchTokenHandler(TokenType.DIVIDE, "/"),
    FullMatchTokenHandler(TokenType.MOD, "%"),
    FullMatchTokenHandler(TokenType.PLUS_ASSIGN, "+="),
    FullMatchTokenHandler(TokenType.MINUS_ASSIGN, "-="),
    FullMatchTokenHandler(TokenType.MULTIPLY_ASSIGN, "*="),
    FullMatchTokenHandler(TokenType.DIVIDE_ASSIGN, "/="),
    FullMatchTokenHandler(TokenType.MOD_ASSIGN, "%="),
    FullMatchTokenHandler(TokenType.CMP_GT, ">"),
    FullMatchTokenHandler(TokenType.CMP_GE, ">="),
    FullMatchTokenHandler(TokenType.CMP_LT, "<"),
    FullMatchTokenHandler(TokenType.CMP_LE, "<="),
    FullMatchTokenHandler(TokenType.CMP_EQ, "=="),
    FullMatchTokenHandler(TokenType.CMP_NE, "!="),
    FullMatchTokenHandler(TokenType.LOGIC_NOT, "!"),
    FullMatchTokenHandler(TokenType.LOGIC_AND, "&&"),
    FullMatchTokenHandler(TokenType.LOGIC_OR, "||"),
    FullMatchTokenHandler(TokenType.DOT, "."),
    FullMatchTokenHandler(TokenType.COLON, ":"),
    FullMatchTokenHandler(TokenType.COMMA, ","),
  )

  private val tokenBuffer = VList<Token>()

  fun peek(n: Int = 1): Token? {
    if (tokenBuffer.size() >= n) return tokenBuffer.get(n - 1)
    while (true) {
      val sizeBeforeRead = tokenBuffer.size()
      readToken()
      val size = tokenBuffer.size()
      if (size == sizeBeforeRead) { // eof
        return null
      }
      if (n <= size) {
        return tokenBuffer.get(n - 1)
      }
    }
  }

  fun next(n: Int = 1): Token? {
    if (tokenBuffer.size() >= n) {
      val ret = tokenBuffer.get(n - 1)
      tokenBuffer.removeFirst(n)
      return ret
    }
    val nn = n - tokenBuffer.size()
    tokenBuffer.clear()
    while (true) {
      val sizeBeforeRead = tokenBuffer.size()
      readToken()
      val size = tokenBuffer.size()
      if (sizeBeforeRead == size) { // eof
        return null
      }
      if (nn <= size) {
        tokenBuffer.removeFirst(nn - 1)
        return tokenBuffer.removeFirst()
      }
    }
  }

  private fun readToken() {
    cs.skipBlank()
    if (!cs.hasNext()) {
      return
    }

    val preCheck = cs.peekNext()
    if (preCheck == '\'' || preCheck == '\"') {
      tokenBuffer.add(readStringToken())
      return
    }

    val lineCol = cs.lineCol()

    for (h in handlers) {
      h.reset()
    }
    var last = ArrayList<TokenHandler>()
    last.addAll(handlers)

    val traveled = StringBuilder()
    var prevC: Char? = null
    while (true) {
      if (!cs.hasNext()) {
        return finish(lineCol, last, traveled, null)
      }
      val c = cs.peekNext()
      val current = ArrayList<TokenHandler>()
      for (h in last) {
        if (h.feed(c)) {
          current.add(h)
        }
      }
      if (current.isEmpty()) {
        if (traveled.isEmpty()) {
          throw ParserException("unable to parse the token: all rules failed when reading the first character $c", cs.lineCol())
        }
        if (!canSplitTokens(c) && (prevC != null && !canSplitTokens(prevC))) {
          throw ParserException(
            "unable to parse the token: all rules failed after reading `$traveled`, the next character is $c, " +
              "both ${traveled[traveled.length - 1]} and $c cannot be used to split a token, " +
              "last applicable rules: $last",
            cs.lineCol()
          )
        }
        return finish(lineCol, last, traveled, c)
      } else {
        cs.moveNextAndGet()
      }
      prevC = c
      traveled.append(c)
      last = current
    }
  }

  private fun readStringToken(): Token {
    val lineCol = cs.lineCol()
    val raw = StringBuilder()
    val stringParser = StringParser(ParserOptions().setStringSingleQuotes(true))
    var result: SimpleString? = null
    while (cs.hasNext()) {
      val c = cs.moveNextAndGet()
      raw.append(c)
      val res = stringParser.feed(LineColCharStream(CharStream.from(charArrayOf(c)), lineCol.filename, cs.lineCol()))
      if (res != null) {
        result = res as SimpleString
        break
      }
    }

    if (result == null) {
      throw ParserException("unable to parse the token: incomplete string literal: $raw", cs.lineCol())
    }

    return Token(TokenType.STRING, raw.toString(), lineCol, result)
  }

  private fun finish(lineCol: LineCol, last: ArrayList<TokenHandler>, traveled: StringBuilder, c: Char?) {
    val current = ArrayList<TokenHandler>()
    for (h in last) {
      if (h.check()) {
        current.add(h)
      }
    }
    if (current.size == 0) {
      throw ParserException(
        "unable to parse the token: all rules failed after reading `$traveled`, the next character is ${c?.toString() ?: "(eof)"}, last applicable rules: $last",
        cs.lineCol()
      )
    }
    val handler: TokenHandler
    if (current.size == 1) {
      handler = current[0]
    } else {
      val foo = ArrayList<TokenHandler>()
      for (h in current) {
        if (foo.isEmpty()) {
          foo.add(h)
        } else {
          if (foo[0].precedence() < h.precedence()) {
            foo.clear()
            foo.add(h)
          } else if (foo[0].precedence() == h.precedence()) {
            foo.add(h)
          }
        }
      }
      if (foo.size > 1) {
        throw ParserException(
          "unable to parse the token: multiple rules conflict after reading `$traveled${c?.toString() ?: ""}`: $foo",
          cs.lineCol()
        )
      }
      handler = foo[0]
    }
    val tokens = handler.build(lineCol)
    if (tokens.isEmpty()) {
      throw ParserException("unable to parse the token: no tokens built by $handler", cs.lineCol())
    }
    for (t in tokens) {
      tokenBuffer.add(t)
    }
  }

  private fun canSplitTokens(c: Char): Boolean {
    if (c in 'a'..'z') return false
    if (c in 'A'..'Z') return false
    if (c == '$') return false
    if (c == '_') return false
    if (c in '0'..'9') return false
    if (c.code < 128) return true
    return false
  }

  fun currentLineCol(): LineCol {
    return cs.lineCol()
  }
}
