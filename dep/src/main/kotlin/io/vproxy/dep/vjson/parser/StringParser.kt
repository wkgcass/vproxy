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
import io.vproxy.dep.vjson.ex.JsonParseException
import io.vproxy.dep.vjson.ex.ParserFinishedException
import io.vproxy.dep.vjson.simple.SimpleString
import io.vproxy.dep.vjson.util.StringDictionary
import io.vproxy.dep.vjson.util.TextBuilder

class StringParser constructor(opts: ParserOptions, dictionary: StringDictionary?) : Parser<JSON.String> {
  private val opts: ParserOptions = ParserOptions.ensureNotModifiedByOutside(opts)
  private var state = 0
  // 0->start`"`,
  // 1->normal or escape_begin or end`"`,
  // 2->escape_val or escape_u,
  // 3->escape_u1,
  // 4->escape_u2,
  // 5->escape_u3,
  // 6->escape_u4,
  // 7->finish
  // 8->already_returned

  val builder: TextBuilder = TextBuilder(opts.bufLen)
  private val traveler: StringDictionary.Traveler? = dictionary?.traveler()
  private var beginning = 0.toChar()
  private var u1 = -1
  private var u2 = -1
  private var u3 = -1
  // u4 can be local variable

  /*#ifndef KOTLIN_NATIVE {{ */ @JvmOverloads/*}}*/
  constructor(opts: ParserOptions = ParserOptions.DEFAULT) : this(opts, null)

  override fun reset() {
    state = 0
    builder.clear()
    traveler?.done()
    // start/u1/2/3 can keep their values
  }

  private fun parseHex(c: Char): Int {
    if (c in '0'..'9') {
      return c.toInt() - '0'.toInt()
    } else if (c in 'A'..'F') {
      return c.toInt() - ('A'.toInt() - 10)
    } else if (c in 'a'..'f') {
      return c.toInt() - ('a'.toInt() - 10)
    } else {
      return -1
    }
  }

  private fun append(c: Char) {
    traveler?.next(c) ?: builder.append(c)
    opts.listener.onStringChar(this, c)
  }

  private fun tryParse(cs: CharStream, isComplete: Boolean): Boolean {
    var c: Char
    val err: String
    if (state == 0) {
      cs.skipBlank()
      if (cs.hasNext()) {
        opts.listener.onStringBegin(this)
        c = cs.moveNextAndGet()
        if (c == '\"') {
          beginning = '\"'
        } else if (c == '\'' && opts.isStringSingleQuotes) {
          beginning = '\''
        } else {
          err = "invalid character for string: not starts with \": $c"
          throw ParserUtils.err(opts, err)
        }
        ++state
      }
    }
    while (cs.hasNext()) {
      if (state == 1) {
        c = cs.moveNextAndGet()
        if (c == '\\') {
          // escape
          state = 2
        } else if (c == beginning) {
          // end
          state = 7
          break
        } else if (c.toInt() > 31) {
          // normal
          append(c)
          continue
        } else {
          err = "invalid character in string: code is: " + c.toInt()
          throw ParserUtils.err(opts, err)
        }
      }
      if (state == 2) {
        if (cs.hasNext()) {
          c = cs.moveNextAndGet()
          when (c) {
            '\"' -> {
              append('\"')
              state = 1
            }
            '\'' -> {
              // not in json standard
              // so check if user enables stringSingleQuotes
              if (!opts.isStringSingleQuotes) {
                err = "invalid escape character: $c"
                throw ParserUtils.err(opts, err)
              }
              append('\'')
              state = 1
            }
            '\\' -> {
              append('\\')
              state = 1
            }
            '/' -> {
              append('/')
              state = 1
            }
            'b' -> {
              append('\b')
              state = 1
            }
            'f' -> {
              append('\u000C')
              state = 1
            }
            'n' -> {
              append('\n')
              state = 1
            }
            'r' -> {
              append('\r')
              state = 1
            }
            't' -> {
              append('\t')
              state = 1
            }
            'u' -> state = 3
            else -> {
              err = "invalid escape character: $c"
              throw ParserUtils.err(opts, err)
            }
          }
          if (state == 1) {
            continue
          }
        }
      }
      if (state == 3) {
        if (cs.hasNext()) {
          c = cs.moveNextAndGet()
          u1 = parseHex(c)
          if (u1 == -1) {
            err = "invalid hex character in \\u[H]HHH: $c"
            throw ParserUtils.err(opts, err)
          }
          ++state
        }
      }
      if (state == 4) {
        if (cs.hasNext()) {
          c = cs.moveNextAndGet()
          u2 = parseHex(c)
          if (u2 == -1) {
            err = "invalid hex character in \\u$u1[H]HH: $c"
            throw ParserUtils.err(opts, err)
          }
          ++state
        }
      }
      if (state == 5) {
        if (cs.hasNext()) {
          c = cs.moveNextAndGet()
          u3 = parseHex(c)
          if (u3 == -1) {
            err = "invalid hex character in \\u$u1$u2[H]H: $c"
            throw ParserUtils.err(opts, err)
          }
          ++state
        }
      }
      if (state == 6) {
        if (cs.hasNext()) {
          c = cs.moveNextAndGet()
          val u4 = parseHex(c)
          if (u4 == -1) {
            err = "invalid hex character in \\u$u1$u2$u3[H]: $c"
            throw ParserUtils.err(opts, err)
          }
          append(((u1 shl 12) or (u2 shl 8) or (u3 shl 4) or u4).toChar())
          state = 1
        }
      }
      if (state == 8) {
        break
      }
    }
    if (state == 7) {
      ++state
      return true
    } else if (state == 8) {
      cs.skipBlank()
      if (cs.hasNext()) {
        throw ParserFinishedException()
      }
      return false
    } else if (isComplete) {
      err = "expecting more characters to build string"
      throw ParserUtils.err(opts, err)
    } else {
      return false
    }
  }

  // #ifdef COVERAGE {{@lombok.Generated}}
  private fun buildResultString(): String {
    return traveler?.done() ?: builder.toString()
  }

  @Throws(JsonParseException::class, ParserFinishedException::class)
  override fun build(cs: CharStream, isComplete: Boolean): JSON.String? {
    if (tryParse(cs, isComplete)) {
      opts.listener.onStringEnd(this)
      val s = buildResultString()
      val ret = SimpleString(s)
      opts.listener.onString(ret)

      ParserUtils.checkEnd(cs, opts, "string")
      return ret
    } else {
      return null
    }
  }

  @Throws(JsonParseException::class, ParserFinishedException::class)
  override fun buildJavaObject(cs: CharStream, isComplete: Boolean): String? {
    if (tryParse(cs, isComplete)) {
      opts.listener.onStringEnd(this)
      val s = buildResultString()
      opts.listener.onString(s)

      ParserUtils.checkEnd(cs, opts, "string")
      return s
    } else {
      return null
    }
  }

  override fun completed(): Boolean {
    return state == 8
  }
}
