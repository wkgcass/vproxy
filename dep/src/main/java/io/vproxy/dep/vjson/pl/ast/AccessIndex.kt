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

data class AccessIndex(val from: Expr, val index: Expr) : AssignableExpr() {
  override fun copy(): AccessIndex {
    val ret = AccessIndex(from.copy(), index.copy())
    ret.lineCol = lineCol
    return ret
  }

  override fun check(ctx: TypeContext, typeHint: TypeInstance?): TypeInstance {
    this.ctx = ctx
    val type = from.check(ctx, null)
    val elementType = type.elementType(ctx)
    if (elementType == null) {
      throw ParserException("$this: $elementType doesn't have elements", lineCol)
    }
    val indexType = index.check(ctx, IntType)
    if (indexType !is IntType) {
      throw ParserException("$this: typeof $index ($indexType) is not `int`", lineCol)
    }
    return elementType
  }

  override fun typeInstance(): TypeInstance {
    return from.typeInstance().elementType(ctx)!!
  }

  override fun isModifiable(): Boolean {
    return true
  }

  override fun generateInstruction(): Instruction {
    return when (typeInstance()) {
      is IntType -> GetIndexInt(from.generateInstruction(), index.generateInstruction(), ctx.stackInfo(lineCol))
      is LongType -> GetIndexLong(from.generateInstruction(), index.generateInstruction(), ctx.stackInfo(lineCol))
      is FloatType -> GetIndexFloat(from.generateInstruction(), index.generateInstruction(), ctx.stackInfo(lineCol))
      is DoubleType -> GetIndexDouble(from.generateInstruction(), index.generateInstruction(), ctx.stackInfo(lineCol))
      is BoolType -> GetIndexBool(from.generateInstruction(), index.generateInstruction(), ctx.stackInfo(lineCol))
      else -> GetIndexRef(from.generateInstruction(), index.generateInstruction(), ctx.stackInfo(lineCol))
    }
  }

  override fun generateSetInstruction(valueInst: Instruction): Instruction {
    return when (typeInstance()) {
      is IntType -> SetIndexInt(from.generateInstruction(), index.generateInstruction(), valueInst, ctx.stackInfo(lineCol))
      is LongType -> SetIndexLong(from.generateInstruction(), index.generateInstruction(), valueInst, ctx.stackInfo(lineCol))
      is FloatType -> SetIndexFloat(from.generateInstruction(), index.generateInstruction(), valueInst, ctx.stackInfo(lineCol))
      is DoubleType -> SetIndexDouble(from.generateInstruction(), index.generateInstruction(), valueInst, ctx.stackInfo(lineCol))
      is BoolType -> SetIndexBool(from.generateInstruction(), index.generateInstruction(), valueInst, ctx.stackInfo(lineCol))
      else -> SetIndexRef(from.generateInstruction(), index.generateInstruction(), valueInst, ctx.stackInfo(lineCol))
    }
  }

  override fun toString(indent: Int): String {
    return "$from[$index]"
  }

  override fun toString(): String {
    return toString(0)
  }
}
