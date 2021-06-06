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
package vjson.parser

import vjson.ParserListener
import vjson.listener.EmptyParserListener

class ParserOptions {
  companion object {
    /*#ifndef KOTLIN_NATIVE {{ */@JvmField/*}}*/
    val DEFAULT = ParserOptions()

    /*#ifndef KOTLIN_NATIVE {{ */@JvmField/*}}*/
    val DEFAULT_NO_END = ParserOptions().setEnd(false)

    /*#ifndef KOTLIN_NATIVE {{ */@JvmField/*}}*/
    val DEFAULT_JAVA_OBJECT = ParserOptions().setMode(ParserMode.JAVA_OBJECT)

    /*#ifndef KOTLIN_NATIVE {{ */@JvmField/*}}*/
    val DEFAULT_JAVA_OBJECT_NO_END = ParserOptions().setMode(ParserMode.JAVA_OBJECT).setEnd(false)

    /*#ifndef KOTLIN_NATIVE {{ */@JvmStatic/*}}*/
    fun isDefaultOptions(opts: ParserOptions): Boolean {
      return opts === DEFAULT || opts === DEFAULT_NO_END || opts === DEFAULT_JAVA_OBJECT || opts === DEFAULT_JAVA_OBJECT_NO_END
    }

    /*#ifndef KOTLIN_NATIVE {{ */@JvmStatic/*}}*/
    fun ensureNotModifiedByOutside(opts: ParserOptions): ParserOptions {
      return if (isDefaultOptions(opts)) opts else ParserOptions(opts)
    }
  }

  var bufLen: Int
    private set
  var isEnd: Boolean
    private set
  var mode: ParserMode
    private set
  var listener: ParserListener
    private set

  // features
  var isStringSingleQuotes: Boolean
    private set
  var isKeyNoQuotes: Boolean
    private set
  var isNullArraysAndObjects: Boolean
    private set

  constructor() {
    bufLen = 256
    isEnd = true
    mode = ParserMode.DEFAULT
    listener = EmptyParserListener.INSTANCE

    // features
    isStringSingleQuotes = false
    isKeyNoQuotes = false
    isNullArraysAndObjects = false
  }

  constructor(opts: ParserOptions) {
    bufLen = opts.bufLen
    isEnd = opts.isEnd
    mode = opts.mode
    listener = opts.listener

    // features
    isStringSingleQuotes = opts.isStringSingleQuotes
    isKeyNoQuotes = opts.isKeyNoQuotes
    isNullArraysAndObjects = opts.isNullArraysAndObjects
  }

  fun setBufLen(bufLen: Int): ParserOptions {
    this.bufLen = bufLen
    return this
  }

  fun setEnd(end: Boolean): ParserOptions {
    isEnd = end
    return this
  }

  fun setMode(mode: ParserMode): ParserOptions {
    this.mode = mode
    return this
  }

  fun setListener(listener: ParserListener?): ParserOptions {
    var listener0 = listener
    if (listener0 == null) {
      listener0 = EmptyParserListener.INSTANCE
    }
    this.listener = listener0
    return this
  }

  // ============
  // features
  // ============

  fun setStringSingleQuotes(stringSingleQuotes: Boolean): ParserOptions {
    isStringSingleQuotes = stringSingleQuotes
    return this
  }

  fun setKeyNoQuotes(keyNoQuotes: Boolean): ParserOptions {
    isKeyNoQuotes = keyNoQuotes
    return this
  }

  fun setNullArraysAndObjects(nullArraysAndObjects: Boolean): ParserOptions {
    isNullArraysAndObjects = nullArraysAndObjects
    return this
  }
}
