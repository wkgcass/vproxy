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

import io.vproxy.dep.vjson.pl.ast.ParamType
import io.vproxy.dep.vjson.pl.ast.TemplateClassDefinition
import io.vproxy.dep.vjson.pl.ast.Type

class TemplateClassTypeInstance(val tmpl: TemplateClassDefinition) : TypeInstance {
  override fun typeParameters(): List<ParamType> {
    return tmpl.paramTypes
  }

  override fun concrete(ctx: TypeContext, typeParams: List<TypeInstance>): TypeInstance {
    val ast = tmpl.copy()
    val newCtx = TypeContext(tmpl.getCtx())
    for (i in 0 until ast.paramTypes.size) {
      newCtx.addType(Type(ast.paramTypes[i].name), typeParams[i])
    }
    ast.classDef.checkAST(newCtx)
    val clsTypeInstance = newCtx.getType(Type(ast.classDef.name)) as ClassTypeInstance
    clsTypeInstance._templateType = this
    clsTypeInstance._templateTypeParams = typeParams
    return clsTypeInstance
  }

  override fun toString(): String {
    return "template class ${tmpl.classDef.name}"
  }
}
