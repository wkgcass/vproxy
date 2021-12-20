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

class LineColCharStream /*#ifndef KOTLIN_NATIVE {{ */ @JvmOverloads/*}}*/ constructor(
  private val cs: CharStream,
  private val filename: String,
  private val offset: LineCol? = null
) : CharStream {
  private var currentLine = 1
  private var currentCol = 1

  override fun hasNext(i: Int): Boolean {
    return cs.hasNext(i)
  }

  override fun moveNextAndGet(): Char {
    val c = cs.moveNextAndGet()
    if (c == '\r') {
      if (!cs.hasNext() || cs.peekNext() != '\n') {
        newLine()
      } else {
        currentCol += 1
      }
    } else if (c == '\n') {
      newLine()
    } else {
      currentCol += 1
    }
    return c
  }

  private fun newLine() {
    currentLine += 1
    currentCol = 1
  }

  override fun peekNext(i: Int): Char {
    return cs.peekNext(i)
  }

  override fun lineCol(): LineCol {
    return if (offset == null) {
      LineCol(filename, currentLine, currentCol)
    } else {
      LineCol(filename, currentLine + offset.line - 1, currentCol + offset.col - 1)
    }
  }
}
