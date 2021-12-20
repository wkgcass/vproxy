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

class FullMatchTokenHandler(
  private val type: TokenType, private val raw: String,
  private val precedence: Int = 0,
  private val value: Any? = null
) : TokenHandler {
  private val cs = raw.toCharArray()
  private var cursor = 0

  override fun feed(c: Char): Boolean {
    val canHandle = cursor < cs.size && cs[cursor] == c
    if (canHandle) {
      ++cursor
    }
    return canHandle
  }

  override fun check(): Boolean {
    return cursor == cs.size
  }

  override fun build(lineCol: LineCol): List<Token> {
    if (!check()) {
      throw Exception("check() returns false, but build() is called")
    }
    return listOf(Token(type, raw, lineCol, value))
  }

  override fun reset() {
    cursor = 0
  }

  override fun precedence(): Int {
    return precedence
  }

  override fun toString(): String {
    return "FullMatchTokenHandler(`$raw`)"
  }
}
