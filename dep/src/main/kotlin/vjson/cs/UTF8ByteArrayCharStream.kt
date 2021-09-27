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

/*
 *    Char. number range  |        UTF-8 octet sequence
 *       (hexadecimal)    |              (binary)
 *    --------------------+---------------------------------------------
 *    0000 0000-0000 007F | 0xxxxxxx
 *    0000 0080-0000 07FF | 110xxxxx 10xxxxxx
 *    0000 0800-0000 FFFF | 1110xxxx 10xxxxxx 10xxxxxx
 *    0001 0000-0010 FFFF | 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
 */
class UTF8ByteArrayCharStream(private val array: ByteArray) : CharStream {
  private var idx = -1
  private var idxIsFirstJavaCharFor4BytesUTF8Char = false

  private fun nextLen(off: Int): Int {
    val idx = idx + off
    if (array.size == idx) {
      return -1
    }
    val first = array[idx]
    if ((first and 0b1_000_0000) == 0b0000_0000) {
      return 1
    }
    val total: Int
    if ((first and 0b111_0_0000) == 0b110_0_0000) {
      total = 2
    } else if ((first and 0b1111_0000) == 0b1110_0000) {
      total = 3
    } else if ((first and 0b1111_1_000) == 0b1111_0_000) {
      total = 4
    } else {
      throw IllegalArgumentException("encoding not UTF-8, first byte not 0xxxxxxx, 110xxxxx, 1110xxxx, 11110xxx: $first")
    }
    return total
  }

  override fun hasNext(i: Int): Boolean {
    var mutI = i
    var total = 0
    if (idxIsFirstJavaCharFor4BytesUTF8Char) {
      if (mutI == 1) {
        return true
      } else {
        mutI -= 1
        total += 4
      }
    }
    var met4bytes = false
    for (n in 0 until mutI) {
      val t = nextLen(1 + total)
      if (t < 0) {
        return false
      }
      if (t == 4) {
        if (!met4bytes) {
          met4bytes = true
          continue
        } else {
          met4bytes = false
          // fall through
        }
      }
      total += t
    }
    return true
  }

  override fun moveNextAndGet(): Char {
    val total = nextLen(1)
    val c = peekNext(1)
    if (total == 4) {
      if (idxIsFirstJavaCharFor4BytesUTF8Char) {
        idx += 4
      }
      idxIsFirstJavaCharFor4BytesUTF8Char = !idxIsFirstJavaCharFor4BytesUTF8Char
    } else {
      idx += total
    }
    return c
  }

  override fun peekNext(i: Int): Char {
    var mutI = i
    var offset = 1
    if (idxIsFirstJavaCharFor4BytesUTF8Char) {
      if (mutI == 1) {
        return fourBytesChar(1)[1]
      } else {
        mutI -= 1
        offset += 4
      }
    }
    var met4bytes = false
    for (n in 0 until mutI - 1) {
      val len = nextLen(offset)
      if (len == 4) {
        if (met4bytes) {
          met4bytes = false
          // fall through
        } else {
          met4bytes = true
          continue  // do not increase offset
        }
      }
      offset += len
    }

    val len = nextLen(offset)
    if (len == 4) {
      if (met4bytes) {
        return fourBytesChar(offset)[1]
      } else {
        return fourBytesChar(offset)[0]
      }
    }
    val first = array[idx + offset]
    if (len == 1) {
      return array[idx + offset].toChar()
    } else {
      val second = array[idx + offset + 1]
      if (len == 2) {
        return ((first and 0b0001_1111) shl 6 or (second and 0b0011_1111)).toChar()
      } else {
        val third = array[idx + offset + 2]
        // assert len == 3;
        return ((first and 0b0000_1111) shl 12 or ((second and 0b0011_1111) shl 6) or (third and 63)).toChar()
      }
    }
  }

  private fun fourBytesChar(offset: Int): CharArray {
    val first = array[idx + offset]
    val second = array[idx + offset + 1]
    val third = array[idx + offset + 2]
    val fourth = array[idx + offset + 3]
    var n: Int =
      (((first and 0b0000_0111) shl 18) or ((second and 63) shl 12) or ((third and 63) shl 6) or (fourth and 63))
    n -= 0x10000
    val a = ((n shr 10) or 0b1101_10_00_0000_0000).toChar()
    val b = ((n and 0b0000_00_11_1111_1111) or 0b1101_11_00_0000_0000).toChar()
    return charArrayOf(a, b)
  }

  private inline infix
  @Suppress("NOTHING_TO_INLINE")
  // #ifdef COVERAGE {{@lombok.Generated}}
  fun Byte.and(other: Int): Int = this.toInt() and other
}
