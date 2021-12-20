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

package io.vproxy.dep.vjson.pl.type

import io.vproxy.dep.vjson.pl.inst.RuntimeMemoryTotal

open class MemoryAllocator {
  private var intCount = 0
  private var longCount = 0
  private var floatCount = 0
  private var doubleCount = 0
  private var boolCount = 0
  private var refCount = 0

  fun nextIndexFor(type: TypeInstance): Int {
    return when (type) {
      is IntType -> nextIntIndex()
      is LongType -> nextLongIndex()
      is FloatType -> nextFloatIndex()
      is DoubleType -> nextDoubleIndex()
      is BoolType -> nextBoolIndex()
      else -> nextRefIndex()
    }
  }

  fun getIntTotal(): Int {
    return intCount
  }

  fun getLongTotal(): Int {
    return longCount
  }

  fun getFloatTotal(): Int {
    return floatCount
  }

  fun getDoubleTotal(): Int {
    return doubleCount
  }

  fun getBoolTotal(): Int {
    return boolCount
  }

  fun getRefTotal(): Int {
    return refCount
  }

  fun nextIntIndex(): Int {
    return intCount++
  }

  fun nextLongIndex(): Int {
    return longCount++
  }

  fun nextFloatIndex(): Int {
    return floatCount++
  }

  fun nextDoubleIndex(): Int {
    return doubleCount++
  }

  fun nextBoolIndex(): Int {
    return boolCount++
  }

  fun nextRefIndex(): Int {
    return refCount++
  }

  open fun getTotal(): RuntimeMemoryTotal {
    return RuntimeMemoryTotal(intCount, longCount, floatCount, doubleCount, boolCount, refCount)
  }
}
