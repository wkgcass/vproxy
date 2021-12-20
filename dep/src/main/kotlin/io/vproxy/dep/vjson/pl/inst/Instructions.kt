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

package io.vproxy.dep.vjson.pl.inst

import io.vproxy.dep.vjson.pl.type.ExecutableField

data class CompositeInstruction(
  val instructions: List<Instruction>
) : Instruction() {
  override val stackInfo: StackInfo = StackInfo.EMPTY

  @Suppress("UNCHECKED_CAST")
  constructor(vararg instructions: Instruction) : this(instructions.asList())

  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    for (inst in instructions) {
      inst.execute(ctx, values)
    }
  }
}

class ExecutableFieldInstruction(
  private val field: ExecutableField,
  stackInfo: StackInfo
) : InstructionWithStackInfo(stackInfo) {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    field.execute(ctx, values)
  }
}

class NoOp : Instruction() {
  override val stackInfo: StackInfo = StackInfo.EMPTY
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
  }
}

data class ReturnInst(
  private val returnValueInst: Instruction?,
) : Instruction() {
  override val stackInfo: StackInfo = StackInfo.EMPTY
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    returnValueInst?.execute(ctx, values)
    ctx.returnImmediately = true
  }
}

data class ThrowInst(
  private val errMsgInst: Instruction?,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    if (errMsgInst == null) {
      throw Exception()
    }
    errMsgInst.execute(ctx, values)
    val res = values.refValue
    if (res is String) {
      throw Exception(res)
    } else {
      throw res as Exception
    }
  }
}

class GetLastError : Instruction() {
  override val stackInfo = StackInfo.EMPTY
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    values.refValue = values.errorValue
  }
}

class LiteralNull(override val stackInfo: StackInfo) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    values.refValue = null
  }
}

class StringConcat(
  val a: Instruction,
  val b: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    a.execute(ctx, values)
    val aStr = values.refValue as String
    b.execute(ctx, values)
    val bStr = values.refValue as String
    values.refValue = aStr + bStr
  }
}

class FunctionInstance(
  private val self: Instruction?,
  private val funcMemDepth: Int,
  private val func: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  var ctxBuilder: (suspend (ActionContext) -> ActionContext)? = null

  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    val capturedContext = if (self == null) {
      ctx.getContext(funcMemDepth)
    } else {
      self.execute(ctx, values)
      values.refValue as ActionContext
    }
    func.execute(capturedContext, values)
    val funcValue = values.refValue as Instruction
    val newCtx = ctxBuilder!!(capturedContext)
    funcValue.execute(newCtx, values)
  }
}

data class LogicNotInstruction(
  private val expr: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    expr.execute(ctx, values)
    values.boolValue = !values.boolValue
  }
}

data class BreakInstruction(
  private val level: Int
) : Instruction() {
  override val stackInfo: StackInfo = StackInfo.EMPTY
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    ctx.breakImmediately = level
  }
}

data class ContinueInstruction(
  private val level: Int
) : Instruction() {
  override val stackInfo: StackInfo = StackInfo.EMPTY
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    ctx.continueImmediately = level
  }
}

abstract class FlowControlInstruction : Instruction() {
  override val stackInfo: StackInfo = StackInfo.EMPTY
  protected fun needReturn(ctx: ActionContext): Boolean {
    return ctx.breakImmediately > 0 || ctx.continueImmediately > 0 || ctx.returnImmediately
  }
}

data class IfInstruction(
  private val conditionInst: Instruction,
  private val ifCodeInst: List<Instruction>,
  private val elseCodeInst: List<Instruction>,
) : FlowControlInstruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    conditionInst.execute(ctx, values)
    if (values.boolValue) {
      for (stmt in ifCodeInst) {
        stmt.execute(ctx, values)
        if (needReturn(ctx)) {
          return
        }
      }
    } else {
      for (stmt in elseCodeInst) {
        stmt.execute(ctx, values)
        if (needReturn(ctx)) {
          return
        }
      }
    }
  }
}

data class ErrorHandlingInstruction(
  private val tryInst: List<Instruction>,
  private val errorCodeInst: List<Instruction>,
  private val elseCodeInst: List<Instruction>
) : FlowControlInstruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    try {
      for (stmt in tryInst) {
        stmt.execute(ctx, values)
        if (needReturn(ctx)) {
          return
        }
      }
    } catch (e: Throwable) {
      for (stmt in errorCodeInst) {
        values.errorValue = e // set this value in the loop to prevent it from being overwritten
        stmt.execute(ctx, values)
        if (needReturn(ctx)) {
          return
        }
      }
    }
    for (stmt in elseCodeInst) {
      stmt.execute(ctx, values)
      if (needReturn(ctx)) {
        return
      }
    }
  }
}

@Suppress("DuplicatedCode")
data class ForLoopInstruction(
  private val initInst: List<Instruction>,
  private val conditionInst: Instruction,
  private val incrInst: List<Instruction>,
  private val codeInst: List<Instruction>,
) : FlowControlInstruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    if (needReturn(ctx)) {
      return
    }
    for (stmt in initInst) {
      stmt.execute(ctx, values)
      if (needReturn(ctx)) {
        return
      }
    }
    while (true) {
      conditionInst.execute(ctx, values)
      if (!values.boolValue) {
        return
      }

      for (stmt in codeInst) {
        stmt.execute(ctx, values)
        if (ctx.breakImmediately > 0) {
          ctx.breakImmediately -= 1
          return
        }
        if (ctx.continueImmediately > 0) {
          ctx.continueImmediately -= 1
          if (ctx.continueImmediately > 1) {
            return
          }
          break
        }
        if (needReturn(ctx)) {
          return
        }
      }

      for (stmt in incrInst) {
        stmt.execute(ctx, values)
        if (needReturn(ctx)) {
          return
        }
      }
    }
  }
}

@Suppress("DuplicatedCode")
data class WhileLoopInstruction(
  private val conditionInst: Instruction,
  private val codeInst: List<Instruction>,
) : FlowControlInstruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    while (true) {
      conditionInst.execute(ctx, values)
      if (!values.boolValue) {
        return
      }
      for (stmt in codeInst) {
        stmt.execute(ctx, values)
        if (ctx.breakImmediately > 0) {
          ctx.breakImmediately -= 1
          return
        }
        if (ctx.continueImmediately > 0) {
          ctx.continueImmediately -= 1
          if (ctx.continueImmediately > 1) {
            return
          }
          break
        }
        if (needReturn(ctx)) {
          return
        }
      }
    }
  }
}
