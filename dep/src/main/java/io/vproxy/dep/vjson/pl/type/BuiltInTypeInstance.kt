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
      "toInt" -> object : ExecutableField(name, ctx.getType(Type("int"))) {
        override fun execute(ctx: ActionContext, exec: Execution) {
        }
      }
      "toLong" -> object : ExecutableField(name, ctx.getType(Type("long"))) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          exec.values.longValue = exec.values.intValue.toLong()
        }
      }
      "toFloat" -> object : ExecutableField("toFloat", ctx.getType(Type("float"))) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          exec.values.floatValue = exec.values.intValue.toFloat()
        }
      }
      "toDouble" -> object : ExecutableField(name, ctx.getType(Type("double"))) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          exec.values.doubleValue = exec.values.intValue.toDouble()
        }
      }
      "toString" -> object : ExecutableField(
        "toString",
        ctx.getFunctionDescriptorAsInstance(listOf(), ctx.getType(Type("string")), DummyMemoryAllocatorProvider)
      ) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val n = exec.values.intValue
          exec.values.refValue = object : InstructionWithStackInfo(INT_TO_STRING_STACK_INFO) {
            override fun execute0(ctx: ActionContext, exec: Execution) {
              exec.values.refValue = n.toString()
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
      "toInt" -> object : ExecutableField(name, ctx.getType(Type("int"))) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          exec.values.intValue = exec.values.longValue.toInt()
        }
      }
      "toLong" -> object : ExecutableField(name, ctx.getType(Type("long"))) {
        override fun execute(ctx: ActionContext, exec: Execution) {
        }
      }
      "toFloat" -> object : ExecutableField("toFloat", ctx.getType(Type("float"))) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          exec.values.floatValue = exec.values.longValue.toFloat()
        }
      }
      "toDouble" -> object : ExecutableField(name, ctx.getType(Type("double"))) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          exec.values.doubleValue = exec.values.longValue.toDouble()
        }
      }
      "toString" -> object : ExecutableField(
        "toString",
        ctx.getFunctionDescriptorAsInstance(listOf(), ctx.getType(Type("string")), DummyMemoryAllocatorProvider)
      ) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val n = exec.values.longValue
          exec.values.refValue = object : InstructionWithStackInfo(LONG_TO_STRING_STACK_INFO) {
            override fun execute0(ctx: ActionContext, exec: Execution) {
              exec.values.refValue = n.toString()
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
      "toInt" -> object : ExecutableField("toInt", ctx.getType(Type("int"))) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          exec.values.intValue = exec.values.floatValue.toInt()
        }
      }
      "toLong" -> object : ExecutableField("toLong", ctx.getType(Type("long"))) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          exec.values.longValue = exec.values.floatValue.toLong()
        }
      }
      "toFloat" -> object : ExecutableField(name, ctx.getType(Type("float"))) {
        override fun execute(ctx: ActionContext, exec: Execution) {
        }
      }
      "toDouble" -> object : ExecutableField("toDouble", ctx.getType(Type("double"))) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          exec.values.doubleValue = exec.values.floatValue.toDouble()
        }
      }
      "toString" -> object : ExecutableField(
        "toString",
        ctx.getFunctionDescriptorAsInstance(listOf(), ctx.getType(Type("string")), DummyMemoryAllocatorProvider)
      ) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val n = exec.values.floatValue
          exec.values.refValue = object : InstructionWithStackInfo(FLOAT_TO_STRING_STACK_INFO) {
            override fun execute0(ctx: ActionContext, exec: Execution) {
              exec.values.refValue = n.toString()
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
      "toInt" -> object : ExecutableField("toInt", ctx.getType(Type("int"))) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          exec.values.intValue = exec.values.doubleValue.toInt()
        }
      }
      "toLong" -> object : ExecutableField("toLong", ctx.getType(Type("long"))) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          exec.values.longValue = exec.values.doubleValue.toLong()
        }
      }
      "toFloat" -> object : ExecutableField("toFloat", ctx.getType(Type("float"))) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          exec.values.floatValue = exec.values.doubleValue.toFloat()
        }
      }
      "toDouble" -> object : ExecutableField(name, ctx.getType(Type("double"))) {
        override fun execute(ctx: ActionContext, exec: Execution) {
        }
      }
      "toString" -> object : ExecutableField(
        "toString",
        ctx.getFunctionDescriptorAsInstance(listOf(), ctx.getType(Type("string")), DummyMemoryAllocatorProvider)
      ) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val n = exec.values.doubleValue
          exec.values.refValue = object : InstructionWithStackInfo(DOUBLE_TO_STRING_STACK_INFO) {
            override fun execute0(ctx: ActionContext, exec: Execution) {
              exec.values.refValue = n.toString()
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
        ctx.getFunctionDescriptorAsInstance(listOf(), ctx.getType(Type("string")), DummyMemoryAllocatorProvider)
      ) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val n = exec.values.boolValue
          exec.values.refValue = object : InstructionWithStackInfo(BOOL_TO_STRING_STACK_INFO) {
            override fun execute0(ctx: ActionContext, exec: Execution) {
              exec.values.refValue = n.toString()
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
      "toInt" -> object : ExecutableField(name, IntType) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val str = exec.values.refValue as String
          exec.values.intValue = str.toInt()
        }
      }
      "toLong" -> object : ExecutableField(name, LongType) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val str = exec.values.refValue as String
          exec.values.longValue = str.toLong()
        }
      }
      "toFloat" -> object : ExecutableField(name, FloatType) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val str = exec.values.refValue as String
          exec.values.floatValue = str.toFloat()
        }
      }
      "toDouble" -> object : ExecutableField(name, DoubleType) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val str = exec.values.refValue as String
          exec.values.doubleValue = str.toDouble()
        }
      }
      "toBool" -> object : ExecutableField(name, BoolType) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val str = exec.values.refValue as String
          exec.values.boolValue = str.toBoolean()
        }
      }
      "toString" -> object : ExecutableField(
        "toString",
        ctx.getFunctionDescriptorAsInstance(listOf(), ctx.getType(Type("string")), DummyMemoryAllocatorProvider)
      ) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val n = exec.values.refValue
          exec.values.refValue = object : InstructionWithStackInfo(STRING_TO_STRING_STACK_INFO) {
            override fun execute0(ctx: ActionContext, exec: Execution) {
              exec.values.refValue = n
            }
          }
        }
      }
      "indexOf" -> object : ExecutableField(
        name,
        ctx.getFunctionDescriptorAsInstance(
          listOf(ParamInstance("sub", StringType, 0)), IntType,
          FixedMemoryAllocatorProvider(RuntimeMemoryTotal(refTotal = 1))
        )
      ) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val str = exec.values.refValue as String
          exec.values.refValue = object : InstructionWithStackInfo(STRING_INDEX_OF_STACK_INFO) {
            override fun execute0(ctx: ActionContext, exec: Execution) {
              exec.values.intValue = str.indexOf(ctx.getCurrentMem().getRef(0) as String)
            }
          }
        }
      }
      "substring" -> object : ExecutableField(
        name,
        ctx.getFunctionDescriptorAsInstance(
          listOf(ParamInstance("fromInclusive", IntType, 0), ParamInstance("toExclusive", IntType, 1)),
          StringType,
          FixedMemoryAllocatorProvider(RuntimeMemoryTotal(intTotal = 2))
        )
      ) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val str = exec.values.refValue as String
          exec.values.refValue = object : InstructionWithStackInfo(STRING_SUBSTRING_STACK_INFO) {
            override fun execute0(ctx: ActionContext, exec: Execution) {
              exec.values.refValue = str.substring(ctx.getCurrentMem().getInt(0), ctx.getCurrentMem().getInt(1))
            }
          }
        }
      }
      "trim" -> object : ExecutableField(
        name,
        ctx.getFunctionDescriptorAsInstance(listOf(), StringType, DummyMemoryAllocatorProvider)
      ) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val str = exec.values.refValue as String
          exec.values.refValue = object : InstructionWithStackInfo(STRING_TRIM_STACK_INFO) {
            override fun execute0(ctx: ActionContext, exec: Execution) {
              exec.values.refValue = str.trim()
            }
          }
        }
      }
      "startsWith" -> object : ExecutableField(
        name,
        ctx.getFunctionDescriptorAsInstance(
          listOf(ParamInstance("prefix", StringType, 0)), BoolType,
          FixedMemoryAllocatorProvider(RuntimeMemoryTotal(refTotal = 1))
        )
      ) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val str = exec.values.refValue as String
          exec.values.refValue = object : InstructionWithStackInfo(STRING_STARTS_WITH_STACK_INFO) {
            override fun execute0(ctx: ActionContext, exec: Execution) {
              exec.values.boolValue = str.startsWith(ctx.getCurrentMem().getRef(0) as String)
            }
          }
        }
      }
      "endsWith" -> object : ExecutableField(
        name,
        ctx.getFunctionDescriptorAsInstance(
          listOf(ParamInstance("suffix", StringType, 0)), BoolType,
          FixedMemoryAllocatorProvider(RuntimeMemoryTotal(refTotal = 1))
        )
      ) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val str = exec.values.refValue as String
          exec.values.refValue = object : InstructionWithStackInfo(STRING_ENDS_WITH_STACK_INFO) {
            override fun execute0(ctx: ActionContext, exec: Execution) {
              exec.values.boolValue = str.endsWith(ctx.getCurrentMem().getRef(0) as String)
            }
          }
        }
      }
      "contains" -> object : ExecutableField(
        name,
        ctx.getFunctionDescriptorAsInstance(
          listOf(ParamInstance("sub", StringType, 0)), BoolType,
          FixedMemoryAllocatorProvider(RuntimeMemoryTotal(refTotal = 1))
        )
      ) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val str = exec.values.refValue as String
          exec.values.refValue = object : InstructionWithStackInfo(STRING_CONTAINS_STACK_INFO) {
            override fun execute0(ctx: ActionContext, exec: Execution) {
              exec.values.boolValue = str.contains(ctx.getCurrentMem().getRef(0) as String)
            }
          }
        }
      }
      "length" -> object : ExecutableField(name, IntType) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val str = exec.values.refValue as String
          exec.values.intValue = str.length
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
