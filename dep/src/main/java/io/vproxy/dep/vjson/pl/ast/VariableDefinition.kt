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
import io.vproxy.dep.vjson.pl.inst.*
import io.vproxy.dep.vjson.pl.type.*

data class VariableDefinition(
  var name: String,
  var value: Expr,
  var modifiers: Modifiers = Modifiers(0),
) : Statement() {
  override fun copy(): VariableDefinition {
    val ret = VariableDefinition(name, value.copy(), modifiers)
    ret.lineCol = lineCol
    return ret
  }

  private var ctx: TypeContext? = null
  private var variableIndex: Int = -1

  override fun checkAST(ctx: TypeContext) {
    this.ctx = ctx
    if (ctx.hasVariableInThisContext(name)) {
      throw ParserException("variable $name is already defined", lineCol)
    }
    if (modifiers.isExecutable()) {
      if (value !is Access) {
        throw ParserException("unexpected value $value for executable variable $name, expecting a function")
      }
      val valueFuncType = value.check(ctx)
      val func = valueFuncType.functionDescriptor(ctx)
        ?: throw ParserException("unexpected value $value ($valueFuncType) for executable variable $name, not a function")
      if (func.params.isNotEmpty()) {
        throw ParserException("unexpected value $value ($valueFuncType) for executable variable $name, expecting a no-argument function")
      }
      if (func.returnType is VoidType) {
        throw ParserException("unexpected value $value ($valueFuncType) for executable variable $name, expecting a function with return value")
      }
      val valueType = func.returnType
      ctx.addVariable(
        Variable(
          name, valueType,
          modifiable = false,
          executor = Pair(func, value.generateInstruction()),
          MemPos(ctx.getMemoryDepth(), -1)
        )
      )
    } else {
      val valueType = value.check(ctx)
      if (valueType is NullType) {
        throw ParserException("$this: cannot determine type for $value", lineCol)
      }
      variableIndex = ctx.getMemoryAllocator().nextIndexFor(valueType)
      ctx.addVariable(
        Variable(
          name, valueType,
          modifiable = !modifiers.isConst(), executor = null,
          MemPos(ctx.getMemoryDepth(), variableIndex)
        )
      )
    }
  }

  override fun functionTerminationCheck(): Boolean {
    return false
  }

  override fun generateInstruction(): Instruction {
    if (modifiers.isExecutable()) {
      return NoOp()
    }
    val valueInst = value.generateInstruction()
    return when (value.typeInstance()) {
      is IntType -> SetInt(ctx!!.getMemoryDepth(), variableIndex, valueInst, ctx!!.stackInfo(lineCol))
      is LongType -> SetLong(ctx!!.getMemoryDepth(), variableIndex, valueInst, ctx!!.stackInfo(lineCol))
      is FloatType -> SetFloat(ctx!!.getMemoryDepth(), variableIndex, valueInst, ctx!!.stackInfo(lineCol))
      is DoubleType -> SetDouble(ctx!!.getMemoryDepth(), variableIndex, valueInst, ctx!!.stackInfo(lineCol))
      is BoolType -> SetBool(ctx!!.getMemoryDepth(), variableIndex, valueInst, ctx!!.stackInfo(lineCol))
      else -> SetRef(ctx!!.getMemoryDepth(), variableIndex, valueInst, ctx!!.stackInfo(lineCol))
    }
  }

  fun getMemPos(): MemPos {
    return MemPos(ctx!!.getMemoryDepth(), variableIndex)
  }

  override fun toString(indent: Int): String {
    return modifiers.toStringWithSpace() + "var $name = $value"
  }

  override fun toString(): String {
    return toString(0)
  }

  fun typeInstance(): TypeInstance {
    if (modifiers.isExecutable()) {
      return value.typeInstance().functionDescriptor(ctx!!)!!.returnType
    } else {
      return value.typeInstance()
    }
  }

  fun getCtx(): TypeContext {
    return ctx!!
  }
}
