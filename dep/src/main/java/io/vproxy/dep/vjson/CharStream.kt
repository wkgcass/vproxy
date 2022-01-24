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
import io.vproxy.dep.vjson.cs.LineCol
import io.vproxy.dep.vjson.parser.ParserUtils.isWhiteSpace

interface CharStream : MutableIterator<Char>, Iterable<Char> {
  companion object {
    /*#ifndef KOTLIN_NATIVE {{ */@JvmStatic/*}}*/
    fun from(array: CharArray): CharStream {
      return CharArrayCharStream(array)
    }

    /*#ifndef KOTLIN_NATIVE {{ */@JvmStatic/*}}*/
    fun from(string: String): CharStream {
      return from(string.toCharArray())
    }
  }

  fun hasNext(i: Int): Boolean
  fun moveNextAndGet(): Char
  fun peekNext(i: Int): Char

  /*#ifndef KOTLIN_NATIVE {{ */
  @Suppress("DEPRECATION")
  @JvmDefault/*}}*/
  fun peekNext(): Char {
    return peekNext(1)
  }

  override
  /*#ifndef KOTLIN_NATIVE {{ */
  @Suppress("DEPRECATION")
  @JvmDefault/*}}*/
  fun hasNext(): Boolean {
    return hasNext(1)
  }

  @Deprecated("")
  override
  /*#ifndef KOTLIN_NATIVE {{ */
  @Suppress("DEPRECATION")
  @JvmDefault/*}}*/
  fun next(): Char {
    return moveNextAndGet()
  }

  @Deprecated("")
  override
  /*#ifndef KOTLIN_NATIVE {{ */
  @Suppress("DEPRECATION")
  @JvmDefault/*}}*/
  fun remove() {
    throw UnsupportedOperationException()
  }

  @Deprecated("")
  override
  /*#ifndef KOTLIN_NATIVE {{ */
  @Suppress("DEPRECATION")
  @JvmDefault/*}}*/
  fun iterator(): MutableIterator<Char> {
    return this
  }

  /*#ifndef KOTLIN_NATIVE {{ */
  @Suppress("DEPRECATION")
  @JvmDefault/*}}*/
  fun skipBlank() {
    skipBlank(true)
  }

  /*#ifndef KOTLIN_NATIVE {{ */
  @Suppress("DEPRECATION")
  @JvmDefault/*}}*/
  fun skipBlank(skipComments: Boolean) {
    while (hasNext()) {
      val c = peekNext()
      if (isWhiteSpace(c)) {
        moveNextAndGet()
      } else if (skipComments) {
        if (c == '#') {
          moveNextAndGet()
          skipSingleLineComment()
        } else if (c == '/' && hasNext(2)) {
          val cc = peekNext(2)
          if (cc == '/') {
            moveNextAndGet()
            moveNextAndGet()
            skipSingleLineComment()
          } else if (cc == '*') {
            moveNextAndGet()
            moveNextAndGet()
            skipMultiLineComment()
          } else {
            break
          }
        } else {
          break
        }
      } else {
        break
      }
    }
  }

  private fun skipSingleLineComment() {
    while (hasNext()) {
      val c = moveNextAndGet()
      if (c == '\n' || c == '\r') {
        return
      }
    }
  }

  private fun skipMultiLineComment() {
    while (hasNext()) {
      val c = moveNextAndGet()
      if (c == '*' && hasNext(2)) {
        val cc = peekNext()
        if (cc == '/') {
          moveNextAndGet()
          return
        }
      }
    }
  }

  fun skip(n: Int) {
    for (i in 1..n) {
      moveNextAndGet()
    }
  }

  fun lineCol(): LineCol {
    return LineCol.EMPTY
  }
}
