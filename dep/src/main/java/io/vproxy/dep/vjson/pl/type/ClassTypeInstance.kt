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

import io.vproxy.dep.vjson.pl.ast.*

class ClassTypeInstance(val cls: ClassDefinition) : TypeInstance {
  private val constructorParams: List<Param>
  private val constructorFields: MutableMap<String, Param> = HashMap()
  private val fields: MutableMap<String, VariableDefinition> = HashMap()
  private val functions: MutableMap<String, FunctionDefinition> = HashMap()
  var _templateType: TemplateClassTypeInstance? = null
  var _templateTypeParams: List<TypeInstance>? = null

  init {
    constructorParams = cls.params
    for (param in constructorParams) {
      constructorFields[param.name] = param
    }
    for (stmt in cls.code) {
      if (stmt is VariableDefinition) {
        fields[stmt.name] = stmt
      } else if (stmt is FunctionDefinition) {
        functions[stmt.name] = stmt
      }
    }
  }

  override fun constructor(ctx: TypeContext): FunctionDescriptor {
    return ctx.getFunctionDescriptor(
      constructorParams.map { ParamInstance(it.typeInstance(), it.memIndex) },
      ctx.getType(Type("void")),
      cls
    )
  }

  @Suppress("LiftReturnOrAssignment")
  override fun field(ctx: TypeContext, name: String, accessFrom: TypeInstance?): Field? {
    if (accessFrom == this) {
      val consField = constructorFields[name]
      if (consField != null) {
        return Field(
          consField.name, consField.typeInstance(), MemPos(cls.getMemDepth() + 1, consField.memIndex),
          modifiable = true, executor = null
        )
      }
    }
    val field = fields[name]
    if (field != null) {
      if (field.modifiers.isPublic() || accessFrom == this) {
        if (field.modifiers.isExecutable()) {
          return Field(
            name, field.typeInstance(), field.getMemPos(),
            !field.modifiers.isConst() && !field.modifiers.isExecutable(),
            Pair(field.value.typeInstance().functionDescriptor(field.getCtx())!!, field.value.generateInstruction())
          )
        } else {
          return Field(
            name, field.typeInstance(), field.getMemPos(),
            !field.modifiers.isConst() && !field.modifiers.isExecutable(), null
          )
        }
      } else {
        return null
      }
    }
    val func = functions[name]
    if (func != null) {
      if (!func.modifiers.isPrivate() || accessFrom == this) {
        return Field(name, FunctionDescriptorTypeInstance(func.descriptor(ctx)), func.getMemPos(), modifiable = false, executor = null)
      } else {
        return null
      }
    }
    return null
  }

  override fun templateType(): TypeInstance? {
    return _templateType
  }

  override fun templateTypeParams(): List<TypeInstance>? {
    return _templateTypeParams
  }

  override fun toString(): String {
    return "class ${cls.name}"
  }
}
