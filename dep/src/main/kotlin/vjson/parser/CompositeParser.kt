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
import vjson.Parser
import vjson.ex.JsonParseException

@Suppress("MemberVisibilityCanBePrivate")
open class CompositeParser protected constructor(private val opts: ParserOptions) {
  private var arrayParser: ArrayParser? = null
  private var boolParser: BoolParser? = null
  private var nullParser: NullParser? = null
  private var numberParser: NumberParser? = null
  private var objectParser: ObjectParser? = null
  private var stringParser: StringParser? = null
  private var keyParser: StringParser? = null

  protected fun getArrayParser(): ArrayParser {
    if (arrayParser == null) {
      arrayParser = ArrayParser(ParserUtils.subParserOptions(opts))
    } else {
      arrayParser!!.reset()
    }
    return arrayParser!!
  }

  protected fun getBoolParser(): BoolParser {
    if (boolParser == null) {
      boolParser = BoolParser(ParserUtils.subParserOptions(opts))
    } else {
      boolParser!!.reset()
    }
    return boolParser!!
  }

  protected fun getNullParser(): NullParser {
    if (nullParser == null) {
      nullParser = NullParser(ParserUtils.subParserOptions(opts))
    } else {
      nullParser!!.reset()
    }
    return nullParser!!
  }

  protected fun getNumberParser(): NumberParser {
    if (numberParser == null) {
      numberParser = NumberParser(ParserUtils.subParserOptions(opts))
    } else {
      numberParser!!.reset()
    }
    return numberParser!!
  }

  protected fun getObjectParser(): ObjectParser {
    if (objectParser == null) {
      objectParser = ObjectParser(ParserUtils.subParserOptions(opts))
    } else {
      objectParser!!.reset()
    }
    return objectParser!!
  }

  protected fun getStringParser(): StringParser {
    if (stringParser == null) {
      stringParser = StringParser(ParserUtils.subParserOptions(opts))
    } else {
      stringParser!!.reset()
    }
    return stringParser!!
  }

  protected fun getSubParser(cs: CharStream): Parser<*> {
    // the caller is responsible for cs.skipBlank() and checking cs.hasNext()
    return when (val first = cs.peekNext()) {
      '{' -> getObjectParser()
      '[' -> getArrayParser()
      '\'' -> {
        if (!opts.isStringSingleQuotes) {
          throw JsonParseException("not valid json string")
        }
        getStringParser()
      }
      '"' -> getStringParser()
      'n' -> getNullParser()
      't' -> getBoolParser()
      'f' -> getBoolParser()
      '-' -> getNumberParser()
      else -> {
        if (first in '0'..'9') {
          return getNumberParser()
        }
        throw JsonParseException("not valid json string")
      }
    }
  }

  fun getKeyParser(): StringParser {
    if (keyParser == null) {
      val opts: ParserOptions
      if (ParserOptions.isDefaultOptions(this.opts)) {
        opts = ParserOptions.DEFAULT_JAVA_OBJECT_NO_END
      } else {
        opts = ParserOptions(this.opts).setEnd(false).setMode(ParserMode.JAVA_OBJECT)
      }
      keyParser = StringParser(opts, ParserUtils.getThreadLocalKeyDictionary())
    } else {
      keyParser!!.reset()
    }
    return keyParser!!
  }
}
