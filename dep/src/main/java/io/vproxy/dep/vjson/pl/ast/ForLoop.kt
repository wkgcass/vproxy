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
import io.vproxy.dep.vjson.pl.inst.ForLoopInstruction
import io.vproxy.dep.vjson.pl.inst.Instruction
import io.vproxy.dep.vjson.pl.type.BoolType
import io.vproxy.dep.vjson.pl.type.TypeContext

data class ForLoop(
  val init: List<Statement>,
  val condition: Expr,
  val incr: List<Statement>,
  val code: List<Statement>
) : LoopStatement() {
  override fun copy(): ForLoop {
    val ret = ForLoop(init.map { it.copy() }, condition.copy(), incr.map { it.copy() }, code.map { it.copy() })
    ret.lineCol = lineCol
    return ret
  }

  override fun checkAST(ctx: TypeContext) {
    val forInitCtx = TypeContext(ctx)
    forInitCtx.checkStatements(init)

    val forConditionCtx = TypeContext(forInitCtx)
    val conditionType = condition.check(forConditionCtx, BoolType)
    if (conditionType !is BoolType) {
      throw ParserException("$condition ($conditionType) is not a boolean value, cannot be used as `for` loop condition", lineCol)
    }

    val forIncrCtx = TypeContext(forInitCtx)
    forIncrCtx.checkStatements(incr)

    val forCodeCtx = TypeContext(forInitCtx, ast = this)
    forCodeCtx.checkStatements(code)
  }

  override fun generateInstruction(): Instruction {
    val initInst = init.map { it.generateInstruction() }
    val conditionInst = condition.generateInstruction()
    val incrInst = incr.map { it.generateInstruction() }
    val codeInst = code.map { it.generateInstruction() }
    return ForLoopInstruction(initInst, conditionInst, incrInst, codeInst)
  }

  override fun functionTerminationCheck(): Boolean {
    if (condition !is BoolLiteral || condition.b.not()) {
      return false
    }
    return this.isInfiniteLoop ?: return true
  }

  @Suppress("DuplicatedCode")
  override fun toString(indent: Int): String {
    val sb = StringBuilder()
    var newLinePrinted = false
    sb.append("for: [ ")
    for ((idx, stmt) in init.withIndex()) {
      if (idx != 0) {
        sb.append(" ".repeat(indent + 2))
      }
      sb.append(stmt.toString(indent + 2))
      if (idx != init.size - 1) {
        newLinePrinted = true
        sb.append("\n")
      }
    }
    sb.append(" ; ").append(condition.toString(indent + 2)).append(" ; ")
    for ((idx, stmt) in incr.withIndex()) {
      if (idx != 0) {
        sb.append(" ".repeat(indent + 2))
      }
      sb.append(stmt.toString(indent + 2))
      if (idx != incr.size - 1) {
        newLinePrinted = true
        sb.append("\n")
      }
    }
    if (newLinePrinted) {
      sb.append(" ".repeat(indent))
    }
    sb.append(" ] do: {\n")
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
