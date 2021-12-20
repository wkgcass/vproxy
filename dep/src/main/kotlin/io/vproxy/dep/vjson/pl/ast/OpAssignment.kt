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

data class OpAssignment(
  val op: BinOpType,
  val variable: AssignableExpr,
  val value: Expr,
) : Expr() {
  override fun copy(): OpAssignment {
    val ret = OpAssignment(op, variable.copy(), value.copy())
    ret.lineCol = lineCol
    return ret
  }

  override fun check(ctx: TypeContext): TypeInstance {
    this.ctx = ctx
    if (op != BinOpType.PLUS && op != BinOpType.MINUS && op != BinOpType.MULTIPLY && op != BinOpType.DIVIDE && op != BinOpType.MOD) {
      throw ParserException("invalid operator for assigning: $op", lineCol)
    }
    val variableType = variable.check(ctx)
    val valueType = value.check(ctx)
    if (!TypeUtils.assignableFrom(variableType, valueType)) {
      throw ParserException("$this: cannot calculate and assign $valueType to $variableType, type mismatch", lineCol)
    }
    if (valueType !is NumericTypeInstance) {
      throw ParserException("$this: cannot execute $op on type $valueType, not numeric", lineCol)
    }
    if (op == BinOpType.MOD) {
      if (valueType !is IntType && valueType !is LongType) {
        throw ParserException("$this: cannot execute $op on type $valueType, must be int or long", lineCol)
      }
    }

    if (!variable.isModifiable()) {
      throw ParserException("$this: cannot assign values to $variable, the variable/field is unmodifiable", lineCol)
    }

    return valueType
  }

  override fun typeInstance(): TypeInstance {
    return value.typeInstance()
  }

  override fun generateInstruction(): Instruction {
    val calculateInst = when (op) {
      BinOpType.PLUS -> when (variable.typeInstance()) {
        is IntType -> PlusInt(variable.generateInstruction(), value.generateInstruction(), ctx.stackInfo(lineCol))
        is LongType -> PlusLong(variable.generateInstruction(), value.generateInstruction(), ctx.stackInfo(lineCol))
        is FloatType -> PlusFloat(variable.generateInstruction(), value.generateInstruction(), ctx.stackInfo(lineCol))
        is DoubleType -> PlusDouble(variable.generateInstruction(), value.generateInstruction(), ctx.stackInfo(lineCol))
        else -> throw IllegalStateException()
      }
      BinOpType.MINUS -> when (variable.typeInstance()) {
        is IntType -> MinusInt(variable.generateInstruction(), value.generateInstruction(), ctx.stackInfo(lineCol))
        is LongType -> MinusLong(variable.generateInstruction(), value.generateInstruction(), ctx.stackInfo(lineCol))
        is FloatType -> MinusFloat(variable.generateInstruction(), value.generateInstruction(), ctx.stackInfo(lineCol))
        is DoubleType -> MinusDouble(variable.generateInstruction(), value.generateInstruction(), ctx.stackInfo(lineCol))
        else -> throw IllegalStateException()
      }
      BinOpType.MULTIPLY -> when (variable.typeInstance()) {
        is IntType -> MultiplyInt(variable.generateInstruction(), value.generateInstruction(), ctx.stackInfo(lineCol))
        is LongType -> MultiplyLong(variable.generateInstruction(), value.generateInstruction(), ctx.stackInfo(lineCol))
        is FloatType -> MultiplyFloat(variable.generateInstruction(), value.generateInstruction(), ctx.stackInfo(lineCol))
        is DoubleType -> MultiplyDouble(variable.generateInstruction(), value.generateInstruction(), ctx.stackInfo(lineCol))
        else -> throw IllegalStateException()
      }
      BinOpType.DIVIDE -> when (variable.typeInstance()) {
        is IntType -> DivideInt(variable.generateInstruction(), value.generateInstruction(), ctx.stackInfo(lineCol))
        is LongType -> DivideLong(variable.generateInstruction(), value.generateInstruction(), ctx.stackInfo(lineCol))
        is FloatType -> DivideFloat(variable.generateInstruction(), value.generateInstruction(), ctx.stackInfo(lineCol))
        is DoubleType -> DivideDouble(variable.generateInstruction(), value.generateInstruction(), ctx.stackInfo(lineCol))
        else -> throw IllegalStateException()
      }
      BinOpType.MOD -> when (variable.typeInstance()) {
        is IntType -> ModInt(variable.generateInstruction(), value.generateInstruction(), ctx.stackInfo(lineCol))
        is LongType -> ModLong(variable.generateInstruction(), value.generateInstruction(), ctx.stackInfo(lineCol))
        else -> throw IllegalStateException()
      }
      else -> throw IllegalStateException()
    }
    return variable.generateSetInstruction(calculateInst)
  }

  override fun toString(indent: Int): String {
    return "($variable $op= $value)"
  }

  override fun toString(): String {
    return toString(0)
  }
}
