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
import io.vproxy.dep.vjson.pl.inst.ActionContext
import io.vproxy.dep.vjson.pl.inst.Instruction
import io.vproxy.dep.vjson.pl.inst.InstructionWithStackInfo
import io.vproxy.dep.vjson.pl.inst.ValueHolder
import io.vproxy.dep.vjson.pl.type.*

data class NewInstance(
  val type: Type,
  val args: List<Expr>,
) : Expr() {
  override fun copy(): NewInstance {
    val ret = NewInstance(type.copy(), args.map { it.copy() })
    ret.lineCol = lineCol
    return ret
  }

  override fun check(ctx: TypeContext): TypeInstance {
    this.ctx = ctx
    val typeInstance = type.check(ctx)
    val constructor = typeInstance.constructor(ctx) ?: throw ParserException("$this: cannot instantiate $typeInstance", lineCol)
    if (args.size != constructor.params.size) {
      throw ParserException(
        "$this: unable to instantiate $typeInstance with $args: arguments count (${args.size}) and parameters count (${constructor.params.size}) mismatch",
        lineCol
      )
    }
    for (idx in args.indices) {
      val argType = args[idx].check(ctx)
      val paramType = constructor.params[idx]
      if (argType != paramType.type) {
        throw ParserException(
          "$this: unable to instantiate $typeInstance with $args, args[$idx] $argType does not match params[$idx] $paramType",
          lineCol
        )
      }
    }
    return typeInstance
  }

  override fun typeInstance(): TypeInstance {
    return type.typeInstance()
  }

  override fun generateInstruction(): Instruction {
    val typeInstance = this.type.typeInstance()
    val cons = typeInstance.constructor(ctx)!!

    if (cons is ExecutableConstructorFunctionDescriptor) {
      return object : InstructionWithStackInfo(ctx.stackInfo(lineCol)) {
        override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
          val newCtx = ActionContext(cons.mem.memoryAllocator().getTotal(), null)
          cons.execute(newCtx, values)
          values.refValue = newCtx
        }
      }
    }

    val classType = typeInstance as ClassTypeInstance
    val cls = classType.cls
    val memDepth = cls.getMemDepth()
    val total = cons.mem.memoryAllocator().getTotal()

    val args = this.args.map { it.generateInstruction() }
    val code = cls.code.map { it.generateInstruction() }

    return object : InstructionWithStackInfo(ctx.stackInfo(lineCol)) {
      override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
        val newCtx = ActionContext(total, ctx.getContext(memDepth))
        for (i in args.indices) {
          val param = cons.params[i]
          args[i].execute(ctx, values)
          when (param.type) {
            is IntType -> newCtx.getCurrentMem().setInt(param.memIndex, values.intValue)
            is LongType -> newCtx.getCurrentMem().setLong(param.memIndex, values.longValue)
            is FloatType -> newCtx.getCurrentMem().setFloat(param.memIndex, values.floatValue)
            is DoubleType -> newCtx.getCurrentMem().setDouble(param.memIndex, values.doubleValue)
            is BoolType -> newCtx.getCurrentMem().setBool(param.memIndex, values.boolValue)
            else -> newCtx.getCurrentMem().setRef(param.memIndex, values.refValue)
          }
        }

        for (c in code) {
          c.execute(newCtx, values)
        }

        values.refValue = newCtx
      }
    }
  }

  override fun toString(indent: Int): String {
    return "new $type:$args"
  }

  override fun toString(): String {
    return toString(0)
  }
}
