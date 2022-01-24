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

import io.vproxy.dep.vjson.cs.LineCol

class VariableNameTokenHandler : TokenHandler {
  private val sb = StringBuilder()

  override fun feed(c: Char): Boolean {
    if (c in 'a'..'z' || c in 'A'..'Z' || c == '$' || c == '_') {
      sb.append(c)
      return true
    }
    if (sb.isNotEmpty()) {
      if (c in '0'..'9') {
        sb.append(c)
        return true
      }
    }
    return false
  }

  override fun check(): Boolean {
    return sb.isNotEmpty()
  }

  override fun build(lineCol: LineCol): List<Token> {
    return listOf(Token(TokenType.VAR_NAME, sb.toString(), lineCol))
  }

  override fun reset() {
    sb.clear()
  }

  override fun precedence(): Int {
    return 0
  }

  override fun toString(): String {
    return "VariableNameTokenHandler"
  }
}
