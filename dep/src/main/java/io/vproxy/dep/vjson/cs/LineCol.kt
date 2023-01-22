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

class LineCol /*#ifndef KOTLIN_NATIVE {{ */ @JvmOverloads/*}}*/ constructor(
  val filename: String,
  val line: Int,
  val col: Int,
  private val innerOffset: Int = 0,
) {
  companion object {
    val EMPTY = LineCol("", 0, 0)
  }

  /*#ifndef KOTLIN_NATIVE {{ */ @JvmOverloads/*}}*/
  constructor(lineCol: LineCol, innerOffsetIncrease: Int = 0) : this(
    lineCol.filename, lineCol.line, lineCol.col,
    if (lineCol.isEmpty()) 0 else lineCol.innerOffset + innerOffsetIncrease
  )

  fun addCol(n: Int): LineCol {
    if (isEmpty()) return EMPTY
    return LineCol(filename, line, col + n)
  }

  fun inner(): LineCol {
    if (isEmpty()) return EMPTY
    if (innerOffset == 0) return this
    return LineCol(filename, line, col + innerOffset)
  }

  fun isEmpty(): Boolean {
    return filename == "" && line == 0 && col == 0
  }

  override fun toString(): String {
    return "$filename($line:$col)"
  }

  override fun equals(other: Any?): Boolean {
    return other is LineCol
  }

  override fun hashCode(): Int {
    return 0
  }
}
