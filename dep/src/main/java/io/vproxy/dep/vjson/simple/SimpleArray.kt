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
package io.vproxy.dep.vjson.simple

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.Stringifier
import io.vproxy.dep.vjson.cs.LineCol
import io.vproxy.dep.vjson.parser.TrustedFlag
import io.vproxy.dep.vjson.pl.ScriptifyContext

open class SimpleArray : AbstractSimpleInstance<List<*>>, JSON.Array {
  private val list: List<JSON.Instance<*>>
  private val lineCol: LineCol

  constructor(vararg list: JSON.Instance<*>) : this(listOf(*list))
  constructor(lineCol: LineCol, vararg list: JSON.Instance<*>) : this(listOf(*list), lineCol)

  /*#ifndef KOTLIN_NATIVE {{ */ @JvmOverloads/*}}*/
  constructor(list: List<JSON.Instance<*>>, lineCol: LineCol = LineCol.EMPTY) {
    for (inst in list) {
      requireNotNull(inst) { "element should not be null" }
    }
    this.list = ArrayList(list)
    this.lineCol = lineCol
  }

  protected constructor(list: List<JSON.Instance<*>>, flag: TrustedFlag?, lineCol: LineCol) {
    if (flag == null) {
      throw UnsupportedOperationException()
    }
    this.list = list
    this.lineCol = lineCol
  }

  protected constructor(list: List<JSON.Instance<*>>, flag: io.vproxy.dep.vjson.util.TrustedFlag?) {
    if (flag == null) {
      throw UnsupportedOperationException()
    }
    this.list = list
    this.lineCol = LineCol.EMPTY
  }

  override fun toJavaObject(): List<Any?> {
    return ArrayList(super.toJavaObject())
  }

  override fun _toJavaObject(): List<Any?> {
    val javaObject: MutableList<Any?> = ArrayList()
    for (inst in list) {
      javaObject.add(inst.toJavaObject())
    }
    return javaObject
  }

  override fun stringify(builder: StringBuilder, sfr: Stringifier) {
    sfr.beforeArrayBegin(builder, this)
    builder.append("[")
    sfr.afterArrayBegin(builder, this)
    if (list.isNotEmpty()) {
      val inst = list[0]
      sfr.beforeArrayValue(builder, this, inst)
      inst.stringify(builder, sfr)
      sfr.afterArrayValue(builder, this, inst)
    }
    for (i in 1 until list.size) {
      sfr.beforeArrayComma(builder, this)
      builder.append(",")
      sfr.afterArrayComma(builder, this)
      val inst = list[i]
      sfr.beforeArrayValue(builder, this, inst)
      inst.stringify(builder, sfr)
      sfr.afterArrayValue(builder, this, inst)
    }
    sfr.beforeArrayEnd(builder, this)
    builder.append("]")
    sfr.afterArrayEnd(builder, this)
  }

  override fun scriptify(builder: StringBuilder, ctx: ScriptifyContext) {
    if (list.size <= 5) {

      builder.append("[")
      var isFirst = true
      for (e in list) {
        if (isFirst) {
          isFirst = false
        } else {
          builder.append(", ")
        }
        e.scriptify(builder, ctx)
      }
      builder.append("]")

    } else {

      builder.append("[\n")
      ctx.increaseIndent()

      for (e in list) {
        ctx.appendIndent(builder)
        e.scriptify(builder, ctx)
        builder.append("\n")
      }

      ctx.decreaseIndent()
      ctx.appendIndent(builder)
      builder.append("]")

    }
  }

  override fun _toString(): String {
    val sb = StringBuilder()
    sb.append("Array[")
    if (list.isNotEmpty()) {
      sb.append(list[0])
    }
    for (i in 1 until list.size) {
      sb.append(", ").append(list[i])
    }
    sb.append("]")
    return sb.toString()
  }

  override fun length(): Int {
    return list.size
  }

  /* #ifndef KOTLIN_NATIVE {{ */ @Throws(IndexOutOfBoundsException::class) // }}
  override fun get(idx: Int): JSON.Instance<*> {
    return list[idx]
  }

  override fun lineCol(): LineCol {
    return lineCol
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is JSON.Array) return false
    if (other.length() != length()) return false
    val len = length()
    for (i in 0 until len) {
      if (other[i] != get(i)) return false
    }
    return true
  }

  override fun hashCode(): Int {
    return list.hashCode()
  }
}
