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
package vjson.listener

import vjson.JSON
import vjson.ParserListener
import vjson.parser.*

abstract class AbstractParserListener : ParserListener {
  override fun onObjectBegin(obj: ObjectParser) {}
  override fun onObjectKey(obj: ObjectParser, key: String) {}
  override fun onObjectValue(obj: ObjectParser, key: String, value: JSON.Instance<*>) {}
  override fun onObjectValueJavaObject(obj: ObjectParser, key: String, value: Any?) {}
  override fun onObjectEnd(obj: ObjectParser) {}
  override fun onObject(obj: JSON.Object) {}
  override fun onObject(obj: Map<String, *>?) {}
  override fun onArrayBegin(array: ArrayParser) {}
  override fun onArrayValue(array: ArrayParser, value: JSON.Instance<*>) {}
  override fun onArrayValueJavaObject(array: ArrayParser, value: Any?) {}
  override fun onArrayEnd(array: ArrayParser) {}
  override fun onArray(array: JSON.Array) {}
  override fun onArray(array: List<*>?) {}
  override fun onBoolBegin(bool: BoolParser) {}
  override fun onBoolEnd(bool: BoolParser) {}
  override fun onBool(bool: JSON.Bool) {}
  override fun onBool(bool: Boolean) {}
  override fun onNullBegin(n: NullParser) {}
  override fun onNullEnd(n: NullParser) {}
  override fun onNull(n: JSON.Null) {}
  override fun onNull(n: Unit?) {}
  override fun onNumberBegin(number: NumberParser) {}
  override fun onNumberFractionBegin(number: NumberParser, integer: Long) {}
  override fun onNumberExponentBegin(number: NumberParser, base: Double) {}
  override fun onNumberEnd(number: NumberParser) {}
  override fun onNumber(number: JSON.Number<*>) {}
  override fun onNumber(number: Number) {}
  override fun onStringBegin(string: StringParser) {}
  override fun onStringChar(string: StringParser, c: Char) {}
  override fun onStringEnd(string: StringParser) {}
  override fun onString(string: JSON.String) {}
  override fun onString(string: String) {}
  override fun onError(err: String) {}
}
