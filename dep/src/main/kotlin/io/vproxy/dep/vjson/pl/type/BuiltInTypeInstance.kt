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

import io.vproxy.dep.vjson.cs.LineCol
import io.vproxy.dep.vjson.pl.ast.Type
import io.vproxy.dep.vjson.pl.inst.*

interface BuiltInTypeInstance : TypeInstance {
}

interface PrimitiveTypeInstance : BuiltInTypeInstance {
}

interface NumericTypeInstance : PrimitiveTypeInstance {
}

object IntType : NumericTypeInstance {
  override fun field(ctx: TypeContext, name: String, accessFrom: TypeInstance?): Field? {
    return when (name) {
      "toInt" -> object : ExecutableField(name, ctx.getType(Type("int")), MemPos(0, 0)) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
        }
      }
      "toLong" -> object : ExecutableField(name, ctx.getType(Type("long")), MemPos(0, 0)) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          values.longValue = values.intValue.toLong()
        }
      }
      "toFloat" -> object : ExecutableField("toFloat", ctx.getType(Type("float")), MemPos(0, 0)) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          values.floatValue = values.intValue.toFloat()
        }
      }
      "toDouble" -> object : ExecutableField(name, ctx.getType(Type("double")), MemPos(0, 0)) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          values.doubleValue = values.intValue.toDouble()
        }
      }
      "toString" -> object : ExecutableField(
        "toString",
        ctx.getFunctionDescriptorAsInstance(listOf(), ctx.getType(Type("string")), DummyMemoryAllocatorProvider),
        MemPos(0, 0)
      ) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          val n = values.intValue
          values.refValue = object : InstructionWithStackInfo(INT_TO_STRING_STACK_INFO) {
            override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
              values.refValue = n.toString()
            }
          }
        }
      }
      else -> null
    }
  }

  override fun toString(): String {
    return "IntType"
  }
}

object LongType : NumericTypeInstance {
  override fun field(ctx: TypeContext, name: String, accessFrom: TypeInstance?): Field? {
    return when (name) {
      "toInt" -> object : ExecutableField(name, ctx.getType(Type("int")), MemPos(0, 0)) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          values.intValue = values.longValue.toInt()
        }
      }
      "toLong" -> object : ExecutableField(name, ctx.getType(Type("long")), MemPos(0, 0)) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
        }
      }
      "toFloat" -> object : ExecutableField("toFloat", ctx.getType(Type("float")), MemPos(0, 0)) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          values.floatValue = values.longValue.toFloat()
        }
      }
      "toDouble" -> object : ExecutableField(name, ctx.getType(Type("double")), MemPos(0, 0)) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          values.doubleValue = values.longValue.toDouble()
        }
      }
      "toString" -> object : ExecutableField(
        "toString",
        ctx.getFunctionDescriptorAsInstance(listOf(), ctx.getType(Type("string")), DummyMemoryAllocatorProvider),
        MemPos(0, 0)
      ) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          val n = values.longValue
          values.refValue = object : InstructionWithStackInfo(LONG_TO_STRING_STACK_INFO) {
            override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
              values.refValue = n.toString()
            }
          }
        }
      }
      else -> null
    }
  }

  override fun toString(): String {
    return "LongType"
  }
}

object FloatType : NumericTypeInstance {
  override fun field(ctx: TypeContext, name: String, accessFrom: TypeInstance?): Field? {
    return when (name) {
      "toInt" -> object : ExecutableField("toInt", ctx.getType(Type("int")), MemPos(0, 0)) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          values.intValue = values.floatValue.toInt()
        }
      }
      "toLong" -> object : ExecutableField("toLong", ctx.getType(Type("long")), MemPos(0, 0)) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          values.longValue = values.floatValue.toLong()
        }
      }
      "toFloat" -> object : ExecutableField(name, ctx.getType(Type("float")), MemPos(0, 0)) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
        }
      }
      "toDouble" -> object : ExecutableField("toDouble", ctx.getType(Type("double")), MemPos(0, 0)) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          values.doubleValue = values.floatValue.toDouble()
        }
      }
      "toString" -> object : ExecutableField(
        "toString",
        ctx.getFunctionDescriptorAsInstance(listOf(), ctx.getType(Type("string")), DummyMemoryAllocatorProvider),
        MemPos(0, 0)
      ) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          val n = values.floatValue
          values.refValue = object : InstructionWithStackInfo(FLOAT_TO_STRING_STACK_INFO) {
            override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
              values.refValue = n.toString()
            }
          }
        }
      }
      else -> null
    }
  }

  override fun toString(): String {
    return "FloatType"
  }
}

object DoubleType : NumericTypeInstance {
  override fun field(ctx: TypeContext, name: String, accessFrom: TypeInstance?): Field? {
    return when (name) {
      "toInt" -> object : ExecutableField("toInt", ctx.getType(Type("int")), MemPos(0, 0)) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          values.intValue = values.doubleValue.toInt()
        }
      }
      "toLong" -> object : ExecutableField("toLong", ctx.getType(Type("long")), MemPos(0, 0)) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          values.longValue = values.doubleValue.toLong()
        }
      }
      "toFloat" -> object : ExecutableField("toFloat", ctx.getType(Type("float")), MemPos(0, 0)) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          values.floatValue = values.doubleValue.toFloat()
        }
      }
      "toDouble" -> object : ExecutableField(name, ctx.getType(Type("double")), MemPos(0, 0)) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
        }
      }
      "toString" -> object : ExecutableField(
        "toString",
        ctx.getFunctionDescriptorAsInstance(listOf(), ctx.getType(Type("string")), DummyMemoryAllocatorProvider),
        MemPos(0, 0)
      ) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          val n = values.doubleValue
          values.refValue = object : InstructionWithStackInfo(DOUBLE_TO_STRING_STACK_INFO) {
            override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
              values.refValue = n.toString()
            }
          }
        }
      }
      else -> null
    }
  }

  override fun toString(): String {
    return "DoubleType"
  }
}

object BoolType : PrimitiveTypeInstance {
  override fun field(ctx: TypeContext, name: String, accessFrom: TypeInstance?): Field? {
    return when (name) {
      "toString" -> object : ExecutableField(
        "toString",
        ctx.getFunctionDescriptorAsInstance(listOf(), ctx.getType(Type("string")), DummyMemoryAllocatorProvider),
        MemPos(0, 0)
      ) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          val n = values.boolValue
          values.refValue = object : InstructionWithStackInfo(BOOL_TO_STRING_STACK_INFO) {
            override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
              values.refValue = n.toString()
            }
          }
        }
      }
      else -> null
    }
  }

  override fun toString(): String {
    return "BoolType"
  }
}

object StringType : BuiltInTypeInstance {
  override fun field(ctx: TypeContext, name: String, accessFrom: TypeInstance?): Field? {
    return when (name) {
      "toInt" -> object : ExecutableField(name, IntType, MemPos(0, 0)) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          val str = values.refValue as String
          values.intValue = str.toInt()
        }
      }
      "toLong" -> object : ExecutableField(name, LongType, MemPos(0, 0)) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          val str = values.refValue as String
          values.longValue = str.toLong()
        }
      }
      "toFloat" -> object : ExecutableField(name, FloatType, MemPos(0, 0)) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          val str = values.refValue as String
          values.floatValue = str.toFloat()
        }
      }
      "toDouble" -> object : ExecutableField(name, DoubleType, MemPos(0, 0)) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          val str = values.refValue as String
          values.doubleValue = str.toDouble()
        }
      }
      "toBool" -> object : ExecutableField(name, BoolType, MemPos(0, 0)) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          val str = values.refValue as String
          values.boolValue = str.toBoolean()
        }
      }
      "toString" -> object : ExecutableField(
        "toString",
        ctx.getFunctionDescriptorAsInstance(listOf(), ctx.getType(Type("string")), DummyMemoryAllocatorProvider),
        MemPos(0, 0)
      ) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          val n = values.refValue
          values.refValue = object : InstructionWithStackInfo(STRING_TO_STRING_STACK_INFO) {
            override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
              values.refValue = n
            }
          }
        }
      }
      "indexOf" -> object : ExecutableField(
        name,
        ctx.getFunctionDescriptorAsInstance(
          listOf(ParamInstance(StringType, 0)), IntType,
          FixedMemoryAllocatorProvider(RuntimeMemoryTotal(refTotal = 1))
        ),
        MemPos(0, 0)
      ) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          val str = values.refValue as String
          values.refValue = object : InstructionWithStackInfo(STRING_INDEX_OF_STACK_INFO) {
            override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
              values.intValue = str.indexOf(ctx.getCurrentMem().getRef(0) as String)
            }
          }
        }
      }
      "substring" -> object : ExecutableField(
        name,
        ctx.getFunctionDescriptorAsInstance(
          listOf(ParamInstance(IntType, 0), ParamInstance(IntType, 1)),
          StringType,
          FixedMemoryAllocatorProvider(RuntimeMemoryTotal(intTotal = 2))
        ),
        MemPos(0, 0)
      ) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          val str = values.refValue as String
          values.refValue = object : InstructionWithStackInfo(STRING_SUBSTRING_STACK_INFO) {
            override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
              values.refValue = str.substring(ctx.getCurrentMem().getInt(0), ctx.getCurrentMem().getInt(1))
            }
          }
        }
      }
      "trim" -> object : ExecutableField(
        name,
        ctx.getFunctionDescriptorAsInstance(listOf(), StringType, DummyMemoryAllocatorProvider),
        MemPos(0, 0)
      ) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          val str = values.refValue as String
          values.refValue = object : InstructionWithStackInfo(STRING_TRIM_STACK_INFO) {
            override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
              values.refValue = str.trim()
            }
          }
        }
      }
      "startsWith" -> object : ExecutableField(
        name,
        ctx.getFunctionDescriptorAsInstance(
          listOf(ParamInstance(StringType, 0)), BoolType,
          FixedMemoryAllocatorProvider(RuntimeMemoryTotal(refTotal = 1))
        ),
        MemPos(0, 0)
      ) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          val str = values.refValue as String
          values.refValue = object : InstructionWithStackInfo(STRING_STARTS_WITH_STACK_INFO) {
            override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
              values.boolValue = str.startsWith(ctx.getCurrentMem().getRef(0) as String)
            }
          }
        }
      }
      "endsWith" -> object : ExecutableField(
        name,
        ctx.getFunctionDescriptorAsInstance(
          listOf(ParamInstance(StringType, 0)), BoolType,
          FixedMemoryAllocatorProvider(RuntimeMemoryTotal(refTotal = 1))
        ),
        MemPos(0, 0)
      ) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          val str = values.refValue as String
          values.refValue = object : InstructionWithStackInfo(STRING_ENDS_WITH_STACK_INFO) {
            override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
              values.boolValue = str.endsWith(ctx.getCurrentMem().getRef(0) as String)
            }
          }
        }
      }
      "contains" -> object : ExecutableField(
        name,
        ctx.getFunctionDescriptorAsInstance(
          listOf(ParamInstance(StringType, 0)), BoolType,
          FixedMemoryAllocatorProvider(RuntimeMemoryTotal(refTotal = 1))
        ),
        MemPos(0, 0)
      ) {
        override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
          val str = values.refValue as String
          values.refValue = object : InstructionWithStackInfo(STRING_CONTAINS_STACK_INFO) {
            override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
              values.boolValue = str.contains(ctx.getCurrentMem().getRef(0) as String)
            }
          }
        }
      }
      else -> null
    }
  }

  override fun toString(): String {
    return "StringType"
  }
}

internal val INT_TO_STRING_STACK_INFO = StackInfo("int", "toString", LineCol.EMPTY)
internal val LONG_TO_STRING_STACK_INFO = StackInfo("long", "toString", LineCol.EMPTY)
internal val FLOAT_TO_STRING_STACK_INFO = StackInfo("float", "toString", LineCol.EMPTY)
internal val DOUBLE_TO_STRING_STACK_INFO = StackInfo("double", "toString", LineCol.EMPTY)
internal val BOOL_TO_STRING_STACK_INFO = StackInfo("bool", "toString", LineCol.EMPTY)
internal val STRING_TO_STRING_STACK_INFO = StackInfo("string", "toString", LineCol.EMPTY)
internal val STRING_INDEX_OF_STACK_INFO = StackInfo("string", "indexOf", LineCol.EMPTY)
internal val STRING_SUBSTRING_STACK_INFO = StackInfo("string", "substring", LineCol.EMPTY)
internal val STRING_TRIM_STACK_INFO = StackInfo("string", "trim", LineCol.EMPTY)
internal val STRING_STARTS_WITH_STACK_INFO = StackInfo("string", "startsWith", LineCol.EMPTY)
internal val STRING_ENDS_WITH_STACK_INFO = StackInfo("string", "endsWith", LineCol.EMPTY)
internal val STRING_CONTAINS_STACK_INFO = StackInfo("string", "contains", LineCol.EMPTY)
