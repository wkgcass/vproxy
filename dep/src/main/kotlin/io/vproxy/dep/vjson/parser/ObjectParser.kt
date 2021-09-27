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
import io.vproxy.dep.vjson.simple.SimpleObject
import io.vproxy.dep.vjson.simple.SimpleObjectEntry
import io.vproxy.dep.vjson.util.StringDictionary

class ObjectParser /*#ifndef KOTLIN_NATIVE {{ */ @JvmOverloads/*}}*/ constructor(
  opts: ParserOptions = ParserOptions.DEFAULT
) : CompositeParser(opts), Parser<JSON.Object> {
  private val opts: ParserOptions = ParserOptions.ensureNotModifiedByOutside(opts)
  private var state = 0

  // 0->`{`
  // 1->first-key_or_`}`
  // 2->`:`
  // 3->value
  // 4->`,`_or_`}`
  // 5->key
  // 6->finish
  // 7->already_returned
  // 8->key unquoted
  // 9->key unquoted end
  var map: MutableList<SimpleObjectEntry<JSON.Instance<*>>>? = null
    private set
  var javaMap: LinkedHashMap<String, Any?>? = null
    private set
  private var _keyParser: StringParser? = null
  private var keyBuilder: StringDictionary.Traveler? = null
  var currentKey: String? = null
    private set
  private var valueParser: Parser<*>? = null

  init {
    reset()
  }

  override fun reset() {
    state = 0
    if (opts.mode == ParserMode.JAVA_OBJECT) {
      if (opts.isNullArraysAndObjects) {
        javaMap = null
      } else {
        if (javaMap == null) {
          javaMap = LinkedHashMap(16)
        } else {
          javaMap = LinkedHashMap(kotlin.math.max(16, javaMap!!.size))
        }
      }
    } else {
      map = ArrayList()
    }
    if (keyBuilder == null) {
      keyBuilder = ParserUtils.getThreadLocalKeyDictionary().traveler()
    }
    keyBuilder!!.done()
    _keyParser = null
    currentKey = null
    valueParser = null
  }

  fun setCurrentKey(key: String) {
    currentKey = key
  }

  private fun handleKeyParser(tryGetNewParser: Boolean, cs: CharStream, isComplete: Boolean) {
    try {
      if (_keyParser == null) {
        if (tryGetNewParser) {
          _keyParser = getKeyParser()
        } else {
          return
        }
      }
      val ret = _keyParser!!.buildJavaObject(cs, isComplete)
      if (ret != null) {
        state = 2
        currentKey = ret
        _keyParser = null
        opts.listener.onObjectKey(this, currentKey!!)
      }
      // otherwise exception would be thrown or cs.hasNext() would return false
    } catch (e: JsonParseException) {
      val err = "invalid json object: failed when parsing key: (" + e.message + ")"
      throw ParserUtils.err(opts, err)
    }
  }

  private fun handleValueParser(tryGetNewParser: Boolean, cs: CharStream, isComplete: Boolean) {
    try {
      if (valueParser == null) {
        if (tryGetNewParser) {
          valueParser = getSubParser(cs)
        } else {
          return
        }
      }
      if (opts.mode == ParserMode.JAVA_OBJECT) {
        val o = valueParser!!.buildJavaObject(cs, isComplete)
        if (valueParser!!.completed()) {
          state = 4
          val key: String = currentKey!!
          valueParser = null
          currentKey = null
          if (!opts.isNullArraysAndObjects) {
            javaMap!![key] = o
          }
          opts.listener.onObjectValueJavaObject(this, key, o)
        }
        // otherwise exception would be thrown or cs.hasNext() would return false
      } else {
        val inst = valueParser!!.build(cs, isComplete)
        if (inst != null) {
          state = 4
          val key = currentKey!!
          valueParser = null
          currentKey = null
          map!!.add(SimpleObjectEntry(key, inst))
          opts.listener.onObjectValue(this, key, inst)
        }
        // otherwise exception would be thrown or cs.hasNext() would return false
      }
    } catch (e: JsonParseException) {
      throw JsonParseException("invalid json object: failed when parsing value: (" + e.message + ")", e)
    }
  }

  private fun tryParse(cs: CharStream, isComplete: Boolean): Boolean {
    // handle sub parser first if it exists
    handleKeyParser(false, cs, isComplete)
    handleValueParser(false, cs, isComplete)

    var c: Char
    val err: String
    if (state == 0) {
      cs.skipBlank()
      if (cs.hasNext()) {
        opts.listener.onObjectBegin(this)
        c = cs.moveNextAndGet()
        if (c == '{') {
          state = 1
        } else {
          err = "invalid character for json object: not starts with `{`: $c"
          throw ParserUtils.err(opts, err)
        }
      }
    }
    if (state == 1) {
      cs.skipBlank()
      if (cs.hasNext()) {
        val peek = cs.peekNext()
        if (peek == '}') {
          cs.moveNextAndGet()
          state = 6
        } else if (peek == '"' || peek == '\'') {
          handleKeyParser(true, cs, isComplete)
        } else if (opts.isKeyNoQuotes) {
          state = 8
        } else {
          err = "invalid character for json object key: $peek"
          throw ParserUtils.err(opts, err)
        }
      }
    }
    while (cs.hasNext()) {
      if (state == 2) {
        cs.skipBlank()
        if (cs.hasNext()) {
          c = cs.moveNextAndGet()
          if (c != ':') {
            err = "invalid key-value separator for json object, expecting `:`, but got $c"
            throw ParserUtils.err(opts, err)
          }
          state = 3
        }
      }
      if (state == 3) {
        cs.skipBlank()
        if (cs.hasNext()) {
          handleValueParser(true, cs, isComplete)
        }
      }
      if (state == 4) {
        cs.skipBlank()
        if (cs.hasNext()) {
          c = cs.moveNextAndGet()
          if (c == '}') {
            state = 6
          } else if (c == ',') {
            state = 5
          } else {
            err = "invalid character for json object, expecting `}` or `,`, but got $c"
            throw ParserUtils.err(opts, err)
          }
        }
      }
      if (state == 5) {
        cs.skipBlank()
        if (cs.hasNext()) {
          val peek = cs.peekNext()
          if (peek == '\"' || peek == '\'') {
            handleKeyParser(true, cs, isComplete)
          } else if (opts.isKeyNoQuotes) {
            state = 8
          } else {
            err = "invalid character for json object key: $peek"
            throw ParserUtils.err(opts, err)
          }
        }
      }
      if (state == 8) {
        // if (cs.hasNext()) {
        // no need to check cs.hasNext()
        // the character will be checked before entering state8
        // or would already be checked in the loop condition
        val peek = cs.peekNext()
        if (peek == ':' || ParserUtils.isWhiteSpace(peek)) {
          val key = keyBuilder.toString()
          if (key.isEmpty()) {
            err = "empty key is not allowed when parsing object key without quotes"
            throw ParserUtils.err(opts, err)
          }
          state = 9
          currentKey = key
          keyBuilder!!.done()
          opts.listener.onObjectKey(this, currentKey!!)
        } else {
          c = cs.moveNextAndGet()
          if (ParserUtils.isVarName(c)) {
            keyBuilder!!.next(c)
          } else {
            err = "invalid character for json object key without quotes: $c"
            throw ParserUtils.err(opts, err)
          }
        }
      }
      if (state == 9) {
        cs.skipBlank()
        if (cs.hasNext()) {
          val peek = cs.peekNext()
          if (peek == ':') {
            state = 2
          } else {
            err = "invalid character after json object key without quotes: $peek"
            throw ParserUtils.err(opts, err)
          }
        }
      }
      if (state == 6) {
        break
      }
      if (state == 7) {
        break
      }
    }
    if (state == 6) {
      ++state
      return true
    } else if (state == 7) {
      cs.skipBlank()
      if (cs.hasNext()) {
        throw ParserFinishedException()
      }
      return false
    } else if (isComplete) {
      err = "expecting more characters to build object"
      throw ParserUtils.err(opts, err)
    } else {
      return false
    }
  }

  @Throws(JsonParseException::class, ParserFinishedException::class)
  override fun build(cs: CharStream, isComplete: Boolean): JSON.Object? {
    if (tryParse(cs, isComplete)) {
      opts.listener.onObjectEnd(this)
      val map: MutableList<SimpleObjectEntry<JSON.Instance<*>>> =
        if (this.map == null) ArrayList(0) else this.map!!
      val ret: SimpleObject = object : SimpleObject(map, TrustedFlag.FLAG) {}
      opts.listener.onObject(ret)

      ParserUtils.checkEnd(cs, opts, "object")
      return ret
    } else {
      return null
    }
  }

  @Throws(JsonParseException::class, ParserFinishedException::class)
  override fun buildJavaObject(cs: CharStream, isComplete: Boolean): Map<String, Any?>? {
    if (tryParse(cs, isComplete)) {
      opts.listener.onObjectEnd(this)
      opts.listener.onObject(javaMap as Map<String, Any?>?)

      ParserUtils.checkEnd(cs, opts, "object")
      return javaMap
    } else {
      return null
    }
  }

  override fun completed(): Boolean {
    return state == 7
  }
}
