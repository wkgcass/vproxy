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

import vjson.util.StringDictionary

interface ParserCacheHolder {
  fun threadLocalArrayParser(): ArrayParser?
  fun threadLocalArrayParser(parser: ArrayParser)
  fun threadLocalObjectParser(): ObjectParser?
  fun threadLocalObjectParser(parser: ObjectParser)
  fun threadLocalStringParser(): StringParser?
  fun threadLocalStringParser(parser: StringParser)
  fun threadLocalArrayParserJavaObject(): ArrayParser?
  fun threadLocalArrayParserJavaObject(parser: ArrayParser)
  fun threadLocalObjectParserJavaObject(): ObjectParser?
  fun threadLocalObjectParserJavaObject(parser: ObjectParser)
  fun threadLocalStringParserJavaObject(): StringParser?
  fun threadLocalStringParserJavaObject(parser: StringParser)
  fun threadLocalKeyDictionary(): StringDictionary?
  fun threadLocalKeyDictionary(dic: StringDictionary)
}
