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

import io.vproxy.dep.vjson.pl.inst.ActionContext
import io.vproxy.dep.vjson.pl.inst.Execution
import io.vproxy.dep.vjson.pl.type.*

class IteratorType(
  private val templateType: TypeInstance,
  private val elementType: TypeInstance
) : TypeInstance {
  override fun field(ctx: TypeContext, name: String, accessFrom: TypeInstance?): Field? {
    return when (name) {
      "hasNext" -> object : ExecutableField(name, BoolType) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val obj = exec.values.refValue as ActionContext
          val ite = obj.getCurrentMem().getRef(0) as Iterator<Any?>
          exec.values.boolValue = ite.hasNext()
        }
      }
      "next" -> when (elementType) {
        IntType -> object : ExecutableField(name, elementType) {
          override fun execute(ctx: ActionContext, exec: Execution) {
            val obj = exec.values.refValue as ActionContext
            @Suppress("UNCHECKED_CAST") val ite = obj.getCurrentMem().getRef(0) as Iterator<Int>
            val nx = ite.next()
            exec.values.intValue = nx
          }
        }
        LongType -> object : ExecutableField(name, elementType) {
          override fun execute(ctx: ActionContext, exec: Execution) {
            val obj = exec.values.refValue as ActionContext
            @Suppress("UNCHECKED_CAST") val ite = obj.getCurrentMem().getRef(0) as Iterator<Long>
            val nx = ite.next()
            exec.values.longValue = nx
          }
        }
        FloatType -> object : ExecutableField(name, elementType) {
          override fun execute(ctx: ActionContext, exec: Execution) {
            val obj = exec.values.refValue as ActionContext
            @Suppress("UNCHECKED_CAST") val ite = obj.getCurrentMem().getRef(0) as Iterator<Float>
            val nx = ite.next()
            exec.values.floatValue = nx
          }
        }
        DoubleType -> object : ExecutableField(name, elementType) {
          override fun execute(ctx: ActionContext, exec: Execution) {
            val obj = exec.values.refValue as ActionContext
            @Suppress("UNCHECKED_CAST") val ite = obj.getCurrentMem().getRef(0) as Iterator<Double>
            val nx = ite.next()
            exec.values.doubleValue = nx
          }
        }
        BoolType -> object : ExecutableField(name, elementType) {
          override fun execute(ctx: ActionContext, exec: Execution) {
            val obj = exec.values.refValue as ActionContext
            @Suppress("UNCHECKED_CAST") val ite = obj.getCurrentMem().getRef(0) as Iterator<Boolean>
            val nx = ite.next()
            exec.values.boolValue = nx
          }
        }
        else -> object : ExecutableField(name, elementType) {
          override fun execute(ctx: ActionContext, exec: Execution) {
            val obj = exec.values.refValue as ActionContext
            @Suppress("UNCHECKED_CAST") val ite = obj.getCurrentMem().getRef(0) as Iterator<Any?>
            val nx = ite.next()
            exec.values.refValue = nx
          }
        }
      }
      else -> null
    }
  }

  override fun templateType(): TypeInstance {
    return templateType
  }

  override fun templateTypeParams(): List<TypeInstance> {
    return listOf(elementType)
  }
}
