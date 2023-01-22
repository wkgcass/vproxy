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

package io.vproxy.dep.vjson.pl.ast

import io.vproxy.dep.vjson.cs.LineCol
import io.vproxy.dep.vjson.ex.ParserException
import io.vproxy.dep.vjson.pl.inst.Instruction
import io.vproxy.dep.vjson.pl.type.TypeContext
import io.vproxy.dep.vjson.pl.type.TypeInstance

data class Param /*#ifndef KOTLIN_NATIVE {{ */ @JvmOverloads/*}}*/ constructor(
  val name: String,
  val type: Type,
  val defaultValue: Expr? = null,
) : TypedAST {
  override var lineCol: LineCol = LineCol.EMPTY

  override fun copy(): Param {
    val ret = Param(name, type, defaultValue)
    ret.lineCol = lineCol
    return ret
  }

  override fun check(ctx: TypeContext, typeHint: TypeInstance?): TypeInstance {
    if (!ctx.hasTypeConsiderArray(type)) {
      throw ParserException("type of parameter $name (${type}) is not defined", lineCol)
    }
    val typeInstance = type.check(ctx, typeHint)
    if (defaultValue != null) {
      if (defaultValue !is IntegerLiteral &&
        defaultValue !is FloatLiteral &&
        defaultValue !is BoolLiteral &&
        defaultValue !is StringLiteral &&
        defaultValue !is NullLiteral
      ) {
        throw ParserException("default value can only be literals or null", defaultValue.lineCol)
      }
      val defaultValueType = defaultValue.check(ctx, typeInstance)
      if (typeInstance != defaultValueType) {
        throw ParserException("default value $defaultValue ($defaultValueType) cannot be assigned to $typeInstance", defaultValue.lineCol)
      }
    }
    return typeInstance
  }

  override fun typeInstance(): TypeInstance {
    return type.typeInstance()
  }

  override fun generateInstruction(): Instruction {
    throw UnsupportedOperationException()
  }

  internal var memIndex: Int = -1

  override fun toString(indent: Int): String {
    return "$name: $type"
  }

  override fun toString(): String {
    return toString(0)
  }
}
