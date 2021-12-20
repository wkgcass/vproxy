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
import io.vproxy.dep.vjson.pl.ast.ParamType
import io.vproxy.dep.vjson.pl.ast.Type
import io.vproxy.dep.vjson.pl.inst.*
import io.vproxy.dep.vjson.pl.type.*

class StdTypes : Types {
  companion object {
    private val CONSOLE_LOG_STACK_INFO = StackInfo("std.Console", "log", LineCol.EMPTY)
  }

  private val stdObject = ActionContext(RuntimeMemoryTotal(refTotal = 1), null)
  private val consoleObject = ActionContext(RuntimeMemoryTotal(refTotal = 1), null)

  private var outputFunc: ((String) -> Unit)? = null

  init {
    stdObject.getCurrentMem().setRef(0, consoleObject)
    consoleObject.getCurrentMem().setRef(0, object : InstructionWithStackInfo(CONSOLE_LOG_STACK_INFO) {
      override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
        val outputFunc = this@StdTypes.outputFunc
        val str = ctx.getCurrentMem().getRef(0)
        if (outputFunc == null)
          println(str)
        else
          outputFunc(str as String)
      }
    })
  }

  override fun initiateType(ctx: TypeContext, offset: RuntimeMemoryTotal): RuntimeMemoryTotal {
    val stdClass = StdClass()
    ctx.addType(Type("std"), stdClass)
    val consoleClass = ConsoleClass()
    ctx.addType(Type("std.Console"), consoleClass)
    ctx.addVariable(Variable("std", stdClass, modifiable = false, executor = null, MemPos(0, ctx.getMemoryAllocator().nextRefIndex())))
    val iteratorType = TemplateIteratorType()
    ctx.addType(Type("std.Iterator"), iteratorType)
    ctx.addType(Type("std.List"), TemplateListType(iteratorTemplateType = iteratorType))
    val setType = TemplateSetType(iteratorTemplateType = iteratorType)
    ctx.addType(Type("std.Set"), setType)
    val linkedHashSetType = TemplateLinkedHashSetType(iteratorTemplateType = iteratorType)
    ctx.addType(Type("std.LinkedHashSet"), linkedHashSetType)
    ctx.addType(Type("std.Map"), TemplateMapType(templateKeySetType = setType, templateKeySetIteratorType = iteratorType))
    ctx.addType(
      Type("std.LinkedHashMap"),
      TemplateLinkedHashMapType(templateKeySetType = linkedHashSetType, templateKeySetIteratorType = iteratorType)
    )
    return RuntimeMemoryTotal(offset, refTotal = 1)
  }

  override fun initiateValues(ctx: ActionContext, offset: RuntimeMemoryTotal, values: RuntimeMemory?) {
    ctx.getCurrentMem().setRef(offset.refTotal, stdObject)
  }

  fun setOutput(func: (String) -> Unit) {
    this.outputFunc = func
  }
}

class StdClass : TypeInstance {
  override fun field(ctx: TypeContext, name: String, accessFrom: TypeInstance?): Field? {
    return when (name) {
      "console" -> Field("console", ctx.getType(Type("std.Console")), MemPos(0, 0), false, null)
      else -> null
    }
  }
}

class ConsoleClass : TypeInstance {
  override fun field(ctx: TypeContext, name: String, accessFrom: TypeInstance?): Field? {
    return when (name) {
      "log" -> {
        val type =
          ctx.getFunctionDescriptorAsInstance(
            listOf(ParamInstance(ctx.getType(Type("string")), 0)), ctx.getType(Type("void")),
            FixedMemoryAllocatorProvider(RuntimeMemoryTotal(refTotal = 1))
          )
        return Field("log", type, MemPos(0, 0), false, null)
      }
      else -> null
    }
  }
}

class TemplateIteratorType : TypeInstance {
  private val typeParameters = listOf(ParamType("E"))
  override fun typeParameters(): List<ParamType> {
    return typeParameters
  }

  override fun concrete(ctx: TypeContext, typeParams: List<TypeInstance>): TypeInstance {
    return IteratorType(templateType = this, typeParams[0])
  }
}

class TemplateListType(private val iteratorTemplateType: TypeInstance) : TypeInstance {
  private val typeParameters = listOf(ParamType("E"))
  override fun typeParameters(): List<ParamType> {
    return typeParameters
  }

  override fun concrete(ctx: TypeContext, typeParams: List<TypeInstance>): TypeInstance {
    return ListType(this, IteratorType(iteratorTemplateType, typeParams[0]), typeParams[0])
  }
}

class TemplateSetType(private val iteratorTemplateType: TypeInstance) : TypeInstance {
  private val typeParameters = listOf(ParamType("E"))
  override fun typeParameters(): List<ParamType> {
    return typeParameters
  }

  override fun concrete(ctx: TypeContext, typeParams: List<TypeInstance>): TypeInstance {
    return SetType(this, IteratorType(iteratorTemplateType, typeParams[0]), typeParams[0])
  }
}

class TemplateLinkedHashSetType(private val iteratorTemplateType: TypeInstance) : TypeInstance {
  private val typeParameters = listOf(ParamType("E"))
  override fun typeParameters(): List<ParamType> {
    return typeParameters
  }

  override fun concrete(ctx: TypeContext, typeParams: List<TypeInstance>): TypeInstance {
    return object : SetType(this@TemplateLinkedHashSetType, IteratorType(iteratorTemplateType, typeParams[0]), typeParams[0]) {
      override fun newCollection(initialCap: Int): Collection<*> {
        return LinkedHashSet<Any?>()
      }
    }
  }
}

class TemplateMapType(private val templateKeySetType: TypeInstance, private val templateKeySetIteratorType: TypeInstance) : TypeInstance {
  private val typeParameters = listOf(ParamType("K"), ParamType("V"))
  override fun typeParameters(): List<ParamType> {
    return typeParameters
  }

  override fun concrete(ctx: TypeContext, typeParams: List<TypeInstance>): TypeInstance {
    return MapType(
      templateType = this,
      templateKeySetType,
      keySetIteratorType = IteratorType(templateKeySetIteratorType, typeParams[0]),
      typeParams[0], typeParams[1]
    )
  }
}

class TemplateLinkedHashMapType(private val templateKeySetType: TypeInstance, private val templateKeySetIteratorType: TypeInstance) :
  TypeInstance {
  private val typeParameters = listOf(ParamType("K"), ParamType("V"))
  override fun typeParameters(): List<ParamType> {
    return typeParameters
  }

  override fun concrete(ctx: TypeContext, typeParams: List<TypeInstance>): TypeInstance {
    return object : MapType(
      templateType = this@TemplateLinkedHashMapType,
      templateKeySetType,
      keySetIteratorType = IteratorType(templateKeySetIteratorType, typeParams[0]),
      typeParams[0], typeParams[1]
    ) {
      override fun newMap(cap: Int): Map<*, *> {
        return LinkedHashMap<Any?, Any?>(cap)
      }
    }
  }
}
