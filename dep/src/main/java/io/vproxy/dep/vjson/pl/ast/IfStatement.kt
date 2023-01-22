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
import io.vproxy.dep.vjson.pl.inst.IfInstruction
import io.vproxy.dep.vjson.pl.inst.Instruction
import io.vproxy.dep.vjson.pl.type.BoolType
import io.vproxy.dep.vjson.pl.type.TypeContext

data class IfStatement(
  val condition: Expr,
  val ifCode: List<Statement>,
  val elseCode: List<Statement>
) : Statement() {
  override fun copy(): IfStatement {
    val ret = IfStatement(condition.copy(), ifCode.map { it.copy() }, elseCode.map { it.copy() })
    ret.lineCol = lineCol
    return ret
  }

  override fun checkAST(ctx: TypeContext) {
    val conditionType = condition.check(ctx, BoolType)
    if (conditionType !is BoolType) {
      throw ParserException("$this: type of condition ($conditionType) is not bool", lineCol)
    }
    val ifCtx = TypeContext(ctx)
    ifCtx.checkStatements(ifCode)
    val elseCtx = TypeContext(ctx)
    elseCtx.checkStatements(elseCode)
  }

  @Suppress("DuplicatedCode")
  override fun functionTerminationCheck(): Boolean {
    var ifCodeTerminate = false
    for (stmt in ifCode) {
      if (stmt.functionTerminationCheck()) {
        ifCodeTerminate = true
        break
      }
    }
    if (!ifCodeTerminate) return false

    var elseCodeTerminate = false
    for (stmt in elseCode) {
      if (stmt.functionTerminationCheck()) {
        elseCodeTerminate = true
        break
      }
    }
    return elseCodeTerminate
  }

  override fun generateInstruction(): Instruction {
    val conditionInst = condition.generateInstruction()
    val ifCodeInst = ifCode.map { it.generateInstruction() }
    val elseCodeInst = elseCode.map { it.generateInstruction() }
    return IfInstruction(conditionInst, ifCodeInst, elseCodeInst)
  }

  override fun toString(indent: Int): String {
    val sb = StringBuilder()
    sb.append("if: $condition; then: {\n")
    for (stmt in ifCode) {
      sb.append(" ".repeat(indent + 2)).append(stmt.toString(indent + 2)).append("\n")
    }
    sb.append(" ".repeat(indent)).append("} else: {\n")
    for (stmt in elseCode) {
      sb.append(" ".repeat(indent + 2)).append(stmt.toString(indent + 2)).append("\n")
    }
    sb.append(" ".repeat(indent)).append("}")
    return sb.toString()
  }

  override fun toString(): String {
    return toString(0)
  }
}
