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
package vjson.simple

import vjson.JSON
import vjson.JSON.String.Companion.stringify
import vjson.Stringifier
import vjson.parser.TrustedFlag

open class SimpleObject : AbstractSimpleInstance<LinkedHashMap<String, Any?>>, JSON.Object {
  private val map: MutableList<SimpleObjectEntry<JSON.Instance<*>>>
  private var keySet: LinkedHashSet<String>? = null
  private var keyList: List<String>? = null
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

  constructor(initMap: Map<String, JSON.Instance<*>>) {
    for ((key, value) in initMap) {
      requireNotNull(key) { "key should not be null" }
      requireNotNull(value) { "value should not be null" }
    }
    map = ArrayList(initMap.size)
    for ((key, value) in initMap) {
      map.add(SimpleObjectEntry(key, value))
    }
  }

  constructor(initMap: List<SimpleObjectEntry<JSON.Instance<*>>>) {
    for (entry in initMap) {
      requireNotNull(entry) { "entry should not be null" }
      // requireNotNull(entry.key) { "key should not be null" }
      // null of the key is tested in the constructor of SimpleObjectEntry
      requireNotNull(entry.value) { "value should not be null" }
    }
    map = ArrayList(initMap)
  }

  protected constructor(initMap: MutableList<SimpleObjectEntry<JSON.Instance<*>>>, flag: TrustedFlag?) {
    if (flag == null) {
      throw UnsupportedOperationException()
    }
    map = initMap
  }

  protected constructor(initMap: MutableList<SimpleObjectEntry<JSON.Instance<*>>>, flag: vjson.util.TrustedFlag?) {
    if (flag == null) {
      throw UnsupportedOperationException()
    }
    map = initMap
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
      builder.append(stringify(entry.key))
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

  override fun size(): Int {
    return map.size
  }

  override fun containsKey(key: String): Boolean {
    return _keySet().contains(key)
  }

  @Throws(NoSuchElementException::class)
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
