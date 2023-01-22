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

abstract class Instruction {
  abstract val stackInfo: StackInfo
  protected var recordStackInfo = false

  fun execute(ctx: ActionContext, exec: Execution) {
    if (ctx.returnImmediately) {
      return
    }
    if (recordStackInfo) {
      exec.stackTrace.add(stackInfo)
    }
    try {
      execute0(ctx, exec)
    } catch (e: InstructionException) {
      throw e
    } catch (e: Throwable) {
      val msg = e.message
      val stackTrace = ArrayList<StackInfo>(exec.stackTrace.size + 1)
      stackTrace.addAll(exec.stackTrace)
      if (!recordStackInfo) {
        stackTrace.add(stackInfo)
      }
      val ex = if (msg == null) {
        InstructionException(stackTrace, e)
      } else {
        InstructionException(msg, stackTrace, e)
      }
      throw ex
    } finally {
      if (recordStackInfo) {
        exec.stackTrace.removeLast()
      }
    }
  }

  protected abstract fun execute0(ctx: ActionContext, exec: Execution)
}

abstract class InstructionWithStackInfo(override val stackInfo: StackInfo) : Instruction() {
  init {
    recordStackInfo = true
  }
}
