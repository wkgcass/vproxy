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

import io.vproxy.dep.vjson.pl.inst.ActionContext
import io.vproxy.dep.vjson.pl.inst.ValueHolder

open class FunctionDescriptor(val params: List<ParamInstance>, val returnType: TypeInstance, val mem: MemoryAllocatorProvider) {
  override fun toString(): String {
    return "(${params.joinToString()}): $returnType"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is FunctionDescriptor) return false

    if (params != other.params) return false
    if (returnType != other.returnType) return false
    if (mem != other.mem) return false

    return true
  }

  override fun hashCode(): Int {
    var result = params.hashCode()
    result = 31 * result + returnType.hashCode()
    result = 31 * result + mem.hashCode()
    return result
  }
}

abstract class ExecutableConstructorFunctionDescriptor(
  params: List<ParamInstance>,
  returnType: TypeInstance,
  mem: MemoryAllocatorProvider
) : FunctionDescriptor(params, returnType, mem) {
  abstract suspend fun execute(ctx: ActionContext, values: ValueHolder)
}
