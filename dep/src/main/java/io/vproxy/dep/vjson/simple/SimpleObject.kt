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
import io.vproxy.dep.vjson.JSON.String.Companion.stringify
import io.vproxy.dep.vjson.Stringifier
import io.vproxy.dep.vjson.cs.LineCol
import io.vproxy.dep.vjson.parser.TrustedFlag
import io.vproxy.dep.vjson.pl.ScriptifyContext

open class SimpleObject : AbstractSimpleInstance<LinkedHashMap<String, Any?>>, JSON.Object {
  private val map: MutableList<SimpleObjectEntry<JSON.Instance<*>>>
  private var keySet: LinkedHashSet<String>? = null
  private var keyList: List<String>? = null
  private var entryList: List<JSON.ObjectEntry>? = null
  private var fastSingleMap: MutableMap<String, JSON.Instance<*>>? = null
  private fun getFastSingleMap(): MutableMap<String, JSON.Instance<*>> {
    if (fastSingleMap == null) {
      fastSingleMap = HashMap(map.size)
    }
    return fastSingleMap!!
  }

  private var fastMultiMap: MutableMap<String, List<JSON.Instance<*>>>? = null
  private fun getFastMultiMap(): MutableMap<String, List<JSON.Instance<*>>> {
    if (fastMultiMap == null) {
      fastMultiMap = HashMap()
    }
    return fastMultiMap!!
  }

  private val lineCol: LineCol

  /*#ifndef KOTLIN_NATIVE {{ */ @JvmOverloads/*}}*/
  constructor(initMap: Map<String, JSON.Instance<*>>, lineCol: LineCol = LineCol.EMPTY) {
    for ((key, value) in initMap) {
      requireNotNull(key) { "key should not be null" }
      requireNotNull(value) { "value should not be null" }
    }
    map = ArrayList(initMap.size)
    for ((key, value) in initMap) {
      map.add(SimpleObjectEntry(key, value))
    }
    this.lineCol = lineCol
  }

  /*#ifndef KOTLIN_NATIVE {{ */ @JvmOverloads/*}}*/
  constructor(initMap: List<SimpleObjectEntry<JSON.Instance<*>>>, lineCol: LineCol = LineCol.EMPTY) {
    for (entry in initMap) {
      requireNotNull(entry) { "entry should not be null" }
      // requireNotNull(entry.key) { "key should not be null" }
      // null of the key is tested in the constructor of SimpleObjectEntry
      requireNotNull(entry.value) { "value should not be null" }
    }
    map = ArrayList(initMap)
    this.lineCol = lineCol
  }

  protected constructor(initMap: MutableList<SimpleObjectEntry<JSON.Instance<*>>>, flag: TrustedFlag?, lineCol: LineCol) {
    if (flag == null) {
      throw UnsupportedOperationException()
    }
    map = initMap
    this.lineCol = lineCol
  }

  protected constructor(initMap: MutableList<SimpleObjectEntry<JSON.Instance<*>>>, flag: io.vproxy.dep.vjson.util.TrustedFlag?) {
    if (flag == null) {
      throw UnsupportedOperationException()
    }
    map = initMap
    this.lineCol = LineCol.EMPTY
  }

  override fun toJavaObject(): LinkedHashMap<String, Any?> {
    return LinkedHashMap(super.toJavaObject())
  }

  override fun _toJavaObject(): LinkedHashMap<String, Any?> {
    val javaObject = LinkedHashMap<String, Any?>()
    for (entry in map) {
      javaObject[entry.key] = entry.value.toJavaObject()
    }
    return javaObject
  }

  override fun stringify(builder: StringBuilder, sfr: Stringifier) {
    sfr.beforeObjectBegin(builder, this)
    builder.append("{")
    sfr.afterObjectBegin(builder, this)
    var isFirst = true
    for (entry in map) {
      if (isFirst) {
        isFirst = false
      } else {
        sfr.beforeObjectComma(builder, this)
        builder.append(",")
        sfr.afterObjectComma(builder, this)
      }
      sfr.beforeObjectKey(builder, this, entry.key)
      builder.append(stringify(entry.key, sfr.stringOptions()))
      sfr.afterObjectKey(builder, this, entry.key)
      sfr.beforeObjectColon(builder, this)
      builder.append(":")
      sfr.afterObjectColon(builder, this)
      sfr.beforeObjectValue(builder, this, entry.key, entry.value)
      entry.value.stringify(builder, sfr)
      sfr.afterObjectValue(builder, this, entry.key, entry.value)
    }
    sfr.beforeObjectEnd(builder, this)
    builder.append("}")
    sfr.afterObjectEnd(builder, this)
  }

  override fun scriptify(builder: StringBuilder, ctx: ScriptifyContext) {
    val isTopLevel = ctx.isTopLevel
    ctx.unsetTopLevel()
    if (map.isEmpty()) {
      builder.append("{ }")
      return
    }
    if (map.size <= 2) {

      builder.append("{ ")
      val ite = map.iterator()
      var isFirst = true
      while (ite.hasNext()) {
        if (isFirst) {
          isFirst = false
        } else {
          builder.append(", ")
        }
        scriptifyEntry(ite, builder, ctx)
      }
      builder.append(" }")

    } else {

      builder.append("{\n")
      if (!isTopLevel) {
        ctx.increaseIndent()
      }

      val ite = map.iterator()
      var addIndent = true
      while (ite.hasNext()) {
        if (addIndent) {
          ctx.appendIndent(builder)
        }
        val finished = scriptifyEntry(ite, builder, ctx)
        addIndent = finished
        if (addIndent) {
          builder.append("\n")
        }
      }

      if (!isTopLevel) {
        ctx.decreaseIndent()
      }
      ctx.appendIndent(builder)
      builder.append("}")

    }
    if (isTopLevel) {
      builder.append("\n")
    }
  }

  private fun scriptifyEntry(ite: Iterator<SimpleObjectEntry<JSON.Instance<*>>>, builder: StringBuilder, ctx: ScriptifyContext): Boolean {
    val entry = ite.next()
    val key = entry.key
    val value = entry.value

    when (key) {
      "function" -> {
        builder.append("function ")
        return appendNameAndParams(value, ite, builder, ctx)
      }
      "class" -> {
        builder.append("class ")
        return appendNameAndParams(value, ite, builder, ctx)
      }
      "template" -> {
        builder.append("template ")
        if (value !is JSON.Object) {
          value.scriptify(builder, ctx)
          return true
        }
        builder.append("{ ")
        var isFirst = true
        for (e in value.entryList()) {
          if (isFirst) isFirst = false
          else builder.append(", ")
          scriptifyKey(e.key, builder)
          if (e.value !is JSON.Null) {
            e.value.scriptify(builder, ctx)
          }
        }
        builder.append(" } ")
        return false
      }
      "var" -> {
        builder.append("var ")
        if (value !is JSON.Null) {
          value.scriptify(builder, ctx)
          return true
        }
        if (!ite.hasNext()) {
          return true
        }
        val kv = ite.next()
        scriptifyKey(kv.key, builder)
        builder.append(" = ")
        kv.value.scriptify(builder, ctx)
        return true
      }
      "if" -> {
        builder.append("if: ")
        value.scriptify(builder, ctx)
        builder.append("; ")
        return false
      }
      "for" -> {
        builder.append("for: ")
        if (value !is JSON.Array) {
          value.scriptify(builder, ctx)
          return true
        }
        builder.append("[")
        var isFirst = true
        for (i in 0 until value.length()) {
          if (isFirst) isFirst = false
          else builder.append("; ")
          val e = value[i]
          e.scriptify(builder, ctx)
        }
        builder.append("] ")
        return false
      }
      "while" -> {
        builder.append("while: ")
        value.scriptify(builder, ctx)
        builder.append("; ")
        return false
      }
      "return" -> {
        builder.append("return: ")
        value.scriptify(builder, ctx)
        return true
      }
      "throw" -> {
        builder.append("throw: ")
        value.scriptify(builder, ctx)
        return true
      }
      "null" -> {
        builder.append("null: ")
        value.scriptify(builder, ctx)
        return true
      }
      else -> {
        scriptifyKey(key, builder)
        if (value is JSON.Null) {
          builder.append(" ")
          return false
        }
        if (value is JSON.Object) {
          if (value.size() == 1 && value.keyList()[0] == "null") {
            builder.append(" = ")
          } else {
            builder.append(" ")
          }
        } else if (value is JSON.Array) {
          builder.append(":")
        } else {
          builder.append(" = ")
        }
        value.scriptify(builder, ctx)
        return true
      }
    }
  }

  private fun appendNameAndParams(
    value: JSON.Instance<*>,
    ite: Iterator<SimpleObjectEntry<JSON.Instance<*>>>,
    builder: StringBuilder,
    ctx: ScriptifyContext
  ): Boolean {
    if (value !is JSON.Null) {
      value.scriptify(builder, ctx)
      return true
    }
    if (!ite.hasNext()) {
      return true
    }
    val nameAndParams = ite.next()
    val name = nameAndParams.key
    scriptifyKey(name, builder)
    builder.append(" ")
    val params = nameAndParams.value
    if (params !is JSON.Object) {
      builder.append("= ")
      params.scriptify(builder, ctx)
      return true
    }
    appendParams(params, builder, ctx)
    return false
  }

  private fun appendParams(params: JSON.Object, builder: StringBuilder, ctx: ScriptifyContext) {
    builder.append("{")
    var isFirst = true
    for (e in params.entryList()) {
      if (isFirst) isFirst = false
      else builder.append(", ")
      scriptifyKey(e.key, builder)
      builder.append(": ")
      e.value.scriptify(builder, ctx)
    }
    builder.append("} ")
  }

  private fun scriptifyKey(key: String, builder: StringBuilder) {
    builder.append(ScriptifyContext.scriptifyString(key))
  }

  override fun _toString(): String {
    val sb = StringBuilder()
    sb.append("Object{")
    var isFirst = true
    for (entry in map) {
      if (isFirst) isFirst = false
      else sb.append(", ")
      sb.append(entry.key).append(":").append(entry.value)
    }
    sb.append("}")
    return sb.toString()
  }

  private fun _keySet(): LinkedHashSet<String> {
    if (keySet == null) {
      val set = LinkedHashSet<String>()
      for (entry in map) {
        set.add(entry.key)
      }
      keySet = set
    }
    return keySet!!
  }

  override fun keySet(): LinkedHashSet<String> {
    return LinkedHashSet(_keySet())
  }

  override fun keyList(): List<String> {
    if (keyList == null) {
      val list: MutableList<String> = ArrayList(map.size)
      for (entry in map) {
        list.add(entry.key)
      }
      keyList = list
    }
    return ArrayList(keyList!!)
  }

  override fun entryList(): List<JSON.ObjectEntry> {
    if (entryList == null) {
      val list: MutableList<JSON.ObjectEntry> = ArrayList(map.size)
      for (entry in map) {
        list.add(JSON.ObjectEntry(entry.key, entry.value, entry.lineCol))
      }
      entryList = list
    }
    return ArrayList(entryList!!)
  }

  override fun size(): Int {
    return map.size
  }

  override fun containsKey(key: String): Boolean {
    return _keySet().contains(key)
  }

  /* #ifndef KOTLIN_NATIVE {{ */ @Throws(NoSuchElementException::class) // }}
  override fun get(key: String): JSON.Instance<*> {
    val fastMap = getFastSingleMap()
    if (fastMap.containsKey(key)) {
      return fastMap[key]!!
    }
    var inst: JSON.Instance<*>? = null
    for (entry in map) {
      if (entry.key == key) {
        inst = entry.value
        break
      }
    }
    if (inst == null) throw NoSuchElementException()
    fastMap[key] = inst
    return inst
  }

  override fun getAll(key: String): List<JSON.Instance<*>> {
    if (!_keySet().contains(key)) {
      throw NoSuchElementException()
    }

    val fastMap = getFastMultiMap()
    if (fastMap.containsKey(key)) {
      return fastMap[key]!!
    }
    val ret: MutableList<JSON.Instance<*>> = ArrayList()
    for (entry in map) {
      if (entry.key == key) {
        ret.add(entry.value)
      }
    }
    val immutableRet: List<JSON.Instance<*>> = ret
    fastMap[key] = immutableRet
    return immutableRet
  }

  override fun lineCol(): LineCol {
    return lineCol
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is JSON.Object) return false
    if (other.keySet() != _keySet()) return false
    for (key in keySet()) {
      if (other[key] != get(key)) return false
    }
    return true
  }

  override fun hashCode(): Int {
    return map.hashCode()
  }
}
