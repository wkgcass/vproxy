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
package io.vproxy.dep.vjson.parser

import io.vproxy.dep.vjson.CharStream
import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.Parser
import io.vproxy.dep.vjson.cs.LineCol
import io.vproxy.dep.vjson.ex.JsonParseException
import io.vproxy.dep.vjson.ex.ParserFinishedException
import io.vproxy.dep.vjson.simple.SimpleNull

class NullParser /*#ifndef KOTLIN_NATIVE {{ */ @JvmOverloads/*}}*/ constructor(
  opts: ParserOptions = ParserOptions.DEFAULT
) : Parser<JSON.Null> {
  private val opts: ParserOptions = ParserOptions.ensureNotModifiedByOutside(opts)
  private var state = 0 // 0->[n]ull,1->n[u]ll,2->nu[l]l,3->nul[l],4->finish,5->already_returned
  private var nullLineCol = LineCol.EMPTY

  override fun reset() {
    state = 0
    nullLineCol = LineCol.EMPTY
  }

  private fun tryParse(cs: CharStream, isComplete: Boolean): Boolean {
    var c: Char
    val err: String
    if (state == 0) {
      cs.skipBlank()
      if (cs.hasNext()) {
        nullLineCol = cs.lineCol()
        opts.listener.onNullBegin(this)
        c = cs.moveNextAndGet()
        if (c != 'n') {
          err = "invalid character for `[n]ull`: $c"
          throw ParserUtils.err(cs, opts, err)
        }
        ++state
      }
    }
    if (state == 1) {
      if (cs.hasNext()) {
        c = cs.moveNextAndGet()
        if (c != 'u') {
          err = "invalid character for `n[u]ll`: $c"
          throw ParserUtils.err(cs, opts, err)
        }
        ++state
      }
    }
    if (state == 2) {
      if (cs.hasNext()) {
        c = cs.moveNextAndGet()
        if (c != 'l') {
          err = "invalid character for `nu[l]l`: $c"
          throw ParserUtils.err(cs, opts, err)
        }
        ++state
      }
    }
    if (state == 3) {
      if (cs.hasNext()) {
        c = cs.moveNextAndGet()
        if (c != 'l') {
          err = "invalid character for `nul[l]`: $c"
          throw ParserUtils.err(cs, opts, err)
        }
        ++state
      }
    }
    if (state == 4) {
      ++state
      return true
    } else if (state == 5) {
      cs.skipBlank()
      if (cs.hasNext()) {
        throw ParserFinishedException()
      }
      return false
    } else if (isComplete) {
      err = "expecting more characters to build `null`"
      throw ParserUtils.err(cs, opts, err)
    } else {
      return false // expecting more data
    }
  }

  /* #ifndef KOTLIN_NATIVE {{ */ @Throws(JsonParseException::class, ParserFinishedException::class) // }}
  override fun build(cs: CharStream, isComplete: Boolean): JSON.Null? {
    if (tryParse(cs, isComplete)) {
      opts.listener.onNullEnd(this)
      val n = SimpleNull(nullLineCol)
      opts.listener.onNull(n)

      ParserUtils.checkEnd(cs, opts, "`null`")
      return n
    } else {
      return null
    }
  }

  /* #ifndef KOTLIN_NATIVE {{ */ @Throws(JsonParseException::class, ParserFinishedException::class) // }}
  override fun buildJavaObject(cs: CharStream, isComplete: Boolean): Any? {
    if (tryParse(cs, isComplete)) {
      opts.listener.onNullEnd(this)
      opts.listener.onNull(null as Unit?)

      ParserUtils.checkEnd(cs, opts, "`null`")
      return null
    } else {
      return null
    }
  }

  override fun completed(): Boolean {
    return state == 5
  }
}
