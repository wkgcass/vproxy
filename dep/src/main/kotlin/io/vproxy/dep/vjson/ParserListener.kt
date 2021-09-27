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

import io.vproxy.dep.vjson.parser.*

interface ParserListener {
  fun onObjectBegin(obj: ObjectParser)
  fun onObjectKey(obj: ObjectParser, key: String)
  fun onObjectValue(obj: ObjectParser, key: String, value: JSON.Instance<*>)
  fun onObjectValueJavaObject(obj: ObjectParser, key: String, value: Any?)
  fun onObjectEnd(obj: ObjectParser)
  fun onObject(obj: JSON.Object)
  fun onObject(obj: Map<String, Any?>?) // opt: nullArraysAndObjects
  fun onArrayBegin(array: ArrayParser)
  fun onArrayValue(array: ArrayParser, value: JSON.Instance<*>)
  fun onArrayValueJavaObject(array: ArrayParser, value: Any?)
  fun onArrayEnd(array: ArrayParser)
  fun onArray(array: JSON.Array)
  fun onArray(array: List<Any?>?) // opt: nullArraysAndObjects
  fun onBoolBegin(bool: BoolParser)
  fun onBoolEnd(bool: BoolParser)
  fun onBool(bool: JSON.Bool)
  fun onBool(bool: Boolean)
  fun onNullBegin(n: NullParser)
  fun onNullEnd(n: NullParser)
  fun onNull(n: JSON.Null)
  fun onNull(n: Unit?)
  fun onNumberBegin(number: NumberParser)
  fun onNumberFractionBegin(number: NumberParser, integer: Long)
  fun onNumberExponentBegin(number: NumberParser, base: Double)
  fun onNumberEnd(number: NumberParser)
  fun onNumber(number: JSON.Number<*>)
  fun onNumber(number: Number)
  fun onStringBegin(string: StringParser)
  fun onStringChar(string: StringParser, c: Char)
  fun onStringEnd(string: StringParser)
  fun onString(string: JSON.String)
  fun onString(string: String)
  fun onError(err: String)
}
