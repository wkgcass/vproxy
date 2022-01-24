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

data class Modifiers(var modifiers: Int) {
  fun isPublic(): Boolean {
    return modifiers.and(ModifierEnum.PUBLIC.num) != 0
  }

  fun isPrivate(): Boolean {
    return modifiers.and(ModifierEnum.PRIVATE.num) != 0
  }

  fun isConst(): Boolean {
    return modifiers.and(ModifierEnum.CONST.num) != 0
  }

  fun isExecutable(): Boolean {
    return modifiers.and(ModifierEnum.EXECUTABLE.num) != 0
  }

  override fun toString(): String {
    val sb = StringBuilder()
    if (isPublic()) {
      sb.append("public")
    }
    if (isPrivate()) {
      if (sb.isNotEmpty()) {
        sb.append(" ")
      }
      sb.append("private")
    }
    if (isConst()) {
      if (sb.isNotEmpty()) {
        sb.append(" ")
      }
      sb.append("const")
    }
    if (isExecutable()) {
      if (sb.isNotEmpty()) {
        sb.append(" ")
      }
      sb.append("executable")
    }
    return sb.toString()
  }

  fun toStringWithSpace(): String {
    val s = toString()
    return if (s.isEmpty()) "" else "$s "
  }
}

enum class ModifierEnum(val str: String, val num: Int) {
  PUBLIC("public", 0x00000001),
  PRIVATE("private", 0x00000002),
  CONST("const", 0x00000004),
  EXECUTABLE("executable", 0x00000008),
  ;

  override fun toString(): String {
    return str
  }

  companion object {
    fun isModifier(key: String): Boolean {
      return key == "public" || key == "private" || key == "const" || key == "executable"
    }
  }
}
