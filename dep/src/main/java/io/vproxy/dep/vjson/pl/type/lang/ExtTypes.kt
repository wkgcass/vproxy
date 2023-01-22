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

package io.vproxy.dep.vjson.pl.type.lang

import io.vproxy.dep.vjson.cs.LineCol
import io.vproxy.dep.vjson.pl.ast.Type
import io.vproxy.dep.vjson.pl.inst.*
import io.vproxy.dep.vjson.pl.type.*

class ExtFunctions {
  var currentTimeMillis: () -> Long = { 0L }
    private set
  var rand: () -> Double = { 0.0 }
    private set

  fun setCurrentTimeMillis(f: () -> Long): ExtFunctions {
    this.currentTimeMillis = f
    return this
  }

  fun setCurrentTimeMillisBlock(f: () -> Long): ExtFunctions {
    this.currentTimeMillis = { f() }
    return this
  }

  fun setRand(f: () -> Double): ExtFunctions {
    this.rand = f
    return this
  }

  fun setRandBlock(f: () -> Double): ExtFunctions {
    this.rand = { f() }
    return this
  }
}

class ExtTypes(private val funcs: ExtFunctions) : Types {
  companion object {
    private val RAND_STACK_INFO = StackInfo("ext", "rand", LineCol.EMPTY)
  }

  private val extObject = ActionContext(RuntimeMemoryTotal(), null)

  override fun initiateType(ctx: TypeContext, offset: RuntimeMemoryTotal): RuntimeMemoryTotal {
    val extClass = ExtClass()
    ctx.addType(Type("ext"), extClass)
    ctx.addVariable(Variable("ext", extClass, modifiable = false, executor = null, MemPos(0, ctx.getMemoryAllocator().nextRefIndex())))
    return RuntimeMemoryTotal(refTotal = 1)
  }

  override fun initiateValues(ctx: ActionContext, offset: RuntimeMemoryTotal, values: RuntimeMemory?) {
    ctx.getCurrentMem().setRef(offset.refTotal, extObject)
  }

  inner class ExtClass : TypeInstance {
    override fun field(ctx: TypeContext, name: String, accessFrom: TypeInstance?): Field? {
      return when (name) {
        "currentTimeMillis" -> object : ExecutableField(name, LongType) {
          override fun execute(ctx: ActionContext, exec: Execution) {
            exec.values.longValue = funcs.currentTimeMillis()
          }
        }
        "rand" -> object : ExecutableField(
          name,
          ctx.getFunctionDescriptorAsInstance(listOf(), DoubleType, DummyMemoryAllocatorProvider)
        ) {
          override fun execute(ctx: ActionContext, exec: Execution) {
            exec.values.refValue = object : InstructionWithStackInfo(RAND_STACK_INFO) {
              override fun execute0(ctx: ActionContext, exec: Execution) {
                exec.values.doubleValue = funcs.rand()
              }
            }
          }
        }
        else -> null
      }
    }
  }
}
