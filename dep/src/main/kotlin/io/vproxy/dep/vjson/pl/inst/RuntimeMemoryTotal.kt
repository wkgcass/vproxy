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

package io.vproxy.dep.vjson.pl.inst

data class RuntimeMemoryTotal(
  val intTotal: Int = 0,
  val longTotal: Int = 0,
  val floatTotal: Int = 0,
  val doubleTotal: Int = 0,
  val boolTotal: Int = 0,
  val refTotal: Int = 0,
) {
  constructor(
    total: RuntimeMemoryTotal,
    intTotal: Int = 0,
    longTotal: Int = 0,
    floatTotal: Int = 0,
    doubleTotal: Int = 0,
    boolTotal: Int = 0,
    refTotal: Int = 0,
  ) : this(
    intTotal + total.intTotal,
    longTotal + total.longTotal,
    floatTotal + total.floatTotal,
    doubleTotal + total.doubleTotal,
    boolTotal + total.boolTotal,
    refTotal + total.refTotal
  )
}
