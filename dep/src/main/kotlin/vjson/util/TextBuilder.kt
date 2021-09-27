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
package vjson.util

class TextBuilder(bufLen: Int) {
  private var buf = CharArray(bufLen) // use buf if not oob

  var bufLen = 0
    private set

  fun getBufCap(): Int {
    return buf.size
  }

  fun clear() {
    bufLen = 0
  }

  fun append(c: Char): TextBuilder {
    buf[bufLen++] = c
    if (bufLen == buf.size) {
      val newbuf = CharArray(bufLen * 4)
      buf.copyInto(newbuf, 0, 0, bufLen)
      buf = newbuf
    }
    return this
  }

  fun removeLast(): TextBuilder {
    if (bufLen != 0) {
      --bufLen
    }
    return this
  }

  override fun toString(): String {
    if (bufLen == 0) {
      return ""
    } else {
      return buf.concatToString(0, bufLen)
    }
  }
}
