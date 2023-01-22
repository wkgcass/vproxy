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

package io.vproxy.dep.vjson.pl.type

import io.vproxy.dep.vjson.cs.LineCol
import io.vproxy.dep.vjson.ex.ParserException
import io.vproxy.dep.vjson.pl.ast.Expr
import io.vproxy.dep.vjson.pl.inst.*

object TypeUtils {
  fun assignableFrom(parent: TypeInstance, child: TypeInstance): Boolean {
    if (parent == child) return true
    if (parent !is PrimitiveTypeInstance && child is NullType) return true
    if (parent.templateType() != null && child.templateType() != null) {
      if (parent.templateType() != child.templateType()) {
        return false
      }
      val parentParams = parent.templateTypeParams()!!
      val childParams = child.templateTypeParams()!!
      for (i in parentParams.indices) {
        if (!assignableFrom(parentParams[i], childParams[i])) {
          return false
        }
      }
      return true
    }
    return false
  }

  fun checkImplicitStringCast(ctx: TypeContext, typeToStringCheck: TypeInstance, variableToStringCheck: Expr, lineCol: LineCol) {
    val toStringField = typeToStringCheck.field(ctx, "toString", ctx.getContextType())
      ?: throw ParserException(
        "$this: cannot concat string, $variableToStringCheck ($typeToStringCheck) does not have `toString` field",
        lineCol
      )
    val toStringFunc = toStringField.type.functionDescriptor(ctx)
      ?: throw ParserException(
        "$this: cannot concat string, $variableToStringCheck ($typeToStringCheck) `toString` field is not a function",
        lineCol
      )
    if (toStringFunc.params.isNotEmpty())
      throw ParserException(
        "$this: cannot concat string, $variableToStringCheck ($typeToStringCheck) `toString` function parameters list is not empty",
        lineCol
      )
    if (toStringFunc.returnType !is StringType) {
      throw ParserException(
        "$this: cannot concat string, $variableToStringCheck ($typeToStringCheck) `toString` function return type (${toStringField.type}) is not $StringType",
        lineCol
      )
    }
  }

  fun buildToStringInstruction(ctx: TypeContext, variableType: TypeInstance, getFuncInst: Instruction, lineCol: LineCol): Instruction {
    val toStringField = variableType.field(ctx, "toString", ctx.getContextType())!!
    val toStringFunc = toStringField.type.functionDescriptor(ctx)!!
    val total = toStringFunc.mem.memoryAllocator().getTotal()

    val depth = if (variableType is ClassTypeInstance) {
      variableType.cls.getMemDepth()
    } else 0

    return object : InstructionWithStackInfo(ctx.stackInfo(lineCol)) {
      override fun execute0(ctx: ActionContext, exec: Execution) {
        if (getFuncInst is FunctionInstance) {
          getFuncInst.ctxBuilder = { ActionContext(total, it) }
          getFuncInst.execute(ctx, exec)
        } else {
          getFuncInst.execute(ctx, exec)
          val func = exec.values.refValue as Instruction
          val newCtx = ActionContext(total, ctx.getContext(depth))
          func.execute(newCtx, exec)
        }
      }
    }
  }
}
