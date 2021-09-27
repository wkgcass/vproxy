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
// #ifndef KOTLIN_NATIVE {{
package vjson.parser

import vjson.util.StringDictionary

open class DefaultParserCacheHolder : ParserCacheHolder {
  var isStarted = false
    private set

  companion object {
    private val threadLocalArrayParser = ThreadLocal<ArrayParser>()
    private val threadLocalObjectParser = ThreadLocal<ObjectParser>()
    private val threadLocalStringParser = ThreadLocal<StringParser>()
    private val threadLocalArrayParserJavaObject = ThreadLocal<ArrayParser>()
    private val threadLocalObjectParserJavaObject = ThreadLocal<ObjectParser>()
    private val threadLocalStringParserJavaObject = ThreadLocal<StringParser>()
    private val threadLocalKeyDictionary = ThreadLocal<StringDictionary>()
  }

  override fun threadLocalArrayParser(): ArrayParser? {
    return threadLocalArrayParser.get()
  }

  override fun threadLocalArrayParser(parser: ArrayParser) {
    isStarted = true
    threadLocalArrayParser.set(parser)
  }

  override fun threadLocalObjectParser(): ObjectParser? {
    return threadLocalObjectParser.get()
  }

  override fun threadLocalObjectParser(parser: ObjectParser) {
    isStarted = true
    threadLocalObjectParser.set(parser)
  }

  override fun threadLocalStringParser(): StringParser? {
    return threadLocalStringParser.get()
  }

  override fun threadLocalStringParser(parser: StringParser) {
    isStarted = true
    threadLocalStringParser.set(parser)
  }

  override fun threadLocalArrayParserJavaObject(): ArrayParser? {
    return threadLocalArrayParserJavaObject.get()
  }

  override fun threadLocalArrayParserJavaObject(parser: ArrayParser) {
    isStarted = true
    threadLocalArrayParserJavaObject.set(parser)
  }

  override fun threadLocalObjectParserJavaObject(): ObjectParser? {
    return threadLocalObjectParserJavaObject.get()
  }

  override fun threadLocalObjectParserJavaObject(parser: ObjectParser) {
    isStarted = true
    threadLocalObjectParserJavaObject.set(parser)
  }

  override fun threadLocalStringParserJavaObject(): StringParser? {
    return threadLocalStringParserJavaObject.get()
  }

  override fun threadLocalStringParserJavaObject(parser: StringParser) {
    isStarted = true
    threadLocalStringParserJavaObject.set(parser)
  }

  override fun threadLocalKeyDictionary(): StringDictionary? {
    return threadLocalKeyDictionary.get()
  }

  override fun threadLocalKeyDictionary(dic: StringDictionary) {
    isStarted = true
    threadLocalKeyDictionary.set(dic)
  }
}
// }}
