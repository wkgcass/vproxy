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

package io.vproxy.dep.vjson.pl.ast

import io.vproxy.dep.vjson.ex.ParserException
import io.vproxy.dep.vjson.pl.inst.Instruction
import io.vproxy.dep.vjson.pl.inst.NoOp
import io.vproxy.dep.vjson.pl.type.TypeContext

data class TemplateTypeInstantiation(val typeName: String, val templateType: Type, val typeParams: List<Type>) : Statement() {
  override fun copy(): TemplateTypeInstantiation {
    val ret = TemplateTypeInstantiation(typeName, templateType.copy(), typeParams.map { it.copy() })
    ret.lineCol = lineCol
    return ret
  }

  override fun functionTerminationCheck(): Boolean {
    return false
  }

  override fun checkAST(ctx: TypeContext) {
    if (ctx.hasTypeInThisContext(Type(typeName))) {
      throw ParserException("type `$typeName` is already defined", lineCol)
    }
    val templateType = this.templateType.check(ctx)
    val astTypeParams = templateType.typeParameters() ?: throw ParserException("type `$templateType` is not a template class", lineCol)

    val typeParams = this.typeParams.map { it.check(ctx) }

    if (astTypeParams.size != typeParams.size) {
      throw ParserException(
        "template type `$templateType` has ${astTypeParams.size} type parameters, but $typeName provides ${typeParams.size}",
        lineCol
      )
    }

    val typeInstance = try {
      templateType.concrete(ctx, typeParams)
    } catch (e: ParserException) {
      throw ParserException("constructing concrete type $typeName failed: ${e.message}", e, this.lineCol)
    }

    ctx.addType(Type(typeName), typeInstance)
  }

  override fun generateInstruction(): Instruction {
    return NoOp()
  }

  override fun toString(indent: Int): String {
    return "let $typeName = { $templateType:" + typeParams.joinToString(", ", prefix = "[", postfix = "]") + " }"
  }

  override fun toString(): String {
    return toString(0)
  }
}
