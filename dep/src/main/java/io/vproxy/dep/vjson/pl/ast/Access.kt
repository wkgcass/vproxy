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

data class Access
/*#ifndef KOTLIN_NATIVE {{ */ @JvmOverloads/*}}*/
constructor(val name: String, val from: Expr? = null) : AssignableExpr() {
  override fun copy(): Access {
    val ret = Access(name, from?.copy())
    ret.lineCol = lineCol
    return ret
  }

  override fun check(ctx: TypeContext, typeHint: TypeInstance?): TypeInstance {
    this.ctx = ctx
    if (from == null) {
      if (!ctx.hasVariable(name)) {
        throw ParserException("$this: variable $name is not defined", lineCol)
      }
      return ctx.getVariable(name).type // ok
    }
    val fromType = from.check(ctx, null)
    val fieldType = fromType.field(ctx, name, ctx.getContextType())
    if (fieldType != null) {
      return fieldType.type // ok
    }
    throw ParserException("$this: $fromType doesn't have field `$name`", lineCol)
  }

  override fun typeInstance(): TypeInstance {
    return if (from == null) {
      ctx.getVariable(name).type
    } else {
      from.typeInstance().field(ctx, name, ctx.getContextType())!!.type
    }
  }

  override fun isModifiable(): Boolean {
    return if (from == null) {
      ctx.getVariable(name).modifiable
    } else {
      val fromType = from.typeInstance()
      val fieldType = fromType.field(ctx, name, ctx.getContextType())!!
      fieldType.modifiable
    }
  }

  override fun generateInstruction(): Instruction {
    return if (from == null) {
      val variable = ctx.getVariable(name)
      if (variable.executor != null) {
        val func = variable.executor
        FunctionInvocation.invokeFunction(ctx, func.first, func.second, listOf(), lineCol)
      } else when (typeInstance()) {
        is IntType -> GetInt(variable.memPos.depth, variable.memPos.index, ctx.stackInfo(lineCol))
        is LongType -> GetLong(variable.memPos.depth, variable.memPos.index, ctx.stackInfo(lineCol))
        is FloatType -> GetFloat(variable.memPos.depth, variable.memPos.index, ctx.stackInfo(lineCol))
        is DoubleType -> GetDouble(variable.memPos.depth, variable.memPos.index, ctx.stackInfo(lineCol))
        is BoolType -> GetBool(variable.memPos.depth, variable.memPos.index, ctx.stackInfo(lineCol))
        else -> if (typeInstance() is ErrorType && name == "err") {
          GetLastError()
        } else {
          val inst = GetRef(variable.memPos.depth, variable.memPos.index, ctx.stackInfo(lineCol))
          if (variable.type.functionDescriptor(ctx) != null) {
            return FunctionInstance(null, variable.memPos.depth, inst, ctx.stackInfo(lineCol))
          }
          inst
        }
      }
    } else {
      val fromType = from.typeInstance()
      val fieldType = fromType.field(ctx, name, ctx.getContextType())!!
      if (fieldType.executor != null) {
        val fromInst = from.generateInstruction()
        val func = fieldType.executor
        val funcDesc = func.first
        val funcInst = func.second
        object : InstructionWithStackInfo(ctx.stackInfo(lineCol)) {
          override fun execute0(ctx: ActionContext, exec: Execution) {
            fromInst.execute(ctx, exec)
            val objectCtx = exec.values.refValue as ActionContext
            if (funcInst is FunctionInstance) {
              funcInst.ctxBuilder = { FunctionInvocation.buildContext(objectCtx, it, exec, funcDesc, listOf()) }
              funcInst.execute(objectCtx, exec)
            } else {
              funcInst.execute(objectCtx, exec)
              val funcValue = exec.values.refValue as Instruction
              val newCtx = FunctionInvocation.buildContext(objectCtx, objectCtx, exec, funcDesc, listOf())
              funcValue.execute(newCtx, exec)
            }
          }
        }
      } else {
        buildGetFieldInstruction(ctx, from.generateInstruction(), from.typeInstance(), name, lineCol)
      }
    }
  }

  override fun generateSetInstruction(valueInst: Instruction): Instruction {
    return if (from == null) {
      val variable = ctx.getVariable(name)
      when (typeInstance()) {
        is IntType -> SetInt(variable.memPos.depth, variable.memPos.index, valueInst, ctx.stackInfo(lineCol))
        is LongType -> SetLong(variable.memPos.depth, variable.memPos.index, valueInst, ctx.stackInfo(lineCol))
        is FloatType -> SetFloat(variable.memPos.depth, variable.memPos.index, valueInst, ctx.stackInfo(lineCol))
        is DoubleType -> SetDouble(variable.memPos.depth, variable.memPos.index, valueInst, ctx.stackInfo(lineCol))
        is BoolType -> SetBool(variable.memPos.depth, variable.memPos.index, valueInst, ctx.stackInfo(lineCol))
        else -> SetRef(variable.memPos.depth, variable.memPos.index, valueInst, ctx.stackInfo(lineCol))
      }
    } else {
      val fromInst = from.generateInstruction()
      val field = from.typeInstance().field(ctx, name, ctx.getContextType())
      val setField = when (field!!.type) {
        is IntType -> SetFieldInt(field.memPos.index, valueInst, ctx.stackInfo(lineCol))
        is LongType -> SetFieldLong(field.memPos.index, valueInst, ctx.stackInfo(lineCol))
        is FloatType -> SetFieldFloat(field.memPos.index, valueInst, ctx.stackInfo(lineCol))
        is DoubleType -> SetFieldDouble(field.memPos.index, valueInst, ctx.stackInfo(lineCol))
        is BoolType -> SetFieldBool(field.memPos.index, valueInst, ctx.stackInfo(lineCol))
        else -> SetFieldRef(field.memPos.index, valueInst, ctx.stackInfo(lineCol))
      }
      object : Instruction() {
        override val stackInfo: StackInfo = ctx.stackInfo(lineCol)
        override fun execute0(ctx: ActionContext, exec: Execution) {
          fromInst.execute(ctx, exec)
          val objCtx = exec.values.refValue as ActionContext
          setField.execute(objCtx, exec)
        }
      }
    }
  }

  override fun toString(indent: Int): String {
    return if (from == null) {
      name
    } else {
      "$from.$name"
    }
  }

  override fun toString(): String {
    return toString(0)
  }

  companion object {
    fun buildGetFieldInstruction(ctx: TypeContext, from: Instruction, fromType: TypeInstance, name: String, lineCol: LineCol): Instruction {
      val field = fromType.field(ctx, name, ctx.getContextType())
      val getFieldInst = if (field is ExecutableField) {
        ExecutableFieldInstruction(field, ctx.stackInfo(lineCol))
      } else when (field!!.type) {
        is IntType -> GetFieldInt(field.memPos.index, ctx.stackInfo(lineCol))
        is LongType -> GetFieldLong(field.memPos.index, ctx.stackInfo(lineCol))
        is FloatType -> GetFieldFloat(field.memPos.index, ctx.stackInfo(lineCol))
        is DoubleType -> GetFieldDouble(field.memPos.index, ctx.stackInfo(lineCol))
        is BoolType -> GetFieldBool(field.memPos.index, ctx.stackInfo(lineCol))
        else -> {
          val inst = GetFieldRef(field.memPos.index, ctx.stackInfo(lineCol))
          if (field.type.functionDescriptor(ctx) != null) {
            return FunctionInstance(from, field.memPos.depth, inst, ctx.stackInfo(lineCol))
          }
          inst
        }
      }
      return CompositeInstruction(from, getFieldInst)
    }
  }
}
