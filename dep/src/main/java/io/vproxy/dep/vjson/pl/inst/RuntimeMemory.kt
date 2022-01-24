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

class RuntimeMemory(
  total: RuntimeMemoryTotal
) {
  private val intValues = IntArray(total.intTotal)
  private val longValues = LongArray(total.longTotal)
  private val floatValues = FloatArray(total.floatTotal)
  private val doubleValues = DoubleArray(total.doubleTotal)
  private val boolValues = BooleanArray(total.boolTotal)
  private val refValues = Array<Any?>(total.refTotal) { null }

  fun getInt(idx: Int): Int {
    return intValues[idx]
  }

  fun setInt(idx: Int, n: Int) {
    intValues[idx] = n
  }

  fun intLen(): Int {
    return intValues.size
  }

  fun getLong(idx: Int): Long {
    return longValues[idx]
  }

  fun setLong(idx: Int, n: Long) {
    longValues[idx] = n
  }

  fun longLen(): Int {
    return longValues.size
  }

  fun getFloat(idx: Int): Float {
    return floatValues[idx]
  }

  fun setFloat(idx: Int, n: Float) {
    floatValues[idx] = n
  }

  fun floatLen(): Int {
    return floatValues.size
  }

  fun getDouble(idx: Int): Double {
    return doubleValues[idx]
  }

  fun setDouble(idx: Int, n: Double) {
    doubleValues[idx] = n
  }

  fun doubleLen(): Int {
    return doubleValues.size
  }

  fun getBool(idx: Int): Boolean {
    return boolValues[idx]
  }

  fun setBool(idx: Int, n: Boolean) {
    boolValues[idx] = n
  }

  fun boolLen(): Int {
    return boolValues.size
  }

  fun getRef(idx: Int): Any? {
    return refValues[idx]
  }

  fun setRef(idx: Int, ref: Any?) {
    refValues[idx] = ref
  }

  fun refLen(): Int {
    return refValues.size
  }

  override fun toString(): String {
    return "RuntimeMemory(\n" +
      "intValues=${intValues.contentToString()}\n" +
      "longValues=${longValues.contentToString()}\n" +
      "floatValues=${floatValues.contentToString()}\n" +
      "doubleValues=${doubleValues.contentToString()}\n" +
      "boolValues=${boolValues.contentToString()}\n" +
      "refValues=${refValues.toList().mapIndexed { idx, o -> "[$idx]: $o" }.joinToString("\n  ", prefix = "\n  ")})"
  }
}
