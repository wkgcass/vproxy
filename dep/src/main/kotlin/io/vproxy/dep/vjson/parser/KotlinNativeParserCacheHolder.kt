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

import io.vproxy.dep.vjson.util.StringDictionary

class KotlinNativeParserCacheHolder : ParserCacheHolder {
  /* #ifdef KOTLIN_JS {{ // }} */ /* #ifdef KOTLIN_NATIVE {{@ThreadLocal}}*/
  companion object {
    private var arrayParser: ArrayParser? = null
    private var objectParser: ObjectParser? = null
    private var stringParser: StringParser? = null
    private var arrayParser4j: ArrayParser? = null
    private var objectParser4j: ObjectParser? = null
    private var stringParser4j: StringParser? = null
    private var keyDictionary: StringDictionary? = null
  }

  override fun threadLocalArrayParser(): ArrayParser? {
    return arrayParser
  }

  override fun threadLocalArrayParser(parser: ArrayParser) {
    arrayParser = parser
  }

  override fun threadLocalObjectParser(): ObjectParser? {
    return objectParser
  }

  override fun threadLocalObjectParser(parser: ObjectParser) {
    objectParser = parser
  }

  override fun threadLocalStringParser(): StringParser? {
    return stringParser
  }

  override fun threadLocalStringParser(parser: StringParser) {
    stringParser = parser
  }

  override fun threadLocalArrayParserJavaObject(): ArrayParser? {
    return arrayParser4j
  }

  override fun threadLocalArrayParserJavaObject(parser: ArrayParser) {
    arrayParser4j = parser
  }

  override fun threadLocalObjectParserJavaObject(): ObjectParser? {
    return objectParser4j
  }

  override fun threadLocalObjectParserJavaObject(parser: ObjectParser) {
    objectParser4j = parser
  }

  override fun threadLocalStringParserJavaObject(): StringParser? {
    return stringParser4j
  }

  override fun threadLocalStringParserJavaObject(parser: StringParser) {
    stringParser4j = parser
  }

  override fun threadLocalKeyDictionary(): StringDictionary? {
    return keyDictionary
  }

  override fun threadLocalKeyDictionary(dic: StringDictionary) {
    keyDictionary = dic
  }
}
