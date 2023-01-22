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

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.pl.inst.Instruction
import io.vproxy.dep.vjson.pl.inst.LiteralDouble
import io.vproxy.dep.vjson.pl.inst.LiteralFloat
import io.vproxy.dep.vjson.pl.type.DoubleType
import io.vproxy.dep.vjson.pl.type.FloatType
import io.vproxy.dep.vjson.pl.type.TypeContext
import io.vproxy.dep.vjson.pl.type.TypeInstance

data class FloatLiteral(val n: JSON.Double) : Expr() {
  override fun copy(): FloatLiteral {
    val ret = FloatLiteral(n)
    ret.lineCol = lineCol
    return ret
  }

  override fun check(ctx: TypeContext, typeHint: TypeInstance?): TypeInstance {
    this.ctx = ctx
    this.typeHint = typeHint
    if (typeHint is FloatType) return FloatType
    return DoubleType
  }

  override fun typeInstance(): TypeInstance {
    if (typeHint is FloatType) return FloatType
    return DoubleType
  }

  override fun generateInstruction(): Instruction {
    if (typeInstance() is FloatType)
      return LiteralFloat(n.doubleValue().toFloat(), ctx.stackInfo(lineCol))
    return LiteralDouble(n.doubleValue(), ctx.stackInfo(lineCol))
  }

  override fun toString(indent: Int): String {
    return "" + n.stringify()
  }

  override fun toString(): String {
    return toString(0)
  }
}
