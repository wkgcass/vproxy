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
import io.vproxy.dep.vjson.pl.inst.WhileLoopInstruction
import io.vproxy.dep.vjson.pl.type.BoolType
import io.vproxy.dep.vjson.pl.type.TypeContext

data class WhileLoop(
  val condition: Expr,
  val code: List<Statement>
) : LoopStatement() {
  override fun copy(): WhileLoop {
    val ret = WhileLoop(condition.copy(), code.map { it.copy() })
    ret.lineCol = lineCol
    return ret
  }

  override fun checkAST(ctx: TypeContext) {
    val conditionType = condition.check(ctx, BoolType)
    if (conditionType !is BoolType) {
      throw ParserException("$condition ($conditionType) is not a boolean value, cannot be used as `while` loop condition", lineCol)
    }
    val loopCtx = TypeContext(ctx, ast = this)
    loopCtx.checkStatements(code)
  }

  override fun functionTerminationCheck(): Boolean {
    if (condition !is BoolLiteral || condition.b.not()) {
      return false
    }
    return this.isInfiniteLoop ?: return true
  }

  override fun generateInstruction(): Instruction {
    val conditionInst = condition.generateInstruction()
    val codeInst = code.map { it.generateInstruction() }
    return WhileLoopInstruction(conditionInst, codeInst)
  }

  override fun toString(indent: Int): String {
    val sb = StringBuilder()
    sb.append("while: ").append(condition).append("; do: {\n")
    for (stmt in code) {
      sb.append(" ".repeat(indent + 2)).append(stmt.toString(indent + 2)).append("\n")
    }
    sb.append(" ".repeat(indent)).append("}")
    return sb.toString()
  }

  override fun toString(): String {
    return toString(0)
  }
}
