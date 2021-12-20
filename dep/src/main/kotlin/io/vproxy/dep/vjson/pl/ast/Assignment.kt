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
import io.vproxy.dep.vjson.pl.inst.Instruction
import io.vproxy.dep.vjson.pl.type.TypeContext
import io.vproxy.dep.vjson.pl.type.TypeInstance
import io.vproxy.dep.vjson.pl.type.TypeUtils

data class Assignment(
  val variable: AssignableExpr,
  val value: Expr,
) : Expr() {
  override fun copy(): Assignment {
    val ret = Assignment(variable.copy(), value.copy())
    ret.lineCol = lineCol
    return ret
  }

  override fun check(ctx: TypeContext): TypeInstance {
    this.ctx = ctx
    val variableType = variable.check(ctx)
    val valueType = value.check(ctx)
    if (!TypeUtils.assignableFrom(variableType, valueType)) {
      throw ParserException("$this: cannot assign $valueType to $variableType, type mismatch", lineCol)
    }
    if (!variable.isModifiable()) {
      throw ParserException("$this: cannot assign value to $variable, the variable/field is unmodifiable", lineCol)
    }
    return valueType
  }

  override fun typeInstance(): TypeInstance {
    return value.typeInstance()
  }

  override fun generateInstruction(): Instruction {
    val valueInst = value.generateInstruction()
    return variable.generateSetInstruction(valueInst)
  }

  override fun toString(indent: Int): String {
    return "($variable = $value)"
  }

  override fun toString(): String {
    return toString(0)
  }
}
