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
import io.vproxy.dep.vjson.pl.inst.ContinueInstruction
import io.vproxy.dep.vjson.pl.inst.Instruction
import io.vproxy.dep.vjson.pl.type.TypeContext

data class ContinueStatement(val flag: String? = null) : Statement() {
  override fun copy(): ContinueStatement {
    val ret = ContinueStatement(flag)
    ret.lineCol = lineCol
    return ret
  }

  override fun checkAST(ctx: TypeContext) {
    val ctxAST = ctx.getContextAST {
      it is ClassDefinition || it is FunctionDefinition ||
        (it is LoopStatement && (flag == null || it.flag == flag))
    }
    if (ctxAST == null || ctxAST !is LoopStatement) {
      if (flag == null) {
        throw ParserException("`continue` is not in a loop, current context is $ctxAST", lineCol)
      } else {
        throw ParserException("unable to find loop $flag for `continue`", lineCol)
      }
    }
  }

  override fun generateInstruction(): Instruction {
    if (flag != null) {
      throw UnsupportedOperationException("continue with flag is not supported yet")
    }
    return ContinueInstruction(1)
  }

  override fun functionTerminationCheck(): Boolean {
    return false
  }

  override fun toString(indent: Int): String {
    return if (flag == null) {
      "continue"
    } else {
      "continue: $flag"
    }
  }

  override fun toString(): String {
    return toString(0)
  }
}
