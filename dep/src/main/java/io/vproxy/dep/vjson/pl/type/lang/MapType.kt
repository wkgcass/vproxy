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
import io.vproxy.dep.vjson.pl.inst.*
import io.vproxy.dep.vjson.pl.type.*

open class MapType(
  private val templateType: TypeInstance,
  templateKeySetType: TypeInstance,
  keySetIteratorType: IteratorType,
  private val key: TypeInstance, private val value: TypeInstance
) : TypeInstance {
  companion object {
    private val MAP_TO_STRING_STACK_INFO = StackInfo("Map", "toString", LineCol.EMPTY)
    private val MAP_CONTAINS_KEY_STACK_INFO = StackInfo("Map", "containsKey", LineCol.EMPTY)
  }

  private val keySetType = SetType(templateKeySetType, keySetIteratorType, key)

  private val constructorDescriptor = object : ExecutableConstructorFunctionDescriptor(
    listOf(ParamInstance("size", IntType, 0)),
    VoidType,
    FixedMemoryAllocatorProvider(RuntimeMemoryTotal(intTotal = 1, refTotal = 1))
  ) {
    override fun execute(ctx: ActionContext, exec: Execution) {
      val mem = ctx.getCurrentMem()
      mem.setRef(0, newMap(mem.getInt(0)))
    }
  }

  protected open fun newMap(cap: Int): Map<*, *> {
    return HashMap<Any?, Any?>()
  }

  override fun constructor(ctx: TypeContext): FunctionDescriptor {
    return constructorDescriptor
  }

  override fun field(ctx: TypeContext, name: String, accessFrom: TypeInstance?): Field? {
    if (name == "put" || name == "get" || name == "remove") {
      return generatedForMap0(key, value, ctx, name)
    }
    return when (name) {
      "size" -> object : ExecutableField(name, IntType) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val obj = exec.values.refValue as ActionContext
          val map = obj.getCurrentMem().getRef(0) as Map<*, *>
          exec.values.intValue = map.size
        }
      }
      "keySet" -> object : ExecutableField(name, keySetType) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val obj = exec.values.refValue as ActionContext
          val map = obj.getCurrentMem().getRef(0) as Map<*, *>
          val newObj = ActionContext(RuntimeMemoryTotal(refTotal = 1), parent = null)
          newObj.getCurrentMem().setRef(0, map.keys)
          exec.values.refValue = newObj
        }
      }
      "containsKey" -> when (key) {
        IntType -> object : ExecutableField(
          name,
          ctx.getFunctionDescriptorAsInstance(
            listOf(ParamInstance("key", IntType, 0)), BoolType,
            FixedMemoryAllocatorProvider(RuntimeMemoryTotal(intTotal = 1))
          )
        ) {
          override fun execute(ctx: ActionContext, exec: Execution) {
            val obj = exec.values.refValue as ActionContext
            @Suppress("UNCHECKED_CAST") val map = obj.getCurrentMem().getRef(0) as Map<Int, *>
            exec.values.refValue = object : InstructionWithStackInfo(MAP_CONTAINS_KEY_STACK_INFO) {
              override fun execute0(ctx: ActionContext, exec: Execution) {
                exec.values.boolValue = map.containsKey(ctx.getCurrentMem().getInt(0))
              }
            }
          }
        }
        LongType -> object : ExecutableField(
          name,
          ctx.getFunctionDescriptorAsInstance(
            listOf(ParamInstance("key", LongType, 0)), BoolType,
            FixedMemoryAllocatorProvider(RuntimeMemoryTotal(longTotal = 1))
          )
        ) {
          override fun execute(ctx: ActionContext, exec: Execution) {
            val obj = exec.values.refValue as ActionContext
            @Suppress("UNCHECKED_CAST") val map = obj.getCurrentMem().getRef(0) as Map<Long, *>
            exec.values.refValue = object : InstructionWithStackInfo(MAP_CONTAINS_KEY_STACK_INFO) {
              override fun execute0(ctx: ActionContext, exec: Execution) {
                exec.values.boolValue = map.containsKey(ctx.getCurrentMem().getLong(0))
              }
            }
          }
        }
        FloatType -> object : ExecutableField(
          name,
          ctx.getFunctionDescriptorAsInstance(
            listOf(ParamInstance("key", FloatType, 0)), BoolType,
            FixedMemoryAllocatorProvider(RuntimeMemoryTotal(floatTotal = 1))
          )
        ) {
          override fun execute(ctx: ActionContext, exec: Execution) {
            val obj = exec.values.refValue as ActionContext
            @Suppress("UNCHECKED_CAST") val map = obj.getCurrentMem().getRef(0) as Map<Float, *>
            exec.values.refValue = object : InstructionWithStackInfo(MAP_CONTAINS_KEY_STACK_INFO) {
              override fun execute0(ctx: ActionContext, exec: Execution) {
                exec.values.boolValue = map.containsKey(ctx.getCurrentMem().getFloat(0))
              }
            }
          }
        }
        DoubleType -> object : ExecutableField(
          name,
          ctx.getFunctionDescriptorAsInstance(
            listOf(ParamInstance("key", DoubleType, 0)), BoolType,
            FixedMemoryAllocatorProvider(RuntimeMemoryTotal(doubleTotal = 1))
          )
        ) {
          override fun execute(ctx: ActionContext, exec: Execution) {
            val obj = exec.values.refValue as ActionContext
            @Suppress("UNCHECKED_CAST") val map = obj.getCurrentMem().getRef(0) as Map<Double, *>
            exec.values.refValue = object : InstructionWithStackInfo(MAP_CONTAINS_KEY_STACK_INFO) {
              override fun execute0(ctx: ActionContext, exec: Execution) {
                exec.values.boolValue = map.containsKey(ctx.getCurrentMem().getDouble(0))
              }
            }
          }
        }
        BoolType -> object : ExecutableField(
          name,
          ctx.getFunctionDescriptorAsInstance(
            listOf(ParamInstance("key", BoolType, 0)), BoolType,
            FixedMemoryAllocatorProvider(RuntimeMemoryTotal(boolTotal = 1))
          )
        ) {
          override fun execute(ctx: ActionContext, exec: Execution) {
            val obj = exec.values.refValue as ActionContext
            @Suppress("UNCHECKED_CAST") val map = obj.getCurrentMem().getRef(0) as Map<Boolean, *>
            exec.values.refValue = object : InstructionWithStackInfo(MAP_CONTAINS_KEY_STACK_INFO) {
              override fun execute0(ctx: ActionContext, exec: Execution) {
                exec.values.boolValue = map.containsKey(ctx.getCurrentMem().getBool(0))
              }
            }
          }
        }
        else -> object : ExecutableField(
          name,
          ctx.getFunctionDescriptorAsInstance(
            listOf(ParamInstance("key", key, 0)), BoolType,
            FixedMemoryAllocatorProvider(RuntimeMemoryTotal(refTotal = 1))
          )
        ) {
          override fun execute(ctx: ActionContext, exec: Execution) {
            val obj = exec.values.refValue as ActionContext
            @Suppress("UNCHECKED_CAST") val map = obj.getCurrentMem().getRef(0) as Map<Any?, *>
            exec.values.refValue = object : InstructionWithStackInfo(MAP_CONTAINS_KEY_STACK_INFO) {
              override fun execute0(ctx: ActionContext, exec: Execution) {
                exec.values.boolValue = map.containsKey(ctx.getCurrentMem().getRef(0))
              }
            }
          }
        }
      }
      "toString" -> object : ExecutableField(
        name,
        ctx.getFunctionDescriptorAsInstance(listOf(), StringType, DummyMemoryAllocatorProvider)
      ) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val obj = exec.values.refValue as ActionContext
          val map = obj.getCurrentMem().getRef(0) as Map<*, *>
          exec.values.refValue = object : InstructionWithStackInfo(MAP_TO_STRING_STACK_INFO) {
            override fun execute0(ctx: ActionContext, exec: Execution) {
              exec.values.refValue = map.toString()
            }
          }
        }
      }
      else -> null
    }
  }

  override fun templateType(): TypeInstance {
    return templateType
  }

  override fun templateTypeParams(): List<TypeInstance>? {
    return listOf(key, value)
  }

  override fun toString(): String {
    return "Map<$key, $value>"
  }
}
