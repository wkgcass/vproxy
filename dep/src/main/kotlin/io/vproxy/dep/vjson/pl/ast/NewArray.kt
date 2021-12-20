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

import io.vproxy.dep.vjson.ex.ParserException
import io.vproxy.dep.vjson.pl.inst.*
import io.vproxy.dep.vjson.pl.type.*

data class NewArray(
  val type: Type,
  val len: Expr,
) : Expr() {
  override fun copy(): NewArray {
    val ret = NewArray(type.copy(), len.copy())
    ret.lineCol = lineCol
    return ret
  }

  override fun check(ctx: TypeContext): TypeInstance {
    this.ctx = ctx
    val arrayType = type.check(ctx)
    if (arrayType !is ArrayTypeInstance) {
      throw ParserException("$this: $arrayType is not array type", lineCol)
    }
    val lenType = len.check(ctx)
    if (lenType !is IntType) {
      throw ParserException("$this: typeof $len ($lenType) is not int", lineCol)
    }
    return arrayType
  }

  override fun typeInstance(): TypeInstance {
    return type.typeInstance()
  }

  override fun generateInstruction(): Instruction {
    return when (type.typeInstance().elementType(ctx)) {
      is IntType -> NewArrayInt(len.generateInstruction(), ctx.stackInfo(lineCol))
      is LongType -> NewArrayLong(len.generateInstruction(), ctx.stackInfo(lineCol))
      is FloatType -> NewArrayFloat(len.generateInstruction(), ctx.stackInfo(lineCol))
      is DoubleType -> NewArrayDouble(len.generateInstruction(), ctx.stackInfo(lineCol))
      is BoolType -> NewArrayBool(len.generateInstruction(), ctx.stackInfo(lineCol))
      else -> NewArrayRef(len.generateInstruction(), ctx.stackInfo(lineCol))
    }
  }

  override fun toString(indent: Int): String {
    val typeStr = type.toString()
    val bracketLeft = typeStr.indexOf("[")
    val bracketRight = typeStr.indexOf("]", bracketLeft + 1)
    return "new ${typeStr.substring(0, bracketLeft + 1)}$len${typeStr.substring(bracketRight)}"
  }

  override fun toString(): String {
    return toString(0)
  }
}
