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
package io.vproxy.dep.vjson.stringifier

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.Stringifier

@Suppress("MemberVisibilityCanBePrivate")
open class PrettyStringifier : Stringifier {
  /*#ifndef KOTLIN_NATIVE {{ */@JvmField/*}}*/
  protected var indent = 0

  protected fun indentIncr(): Int {
    return 4
  }

  protected fun increaseIndent() {
    indent += indentIncr()
  }

  protected fun decreaseIndent() {
    indent -= indentIncr()
  }

  protected fun endl(): String {
    return "\n"
  }

  protected fun ws(): String {
    return " "
  }

  protected fun writeNewline(sb: StringBuilder) {
    sb.append(endl())
  }

  protected fun writeIndent(sb: StringBuilder) {
    for (i in 0 until indent) {
      sb.append(ws())
    }
  }

  override fun beforeObjectBegin(sb: StringBuilder, obj: JSON.Object) {}

  override fun afterObjectBegin(sb: StringBuilder, obj: JSON.Object) {
    if (obj.size() > 1) {
      // {
      //     ...
      // }
      writeNewline(sb)
      increaseIndent()
    } else if (obj.size() > 0) {
      // { ... }
      sb.append(ws())
    } // {}
  }

  override fun beforeObjectKey(sb: StringBuilder, obj: JSON.Object, key: String) {
    if (obj.size() > 1) {
      writeIndent(sb)
    }
  }

  override fun afterObjectKey(sb: StringBuilder, obj: JSON.Object, key: String) {}

  override fun beforeObjectColon(sb: StringBuilder, obj: JSON.Object) {}

  override fun afterObjectColon(sb: StringBuilder, obj: JSON.Object) {}

  override fun beforeObjectValue(sb: StringBuilder, obj: JSON.Object, key: String, value: JSON.Instance<*>) {
    sb.append(ws())
  }

  override fun afterObjectValue(sb: StringBuilder, obj: JSON.Object, key: String, value: JSON.Instance<*>) {}

  override fun beforeObjectComma(sb: StringBuilder, obj: JSON.Object) {}

  override fun afterObjectComma(sb: StringBuilder, obj: JSON.Object) {
    writeNewline(sb)
  }

  override fun beforeObjectEnd(sb: StringBuilder, obj: JSON.Object) {
    if (obj.size() > 1) {
      // {
      //     ...
      // }
      decreaseIndent()
      writeNewline(sb)
      writeIndent(sb)
    } else if (obj.size() > 0) {
      // { ... }
      sb.append(ws())
    } // {}
  }

  override fun afterObjectEnd(sb: StringBuilder, obj: JSON.Object) {}
  override fun beforeArrayBegin(sb: StringBuilder, array: JSON.Array) {}
  override fun afterArrayBegin(sb: StringBuilder, array: JSON.Array) {
    if (array.length() > 1) {
      // [
      //     ...
      // ]
      writeNewline(sb)
      increaseIndent()
    } else if (array.length() > 0) {
      // [ ... ]
      sb.append(ws())
    } // []
  }

  override fun beforeArrayValue(sb: StringBuilder, array: JSON.Array, value: JSON.Instance<*>) {
    if (array.length() > 1) {
      writeIndent(sb)
    }
  }

  override fun afterArrayValue(sb: StringBuilder, array: JSON.Array, value: JSON.Instance<*>) {}
  override fun beforeArrayComma(sb: StringBuilder, array: JSON.Array) {}
  override fun afterArrayComma(sb: StringBuilder, array: JSON.Array) {
    writeNewline(sb)
  }

  override fun beforeArrayEnd(sb: StringBuilder, array: JSON.Array) {
    if (array.length() > 1) {
      // [
      //     ...
      // ]
      decreaseIndent()
      writeNewline(sb)
      writeIndent(sb)
    } else if (array.length() > 0) {
      // [ ... ]
      sb.append(ws())
    } // []
  }

  override fun afterArrayEnd(sb: StringBuilder, array: JSON.Array) {}
}
