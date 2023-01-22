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

package io.vproxy.dep.vjson.cs

import io.vproxy.dep.vjson.CharStream

class PeekCharStream(private val cs: CharStream, private var cursor: Int = 0) : CharStream {
  override fun hasNext(i: Int): Boolean {
    return cs.hasNext(cursor + i)
  }

  override fun moveNextAndGet(): Char {
    cursor += 1
    return cs.peekNext(cursor)
  }

  override fun peekNext(i: Int): Char {
    return cs.peekNext(cursor + i)
  }

  fun getCursor(): Int {
    return cursor
  }

  fun setCursor(cursor: Int) {
    this.cursor = cursor
  }

  override fun lineCol(): LineCol {
    val lineCol = cs.lineCol()
    if (lineCol.isEmpty()) return lineCol

    var line = lineCol.line
    var col = lineCol.col
    for (i in 1..cursor) {
      val c = cs.peekNext(i)
      if (c == '\n') {
        line += 1
        col = 1
        continue
      } else if (c == '\r') {
        if (cs.hasNext(i + 1)) {
          val cc = cs.peekNext(i + 1)
          if (cc != '\n') {
            line += 1
            col = 1
            continue
          }
        } else {
          line += 1
          col = 1
          continue
        }
      }
      col += 1
    }
    return LineCol(lineCol.filename, line, col)
  }

  fun rollback() {
    if (cursor == 0) {
      throw IllegalStateException()
    }
    --cursor
  }
}
