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

package io.vproxy.dep.vjson.pl

import io.vproxy.dep.vjson.CharStream
import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.cs.IncludeCharStream
import io.vproxy.dep.vjson.cs.LineColCharStream
import io.vproxy.dep.vjson.parser.ObjectParser
import io.vproxy.dep.vjson.parser.ParserOptions
import io.vproxy.dep.vjson.pl.ast.Statement
import io.vproxy.dep.vjson.pl.type.lang.Types
import io.vproxy.dep.vjson.util.Manager

class InterpreterBuilder {
  private val types: MutableList<Types> = ArrayList()

  companion object {
    fun interpreterOptions(): ParserOptions = ParserOptions()
      .setStringSingleQuotes(true)
      .setKeyNoQuotes(true)
      .setKeyNoQuotesAnyChar(true)
      .setAllowSkippingCommas(true)
      .setAllowObjectEntryWithoutValue(true)
      .setAllowOmittingColonBeforeBraces(true)
      .setEqualAsColon(true)
      .setSemicolonAsComma(true)
      .setStringValueNoQuotes(true)
  }

  fun addTypes(types: Types): InterpreterBuilder {
    this.types.add(types)
    return this
  }

  /*#ifndef KOTLIN_NATIVE {{ */ @JvmOverloads/*}}*/
  fun compile(prog: String, filename: String = ""): Interpreter {
    val jsonParser = ObjectParser(interpreterOptions())
    val json = jsonParser.last(LineColCharStream(CharStream.from(prog), filename))!!
    return compile(json)
  }

  fun compile(json: JSON.Object): Interpreter {
    val astGen = ASTGen(json)
    return interpreter(astGen.parse())
  }

  fun compile(prog: Manager<String>, mainName: String): Interpreter {
    val jsonParser = ObjectParser(interpreterOptions())
    val json = jsonParser.last(IncludeCharStream(prog, mainName))!!
    return compile(json)
  }

  fun interpreter(ast: List<Statement>): Interpreter {
    return Interpreter(types, ast)
  }
}
