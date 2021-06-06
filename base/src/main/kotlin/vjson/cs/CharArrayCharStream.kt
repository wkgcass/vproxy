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

package vjson.cs

import vjson.CharStream

class CharArrayCharStream(private val cs: CharArray) : CharStream {
  companion object {
    /*#ifndef KOTLIN_NATIVE {{ */@JvmField/*}}*/
    val EMPTY = CharArrayCharStream(CharArray(0))
  }

  private var idx = -1

  override fun hasNext(i: Int): Boolean {
    return idx + i < cs.size
  }

  override fun moveNextAndGet(): Char {
    return cs[++idx]
  }

  override fun peekNext(i: Int): Char {
    return cs[idx + i]
  }

  override fun toString(): String {
    val sb = StringBuilder()
    sb.append("CharStream(")
    if (idx == -1) {
      sb.append("[]")
    }
    for (i in cs.indices) {
      if (i == idx) {
        sb.append('[')
      }
      sb.append(cs[i])
      if (i == idx) {
        sb.append(']')
      }
    }
    sb.append(")")
    return sb.toString()
  }
}
