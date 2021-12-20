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

import io.vproxy.dep.vjson.cs.CharArrayCharStream
import io.vproxy.dep.vjson.ex.JsonParseException
import io.vproxy.dep.vjson.ex.ParserFinishedException

interface Parser<T : JSON.Instance<*>> {
  /* #ifndef KOTLIN_NATIVE {{ */ @Throws(JsonParseException::class, ParserFinishedException::class) // }}
  fun build(cs: CharStream, isComplete: Boolean): T?

  /* #ifndef KOTLIN_NATIVE {{ */ @Throws(JsonParseException::class, ParserFinishedException::class) // }}
  fun buildJavaObject(cs: CharStream, isComplete: Boolean): Any?

  fun completed(): Boolean

  fun reset()

  /*#ifndef KOTLIN_NATIVE {{ */
  @Throws(JsonParseException::class, ParserFinishedException::class)
  @Suppress("DEPRECATION")
  @JvmDefault/*}}*/
  fun feed(cs: CharStream): T? {
    return build(cs, false)
  }

  /*#ifndef KOTLIN_NATIVE {{ */
  @Throws(JsonParseException::class, ParserFinishedException::class)
  @Suppress("DEPRECATION")
  @JvmDefault/*}}*/
  fun feed(cs: String): T? {
    return feed(CharStream.from(cs))
  }

  /*#ifndef KOTLIN_NATIVE {{ */
  @Throws(JsonParseException::class, ParserFinishedException::class)
  @Suppress("DEPRECATION")
  @JvmDefault/*}}*/
  fun last(cs: CharStream): T? {
    return build(cs, true)
  }

  /*#ifndef KOTLIN_NATIVE {{ */
  @Throws(JsonParseException::class, ParserFinishedException::class)
  @Suppress("DEPRECATION")
  @JvmDefault/*}}*/
  fun last(cs: String): T? {
    return last(CharStream.from(cs))
  }

  /*#ifndef KOTLIN_NATIVE {{ */
  @Throws(JsonParseException::class, ParserFinishedException::class)
  @Suppress("DEPRECATION")
  @JvmDefault/*}}*/
  fun end(): T? {
    return last(CharArrayCharStream.EMPTY)
  }
}
