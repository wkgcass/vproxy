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
package io.vproxy.dep.vjson.simple

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.Stringifier
import io.vproxy.dep.vjson.cs.LineCol
import io.vproxy.dep.vjson.pl.ScriptifyContext

class SimpleInteger /*#ifndef KOTLIN_NATIVE {{ */ @JvmOverloads/*}}*/ constructor(
  private val value: Int,
  private val lineCol: LineCol = LineCol.EMPTY
) : AbstractSimpleInstance<Int>(), JSON.Integer {
  public override fun _toJavaObject(): Int {
    return value
  }

  override fun stringify(builder: StringBuilder, sfr: Stringifier) {
    builder.append(value)
  }

  override fun scriptify(builder: StringBuilder, ctx: ScriptifyContext) {
    builder.append(value)
  }

  override fun lineCol(): LineCol {
    return lineCol
  }

  override fun _toString(): String {
    return "Integer($value)"
  }

  override fun intValue(): Int {
    return value
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other is JSON.Integer) {
      return value == other.intValue()
    } else if (other is JSON.Long) {
      return value.toLong() == other.longValue()
    } else {
      return false
    }
  }

  override fun hashCode(): Int {
    return value.hashCode()
  }
}
