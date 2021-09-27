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
import io.vproxy.dep.vjson.simple.SimpleArray
import io.vproxy.dep.vjson.util.CastUtils.cast

class ArrayParser /*#ifndef KOTLIN_NATIVE {{ */ @JvmOverloads/*}}*/ constructor(
  opts: ParserOptions = ParserOptions.DEFAULT
) : CompositeParser(opts), Parser<JSON.Array> {
  private val opts: ParserOptions = ParserOptions.ensureNotModifiedByOutside(opts)
  private var state = 0 // 0->`[`,1->first-value_or_`]`,2->`,`_or_`]`,3->value,4->finish,5->already_returned
  var list: MutableList<JSON.Instance<*>>? = null
    private set
  var javaList: MutableList<Any?>? = null
    private set
  private var subParser: Parser<*>? = null

  init {
    reset()
  }

  override fun reset() {
    state = 0
    if (opts.mode == ParserMode.JAVA_OBJECT) {
      if (opts.isNullArraysAndObjects) {
        javaList = null
      } else {
        javaList = ArrayList()
      }
    } else {
      list = ArrayList()
    }
    subParser = null
  }

  private fun handleSubParser(tryGetNewSubParser: Boolean, cs: CharStream, isComplete: Boolean) {
    try {
      if (subParser == null) {
        if (tryGetNewSubParser) {
          subParser = getSubParser(cs)
        } else {
          return
        }
      }
      if (opts.mode == ParserMode.JAVA_OBJECT) {
        val o = subParser!!.buildJavaObject(cs, isComplete)
        if (subParser!!.completed()) {
          state = 2
          if (!opts.isNullArraysAndObjects) {
            javaList!!.add(o)
          }
          subParser = null // clear the parser
          opts.listener.onArrayValueJavaObject(this, o)
        }
        // otherwise exception would be thrown or cs.hasNext() would return false
      } else {
        val inst = subParser!!.build(cs, isComplete)
        if (inst != null) {
          state = 2
          list!!.add(inst)
          subParser = null // clear the parser
          opts.listener.onArrayValue(this, inst)
        }
        // otherwise exception would be thrown or cs.hasNext() would return false
      }
    } catch (e: JsonParseException) {
      throw JsonParseException("invalid json array: failed when parsing element: (" + e.message + ")", e)
    }
  }

  private fun tryParse(cs: CharStream, isComplete: Boolean): Boolean {
    handleSubParser(false, cs, isComplete) // handle sub parser first if it exists

    var c: Char
    val err: String
    if (state == 0) {
      cs.skipBlank()
      if (cs.hasNext()) {
        opts.listener.onArrayBegin(this)
        c = cs.moveNextAndGet()
        if (c != '[') {
          err = "invalid character for json array: not starts with `[`: $c"
          throw ParserUtils.err(opts, err)
        }
        ++state
      }
    }
    if (state == 1) {
      cs.skipBlank()
      if (cs.hasNext()) {
        val peek = cs.peekNext()
        if (peek == ']') {
          cs.moveNextAndGet()
          state = 4
        } else {
          handleSubParser(true, cs, isComplete)
        }
      }
    }
    while (cs.hasNext()) {
      if (state == 2) {
        cs.skipBlank()
        if (cs.hasNext()) {
          c = cs.moveNextAndGet()
          if (c == ']') {
            state = 4
          } else if (c == ',') {
            state = 3
          } else {
            err = "invalid character for json array, expecting `]` or `,`, but got $c"
            throw ParserUtils.err(opts, err)
          }
        }
      }
      if (state == 3) {
        cs.skipBlank()
        if (cs.hasNext()) {
          handleSubParser(true, cs, isComplete)
        }
      }
      if (state == 4) {
        break
      }
      if (state == 5) {
        break
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
      err = "expecting more characters to build array"
      throw ParserUtils.err(opts, err)
    } else {
      return false
    }
  }

  @Throws(JsonParseException::class, ParserFinishedException::class)
  override fun build(cs: CharStream, isComplete: Boolean): JSON.Array? {
    if (tryParse(cs, isComplete)) {
      opts.listener.onArrayEnd(this)
      val list: List<JSON.Instance<*>> =
        if (this.list == null) emptyList() else cast(this.list)
      val ret: SimpleArray = object : SimpleArray(list, TrustedFlag.FLAG) {}
      opts.listener.onArray(ret)

      ParserUtils.checkEnd(cs, opts, "array")
      return ret
    } else {
      return null
    }
  }

  @Throws(JsonParseException::class, ParserFinishedException::class)
  override fun buildJavaObject(cs: CharStream, isComplete: Boolean): List<Any?>? {
    if (tryParse(cs, isComplete)) {
      opts.listener.onArrayEnd(this)
      opts.listener.onArray(javaList as List<Any?>?)

      ParserUtils.checkEnd(cs, opts, "array")
      return javaList
    } else {
      return null
    }
  }

  override fun completed(): Boolean {
    return state == 5
  }
}
