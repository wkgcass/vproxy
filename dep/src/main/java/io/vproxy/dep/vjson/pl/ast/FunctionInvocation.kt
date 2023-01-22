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

import io.vproxy.dep.vjson.cs.LineCol
import io.vproxy.dep.vjson.ex.ParserException
import io.vproxy.dep.vjson.pl.inst.*
import io.vproxy.dep.vjson.pl.type.*

data class FunctionInvocation(
  val target: Expr,
  val args: List<Expr>
) : Expr() {
  override fun copy(): FunctionInvocation {
    val ret = FunctionInvocation(target.copy(), args.map { it.copy() })
    ret.lineCol = lineCol
    return ret
  }

  override fun check(ctx: TypeContext, typeHint: TypeInstance?): TypeInstance {
    this.ctx = ctx
    val targetType = target.check(ctx, null)
    val func = targetType.functionDescriptor(ctx)
      ?: throw ParserException("$this: unable to invoke $target, which is not a functional object", lineCol)
    if (func.params.size != args.size) {
      throw ParserException(
        "$this: unable to invoke $target with $args, arguments count (${args.size}) parameters count (${func.params.size}) mismatch",
        lineCol
      )
    }
    for (idx in args.indices) {
      val argType = args[idx].check(ctx, func.params[idx].type)
      val paramType = func.params[idx]
      if (!TypeUtils.assignableFrom(paramType.type, argType)) {
        throw ParserException(
          "$this: unable to invoke $target with $args, args[$idx] $argType does not match params[$idx] $paramType",
          lineCol
        )
      }
    }
    return func.returnType
  }

  override fun typeInstance(): TypeInstance {
    return target.typeInstance().functionDescriptor(ctx)!!.returnType
  }

  override fun generateInstruction(): Instruction {
    return buildFunctionInvocationInstruction(ctx, this, args.map { it.generateInstruction() })
  }

  override fun toString(indent: Int): String {
    return "($target:$args)"
  }

  override fun toString(): String {
    return toString(0)
  }

  companion object {
    fun buildFunctionInvocationInstruction(ctx: TypeContext, func: FunctionInvocation, args: List<Instruction>): Instruction {
      val funcDesc = func.target.typeInstance().functionDescriptor(ctx)!!
      val funcInst = func.target.generateInstruction()
      return invokeFunction(ctx, funcDesc, funcInst, args, func.lineCol)
    }

    fun invokeFunction(ctx: TypeContext, funcDesc: FunctionDescriptor, funcInst: Instruction, args: List<Instruction>, lineCol: LineCol):
      Instruction {
      return object : InstructionWithStackInfo(ctx.stackInfo(lineCol)) {
        override fun execute0(ctx: ActionContext, exec: Execution) {
          if (funcInst is FunctionInstance) {
            funcInst.ctxBuilder = { buildContext(ctx, it, exec, funcDesc, args) }
            funcInst.execute(ctx, exec)
          } else {
            funcInst.execute(ctx, exec)
            val funcValue = exec.values.refValue as Instruction
            val newCtx = buildContext(ctx, ctx, exec, funcDesc, args)
            funcValue.execute(newCtx, exec)
          }
        }
      }
    }

    fun buildContext(
      callerCtx: ActionContext,
      ctx: ActionContext,
      exec: Execution,
      funcDesc: FunctionDescriptor,
      args: List<Instruction>
    ): ActionContext {
      val newCtx = ActionContext(funcDesc.mem.memoryAllocator().getTotal(), ctx)
      val newMem = newCtx.getCurrentMem()

      for (i in args.indices) {
        args[i].execute(callerCtx, exec)
        val param = funcDesc.params[i]
        when (param.type) {
          is IntType -> newMem.setInt(param.memIndex, exec.values.intValue)
          is LongType -> newMem.setLong(param.memIndex, exec.values.longValue)
          is FloatType -> newMem.setFloat(param.memIndex, exec.values.floatValue)
          is DoubleType -> newMem.setDouble(param.memIndex, exec.values.doubleValue)
          is BoolType -> newMem.setBool(param.memIndex, exec.values.boolValue)
          else -> newMem.setRef(param.memIndex, exec.values.refValue)
        }
      }

      return newCtx
    }
  }
}
