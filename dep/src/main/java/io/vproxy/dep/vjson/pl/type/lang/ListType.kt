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

open class ListType(
  templateType: TypeInstance,
  iteratorType: IteratorType,
  elementType: TypeInstance
) : CollectionType(templateType, iteratorType, elementType) {
  companion object {
    private val LIST_REMOVE_AT_STACK_INFO = StackInfo("List", "removeAt", LineCol.EMPTY)
    private val LIST_GET_STACK_INFO = StackInfo("List", "get", LineCol.EMPTY)
    private val LIST_SET_STACK_INFO = StackInfo("List", "set", LineCol.EMPTY)
    private val LIST_INSERT_STACK_INFO = StackInfo("List", "insert", LineCol.EMPTY)
    private val LIST_INDEX_OF_STACK_INFO = StackInfo("List", "indexOf", LineCol.EMPTY)
    private val LIST_SUB_LIST_STACK_INFO = StackInfo("List", "subList", LineCol.EMPTY)
  }

  override fun newCollection(initialCap: Int): Collection<*> {
    return ArrayList<Any?>()
  }

  override fun field(ctx: TypeContext, name: String, accessFrom: TypeInstance?): Field? {
    val ret = super.field(ctx, name, accessFrom)
    if (ret != null) return ret

    return when (name) {
      "removeAt" -> {
        val type = ctx.getFunctionDescriptorAsInstance(
          listOf(ParamInstance("index", IntType, 0)), VoidType, FixedMemoryAllocatorProvider(
            RuntimeMemoryTotal(intTotal = 1)
          )
        )
        object : ExecutableField(name, type) {
          override fun execute(ctx: ActionContext, exec: Execution) {
            val obj = exec.values.refValue as ActionContext
            val coll = obj.getCurrentMem().getRef(0) as MutableList<*>
            exec.values.refValue = object : InstructionWithStackInfo(LIST_REMOVE_AT_STACK_INFO) {
              override fun execute0(ctx: ActionContext, exec: Execution) {
                coll.removeAt(ctx.getCurrentMem().getInt(0))
              }
            }
          }
        }
      }
      "get" -> {
        val type = ctx.getFunctionDescriptorAsInstance(
          listOf(ParamInstance("index", IntType, 0)), elementType, FixedMemoryAllocatorProvider(
            RuntimeMemoryTotal(intTotal = 1)
          )
        )
        when (elementType) {
          IntType -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Int>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_GET_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  val x = coll[ctx.getCurrentMem().getInt(0)]
                  exec.values.intValue = x
                }
              }
            }
          }
          LongType -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Long>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_GET_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  val x = coll[ctx.getCurrentMem().getInt(0)]
                  exec.values.longValue = x
                }
              }
            }
          }
          FloatType -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Float>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_GET_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  val x = coll[ctx.getCurrentMem().getInt(0)]
                  exec.values.floatValue = x
                }
              }
            }
          }
          DoubleType -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Double>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_GET_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  val x = coll[ctx.getCurrentMem().getInt(0)]
                  exec.values.doubleValue = x
                }
              }
            }
          }
          BoolType -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Boolean>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_GET_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  val x = coll[ctx.getCurrentMem().getInt(0)]
                  exec.values.boolValue = x
                }
              }
            }
          }
          else -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Any?>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_GET_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  val x = coll[ctx.getCurrentMem().getInt(0)]
                  exec.values.refValue = x
                }
              }
            }
          }
        }
      }
      "set" -> {
        val type = typeForInsertOrSet(ctx)
        when (elementType) {
          IntType -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Int>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_SET_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  val idx = ctx.getCurrentMem().getInt(0)
                  coll[idx] = ctx.getCurrentMem().getInt(1)
                }
              }
            }
          }
          LongType -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Long>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_SET_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  val idx = ctx.getCurrentMem().getInt(0)
                  coll[idx] = ctx.getCurrentMem().getLong(0)
                }
              }
            }
          }
          FloatType -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Float>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_SET_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  val idx = ctx.getCurrentMem().getInt(0)
                  coll[idx] = ctx.getCurrentMem().getFloat(0)
                }
              }
            }
          }
          DoubleType -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Double>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_SET_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  val idx = ctx.getCurrentMem().getInt(0)
                  coll[idx] = ctx.getCurrentMem().getDouble(0)
                }
              }
            }
          }
          BoolType -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Boolean>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_SET_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  val idx = ctx.getCurrentMem().getInt(0)
                  coll[idx] = ctx.getCurrentMem().getBool(0)
                }
              }
            }
          }
          else -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Any?>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_SET_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  val idx = ctx.getCurrentMem().getInt(0)
                  coll[idx] = ctx.getCurrentMem().getRef(0)
                }
              }
            }
          }
        }
      }
      "insert" -> {
        val type = typeForInsertOrSet(ctx)
        when (elementType) {
          IntType -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Int>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_INSERT_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  val idx = ctx.getCurrentMem().getInt(0)
                  coll.add(idx, ctx.getCurrentMem().getInt(1))
                }
              }
            }
          }
          LongType -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Long>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_INSERT_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  val idx = ctx.getCurrentMem().getInt(0)
                  coll.add(idx, ctx.getCurrentMem().getLong(0))
                }
              }
            }
          }
          FloatType -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Float>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_INSERT_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  val idx = ctx.getCurrentMem().getInt(0)
                  coll.add(idx, ctx.getCurrentMem().getFloat(0))
                }
              }
            }
          }
          DoubleType -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Double>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_INSERT_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  val idx = ctx.getCurrentMem().getInt(0)
                  coll.add(idx, ctx.getCurrentMem().getDouble(0))
                }
              }
            }
          }
          BoolType -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Boolean>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_INSERT_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  val idx = ctx.getCurrentMem().getInt(0)
                  coll.add(idx, ctx.getCurrentMem().getBool(0))
                }
              }
            }
          }
          else -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Any?>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_INSERT_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  val idx = ctx.getCurrentMem().getInt(0)
                  coll.add(idx, ctx.getCurrentMem().getRef(0))
                }
              }
            }
          }
        }
      }
      "indexOf" -> {
        val type = ctx.getFunctionDescriptorAsInstance(
          listOf(ParamInstance("e", elementType, 0)), IntType,
          memoryAllocatorForSingleElementTypeFunction()
        )
        when (elementType) {
          IntType -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Int>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_INDEX_OF_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  exec.values.intValue = coll.indexOf(ctx.getCurrentMem().getInt(0))
                }
              }
            }
          }
          LongType -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Long>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_INDEX_OF_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  exec.values.intValue = coll.indexOf(ctx.getCurrentMem().getLong(0))
                }
              }
            }
          }
          FloatType -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Float>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_INDEX_OF_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  exec.values.intValue = coll.indexOf(ctx.getCurrentMem().getFloat(0))
                }
              }
            }
          }
          DoubleType -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Double>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_INDEX_OF_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  exec.values.intValue = coll.indexOf(ctx.getCurrentMem().getDouble(0))
                }
              }
            }
          }
          BoolType -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Boolean>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_INDEX_OF_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  exec.values.intValue = coll.indexOf(ctx.getCurrentMem().getBool(0))
                }
              }
            }
          }
          else -> object : ExecutableField(name, type) {
            override fun execute(ctx: ActionContext, exec: Execution) {
              val obj = exec.values.refValue as ActionContext
              @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Any?>
              exec.values.refValue = object : InstructionWithStackInfo(LIST_INDEX_OF_STACK_INFO) {
                override fun execute0(ctx: ActionContext, exec: Execution) {
                  exec.values.intValue = coll.indexOf(ctx.getCurrentMem().getRef(0))
                }
              }
            }
          }
        }
      }
      "subList" -> object : ExecutableField(
        name,
        ctx.getFunctionDescriptorAsInstance(
          listOf(ParamInstance("fromInclusive", IntType, 0), ParamInstance("toExclusive", IntType, 1)), this,
          FixedMemoryAllocatorProvider(RuntimeMemoryTotal(intTotal = 2))
        )
      ) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val obj = exec.values.refValue as ActionContext
          @Suppress("UNCHECKED_CAST") val coll = obj.getCurrentMem().getRef(0) as MutableList<Any?>
          exec.values.refValue = object : InstructionWithStackInfo(LIST_SUB_LIST_STACK_INFO) {
            override fun execute0(ctx: ActionContext, exec: Execution) {
              val subLs = coll.subList(ctx.getCurrentMem().getInt(0), ctx.getCurrentMem().getInt(1))
              val newObj = ActionContext(RuntimeMemoryTotal(refTotal = 1), null)
              newObj.getCurrentMem().setRef(0, subLs)
              exec.values.refValue = newObj
            }
          }
        }
      }
      else -> null
    }
  }

  private fun typeForInsertOrSet(ctx: TypeContext): FunctionDescriptorTypeInstance {
    return ctx.getFunctionDescriptorAsInstance(
      listOf(ParamInstance("index", IntType, 0), ParamInstance("e", elementType, if (elementType is IntType) 1 else 0)),
      VoidType,
      when (elementType) {
        is IntType -> FixedMemoryAllocatorProvider(RuntimeMemoryTotal(intTotal = 2))
        is LongType -> FixedMemoryAllocatorProvider(RuntimeMemoryTotal(intTotal = 1, longTotal = 1))
        is FloatType -> FixedMemoryAllocatorProvider(RuntimeMemoryTotal(intTotal = 1, floatTotal = 1))
        is DoubleType -> FixedMemoryAllocatorProvider(RuntimeMemoryTotal(intTotal = 1, doubleTotal = 1))
        is BoolType -> FixedMemoryAllocatorProvider(RuntimeMemoryTotal(intTotal = 1, boolTotal = 1))
        else -> FixedMemoryAllocatorProvider(RuntimeMemoryTotal(intTotal = 1, refTotal = 1))
      }
    )
  }

  override fun toString(): String {
    return "List<$elementType>"
  }
}
