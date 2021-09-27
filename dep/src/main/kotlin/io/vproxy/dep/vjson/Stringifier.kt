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
package io.vproxy.dep.vjson

interface Stringifier {
  fun beforeObjectBegin(sb: StringBuilder, obj: JSON.Object)
  fun afterObjectBegin(sb: StringBuilder, obj: JSON.Object)
  fun beforeObjectKey(sb: StringBuilder, obj: JSON.Object, key: String)
  fun afterObjectKey(sb: StringBuilder, obj: JSON.Object, key: String)
  fun beforeObjectColon(sb: StringBuilder, obj: JSON.Object)
  fun afterObjectColon(sb: StringBuilder, obj: JSON.Object)
  fun beforeObjectValue(sb: StringBuilder, obj: JSON.Object, key: String, value: JSON.Instance<*>)
  fun afterObjectValue(sb: StringBuilder, obj: JSON.Object, key: String, value: JSON.Instance<*>)
  fun beforeObjectComma(sb: StringBuilder, obj: JSON.Object)
  fun afterObjectComma(sb: StringBuilder, obj: JSON.Object)
  fun beforeObjectEnd(sb: StringBuilder, obj: JSON.Object)
  fun afterObjectEnd(sb: StringBuilder, obj: JSON.Object)
  fun beforeArrayBegin(sb: StringBuilder, array: JSON.Array)
  fun afterArrayBegin(sb: StringBuilder, array: JSON.Array)
  fun beforeArrayValue(sb: StringBuilder, array: JSON.Array, value: JSON.Instance<*>)
  fun afterArrayValue(sb: StringBuilder, array: JSON.Array, value: JSON.Instance<*>)
  fun beforeArrayComma(sb: StringBuilder, array: JSON.Array)
  fun afterArrayComma(sb: StringBuilder, array: JSON.Array)
  fun beforeArrayEnd(sb: StringBuilder, array: JSON.Array)
  fun afterArrayEnd(sb: StringBuilder, array: JSON.Array)
}
