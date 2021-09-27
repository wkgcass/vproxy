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
package vjson.simple

import vjson.JSON
import vjson.stringifier.EmptyStringifier
import vjson.stringifier.PrettyStringifier

abstract class AbstractSimpleInstance<T> : JSON.Instance<T> {
  private var javaObject: T? = null
  private var stringified: String? = null
  private var pretty: String? = null
  private var javaToString: String? = null

  override fun toJavaObject(): T {
    if (javaObject == null) {
      javaObject = _toJavaObject()
    }
    return javaObject!!
  }

  protected abstract fun _toJavaObject(): T

  override fun stringify(): String {
    if (stringified == null) {
      val sb = StringBuilder()
      stringify(sb, EmptyStringifier.INSTANCE)
      stringified = sb.toString()
    }
    return stringified!!
  }

  override fun pretty(): String {
    if (pretty == null) {
      val sb = StringBuilder()
      stringify(sb, PrettyStringifier())
      pretty = sb.toString()
    }
    return pretty!!
  }

  override fun toString(): String {
    if (javaToString == null) {
      javaToString = _toString()
    }
    return javaToString!!
  }

  protected abstract fun _toString(): String
}
