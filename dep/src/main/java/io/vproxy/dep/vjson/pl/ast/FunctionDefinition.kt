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

data class FunctionDefinition(
  val name: String,
  val params: List<Param>,
  val returnType: Type,
  val code: List<Statement>,
  val modifiers: Modifiers = Modifiers(0),
) : Statement(), MemoryAllocatorProvider {
  override fun copy(): FunctionDefinition {
    val ret = FunctionDefinition(name, params.map { it.copy() }, returnType.copy(), code.map { it.copy() }, modifiers)
    ret.lineCol
    return ret
  }

  private var ctx: TypeContext? = null
  private var variableIndex: Int = -1
  private val memoryAllocator = MemoryAllocator()

  override fun checkAST(ctx: TypeContext) {
    this.ctx = ctx

    if (ctx.hasVariableInThisContext(name)) {
      throw ParserException("variable $name is already defined", lineCol)
    }

    val codeCtx = TypeContext(ctx, ast = this)
    val paramTypes = ArrayList<ParamInstance>(params.size)
    for (p in params) {
      val paramType = p.check(codeCtx, null)
      p.memIndex = memoryAllocator.nextIndexFor(paramType)
      paramTypes.add(ParamInstance(p.name, paramType, p.memIndex))
      codeCtx.addVariable(Variable(p.name, paramType, modifiable = true, executor = null, MemPos(codeCtx.getMemoryDepth(), p.memIndex)))
    }
    val returnTypeInstance = returnType.check(codeCtx, null)
    val funcType = ctx.getFunctionDescriptor(paramTypes, returnTypeInstance, this)
    variableIndex = ctx.getMemoryAllocator().nextRefIndex()
    ctx.addVariable(
      Variable(
        name, FunctionDescriptorTypeInstance(funcType),
        modifiable = false, executor = null,
        MemPos(ctx.getMemoryDepth(), variableIndex)
      )
    )

    codeCtx.checkStatements(code)

    // check whether it has return statement
    if (returnTypeInstance !is VoidType) {
      val lastStatement = code.last()
      if (!lastStatement.functionTerminationCheck()) {
        throw ParserException("function $name not ending properly: missing return statement", lineCol)
      }
    }
  }

  override fun generateInstruction(): Instruction {
    val memDepth = this.ctx!!.getMemoryDepth()

    val ins = ArrayList<Instruction>(code.size)
    for (stmt in code) {
      ins.add(stmt.generateInstruction())
    }

    val composite = CompositeInstruction(ins)
    return object : Instruction() {
      override val stackInfo: StackInfo = ctx!!.stackInfo(lineCol)
      override fun execute0(ctx: ActionContext, exec: Execution) {
        ctx.getMem(memDepth).setRef(variableIndex, composite)
      }
    }
  }

  override fun functionTerminationCheck(): Boolean {
    return false
  }

  fun descriptor(ctx: TypeContext): FunctionDescriptor {
    val paramTypes = ArrayList<ParamInstance>(params.size)
    for (p in params) {
      paramTypes.add(ParamInstance(p.name, p.typeInstance(), p.memIndex))
    }
    val returnType = this.returnType.typeInstance()
    return ctx.getFunctionDescriptor(paramTypes, returnType, this)
  }

  override fun memoryAllocator(): MemoryAllocator {
    return memoryAllocator
  }

  fun getMemPos(): MemPos {
    return MemPos(ctx!!.getMemoryDepth(), variableIndex)
  }

  override fun toString(indent: Int): String {
    val sb = StringBuilder()
    sb.append(modifiers.toStringWithSpace())
    sb.append("function ").append(name).append(":")
    sb.append(params.joinToString(", ", prefix = " { ", postfix = " } "))
    sb.append(returnType)
    sb.append(": {\n")
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
