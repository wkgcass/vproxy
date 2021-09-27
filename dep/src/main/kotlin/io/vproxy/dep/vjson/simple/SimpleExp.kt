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
import kotlin.math.pow

class SimpleExp(
  private val base: Double, private val exponent: Int
) : AbstractSimpleInstance<Double>(), JSON.Exp {
  private val value: Double = base * 10.0.pow(exponent.toDouble())

  public override fun _toJavaObject(): Double {
    return value
  }

  override fun stringify(builder: StringBuilder, sfr: Stringifier) {
    builder.append(base).append("e").append(exponent)
  }

  override fun _toString(): String {
    return "Exp(" + base + "e" + exponent + "=" + value + ")"
  }

  override fun doubleValue(): Double {
    return value
  }

  override fun base(): Double {
    return base
  }

  override fun exponent(): Int {
    return exponent
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is JSON.Double) return false
    return other.doubleValue().compareTo(doubleValue()) == 0
  }

  override fun hashCode(): Int {
    var result = base.hashCode()
    result = 31 * result + exponent
    result = 31 * result + value.hashCode()
    return result
  }
}
