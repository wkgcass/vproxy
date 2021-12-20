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

package io.vproxy.dep.vjson.pl.token

import io.vproxy.dep.vjson.CharStream
import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.cs.LineCol
import io.vproxy.dep.vjson.parser.NumberParser

class IntTokenHandler : TokenHandler {
  private val parser = NumberParser()
  private var result: JSON.Number<*>? = null
  private var finished = false
  private val sb = StringBuilder()

  @Suppress("DuplicatedCode")
  override fun feed(c: Char): Boolean {
    if (c == '-') { // do not accept negative integers
      return false
    }
    if (c == '.') { // do not accept dot for integers
      return false
    }
    if (finished) {
      return false
    }
    val res = try {
      parser.feed(CharStream.from(charArrayOf(c)))
    } catch (e: Exception) {
      return false
    }
    if (res != null) {
      finished = true
    }
    if (res is JSON.Integer || res is JSON.Long) {
      result = res
    }
    if (finished) {
      return false // it only finishes when getting a non-numeric character
    } else {
      sb.append(c)
      return true
    }
  }

  private fun finish() {
    val res = try {
      NumberParser().build(CharStream.from(sb.toString()), true)
    } catch (e: Exception) {
      return
    }
    if (res != null) {
      finished = true
    }
    if (res is JSON.Integer || res is JSON.Long) {
      result = res
    }
  }

  override fun check(): Boolean {
    if (!finished) {
      finish()
    }
    return finished && result != null
  }

  override fun build(lineCol: LineCol): List<Token> {
    return listOf(Token(TokenType.INTEGER, sb.toString(), lineCol, result))
  }

  override fun reset() {
    parser.reset()
    result = null
    finished = false
    sb.clear()
  }

  override fun precedence(): Int {
    return 1
  }

  override fun toString(): String {
    return "IntTokenHandler"
  }
}
