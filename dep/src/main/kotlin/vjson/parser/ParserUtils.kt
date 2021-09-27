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

import vjson.CharStream
import vjson.JSON
import vjson.Parser
import vjson.ex.JsonParseException
import vjson.util.CastUtils.cast
import vjson.util.StringDictionary

object ParserUtils {
  /* #ifdef KOTLIN_NATIVE {{
  private val holder: ParserCacheHolder = KotlinNativeParserCacheHolder()
  fun setParserCacheHolder(@Suppress("UNUSED_PARAMETER")parserCacheHolder: ParserCacheHolder) {} // do nothing
  }} else {{*/
  private var holder: ParserCacheHolder = DefaultParserCacheHolder()

  @Throws(IllegalStateException::class)
  @JvmStatic
  fun setParserCacheHolder(parserCacheHolder: ParserCacheHolder) {
    check((holder is DefaultParserCacheHolder && !(cast<DefaultParserCacheHolder>(holder).isStarted))) {
      "parser cache holder already set"
    }
    holder = parserCacheHolder
  }
  // }}

  /*#ifndef KOTLIN_NATIVE {{ */@JvmStatic/*}}*/
  fun getThreadLocalKeyDictionary(): StringDictionary {
    var dic = holder.threadLocalKeyDictionary()
    if (dic == null) {
      dic = StringDictionary(16)
      holder.threadLocalKeyDictionary(dic)
    }
    return dic
  }

  /*#ifndef KOTLIN_NATIVE {{ */@JvmStatic/*}}*/
  fun isWhiteSpace(c: Char): Boolean {
    return c == '\n' || c == '\r' || c == ' ' || c == '\t'
  }

  /*#ifndef KOTLIN_NATIVE {{ */@JvmStatic/*}}*/
  fun isInitialVarName(c: Char): Boolean {
    return c in 'a'..'z' || c in 'A'..'Z' || c == '_' || c == '$'
  }

  /*#ifndef KOTLIN_NATIVE {{ */@JvmStatic/*}}*/
  fun isVarName(c: Char): Boolean {
    return isInitialVarName(c) || c in '0'..'9'
  }

  internal
  /*#ifndef KOTLIN_NATIVE {{ */@JvmStatic/*}}*/
  fun checkEnd(cs: CharStream, opts: ParserOptions, type: String) {
    if (opts.isEnd) {
      cs.skipBlank()
      if (cs.hasNext()) {
        val err = "input stream contain extra characters other than $type"
        opts.listener.onError(err)
        throw JsonParseException(err)
      }
    }
  }

  internal
  /*#ifndef KOTLIN_NATIVE {{ */@JvmStatic/*}}*/
  fun err(opts: ParserOptions, msg: String): JsonParseException {
    opts.listener.onError(msg)
    return JsonParseException(msg)
  }

  internal
  /*#ifndef KOTLIN_NATIVE {{ */@JvmStatic/*}}*/
  fun subParserOptions(opts: ParserOptions): ParserOptions {
    if (opts == ParserOptions.DEFAULT || opts == ParserOptions.DEFAULT_NO_END) {
      return ParserOptions.DEFAULT_NO_END
    }
    if (opts == ParserOptions.DEFAULT_JAVA_OBJECT || opts == ParserOptions.DEFAULT_JAVA_OBJECT_NO_END) {
      return ParserOptions.DEFAULT_JAVA_OBJECT_NO_END
    }
    if (!opts.isEnd) {
      return opts
    }
    return ParserOptions(opts).setEnd(false)
  }

  @Throws(JsonParseException::class)
  /*#ifndef KOTLIN_NATIVE {{ */
  @JvmStatic/*}}*/
  fun buildFrom(cs: CharStream): JSON.Instance<*> {
    val opts = ParserOptions.DEFAULT
    cs.skipBlank()
    if (!cs.hasNext()) {
      throw JsonParseException("empty input string")
    }
    when (cs.peekNext()) {
      '{' -> {
        var p = holder.threadLocalObjectParser()
        if (p == null) {
          p = ObjectParser(opts)
          holder.threadLocalObjectParser(p)
        }
        val ret: JSON.Object
        try {
          ret = p.last(cs)!!
        } finally {
          p.reset()
        }
        return ret
      }
      '[' -> {
        var p = holder.threadLocalArrayParser()
        if (p == null) {
          p = ArrayParser(opts)
          holder.threadLocalArrayParser(p)
        }
        val ret: JSON.Array
        try {
          ret = p.last(cs)!!
        } finally {
          p.reset()
        }
        return ret
      }
      '\'' -> throw JsonParseException("not valid json string: stringSingleQuotes not enabled")
      '"' -> {
        var p = holder.threadLocalStringParser()
        if (p == null) {
          p = StringParser(opts)
          holder.threadLocalStringParser(p)
        }
        val ret: JSON.String
        try {
          ret = p.last(cs)!!
        } finally {
          p.reset()
        }
        return ret
      }
      else -> {
        return build(cs, opts)
      }
    }
  }

  @Throws(JsonParseException::class)
  /*#ifndef KOTLIN_NATIVE {{ */
  @JvmStatic/*}}*/
  fun buildFrom(cs: CharStream, opts: ParserOptions): JSON.Instance<*> {
    return build(cs, opts)
  }

  @Throws(JsonParseException::class)
  /*#ifndef KOTLIN_NATIVE {{ */
  @JvmStatic/*}}*/
  fun buildJavaObject(cs: CharStream): Any? {
    val opts = ParserOptions.DEFAULT_JAVA_OBJECT
    cs.skipBlank()
    if (!cs.hasNext()) {
      throw JsonParseException("empty input string")
    }
    when (cs.peekNext()) {
      '{' -> {
        var p = holder.threadLocalObjectParserJavaObject()
        if (p == null) {
          p = ObjectParser(opts)
          holder.threadLocalObjectParserJavaObject(p)
        }
        val ret: Map<*, *>
        try {
          ret = p.buildJavaObject(cs, true)!!
        } finally {
          p.reset()
        }
        return ret
      }
      '[' -> {
        var p = holder.threadLocalArrayParserJavaObject()
        if (p == null) {
          p = ArrayParser(opts)
          holder.threadLocalArrayParserJavaObject(p)
        }
        val ret: List<*>
        try {
          ret = p.buildJavaObject(cs, true)!!
        } finally {
          p.reset()
        }
        return ret
      }
      '\'' -> throw JsonParseException("not valid json string: stringSingleQuotes not enabled")
      '"' -> {
        var p = holder.threadLocalStringParserJavaObject()
        if (p == null) {
          p = StringParser(opts)
          holder.threadLocalStringParserJavaObject(p)
        }
        val ret: String
        try {
          ret = p.buildJavaObject(cs, true)!!
        } finally {
          p.reset()
        }
        return ret
      }
      else -> return buildJ(cs, opts)
    }
  }

  @Throws(JsonParseException::class)
  /*#ifndef KOTLIN_NATIVE {{ */
  @JvmStatic/*}}*/
  fun buildJavaObject(cs: CharStream, opts: ParserOptions): Any? {
    return buildJ(cs, opts)
  }

  @Throws(JsonParseException::class)
  private fun parser(cs: CharStream, opts: ParserOptions): Parser<*> {
    cs.skipBlank()
    if (!cs.hasNext()) {
      throw JsonParseException("empty input string")
    }
    return when (val first = cs.peekNext()) {
      '{' -> ObjectParser(opts)
      '[' -> ArrayParser(opts)
      '\'' -> {
        if (!opts.isStringSingleQuotes) {
          throw JsonParseException("not valid json string: stringSingleQuotes not enabled")
        }
        return StringParser(opts)
      }
      '"' -> StringParser(opts)
      'n' -> NullParser(opts)
      't' -> BoolParser(opts)
      'f' -> BoolParser(opts)
      '-' -> NumberParser(opts)
      else -> {
        if (first in '0'..'9') {
          return NumberParser(opts)
        }
        throw JsonParseException("not valid json string")
      }
    }
  }

  @Throws(IllegalArgumentException::class, JsonParseException::class)
  private fun build(cs: CharStream, opts: ParserOptions): JSON.Instance<*> {
    return parser(cs, opts).build(cs, true)!!
  }

  @Throws(IllegalArgumentException::class, JsonParseException::class)
  private fun buildJ(cs: CharStream, opts: ParserOptions): Any? {
    opts.setMode(ParserMode.JAVA_OBJECT)
    return parser(cs, opts).buildJavaObject(cs, true)
  }
}
