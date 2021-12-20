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
package io.vproxy.dep.vjson.util

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.simple.*
import kotlin.reflect.KClass

class Transformer {
  private val rules: MutableMap<KClass<*>, (Any) -> JSON.Instance<*>> = LinkedHashMap()

  fun <T : Any> addRule(type: KClass<T>, func: (T) -> JSON.Instance<*>): Transformer {
    @Suppress("UNCHECKED_CAST")
    rules[type] = func as (Any) -> JSON.Instance<*>
    return this
  }

  fun removeRule(type: KClass<*>): Transformer {
    rules.remove(type)
    return this
  }

  fun transform(input: Any?): JSON.Instance<*> {
    if (input == null) return SimpleNull()
    if (input is JSON.Instance<*>) {
      return input
    }
    if (input is Collection<*>) {
      val list: MutableList<JSON.Instance<*>> = ArrayList(input.size)
      for (o in input) {
        list.add(transform(o))
      }
      return object : SimpleArray(list, TrustedFlag.FLAG) {}
    }
    if (input is Map<*, *>) {
      val map: MutableList<SimpleObjectEntry<JSON.Instance<*>>> = ArrayList(input.size)
      for (key in input.keys) {
        require(key is String) { "keys of map should be String" }
        map.add(SimpleObjectEntry((key as String?)!!, transform(input[key])))
      }
      return object : SimpleObject(map, TrustedFlag.FLAG) {}
    }
    for ((key, value) in rules) {
      if (key.isInstance(input)) {
        return value(input)
      }
    }
    throw IllegalArgumentException("unknown input type: " + input::class./* #ifdef KOTLIN_JS {{ simpleName }} else {{ */qualifiedName/* }} */)
  }

  init {
    this.addRule(Boolean::class) { value -> SimpleBool(value) }
      .addRule(Int::class) { value -> SimpleInteger(value) }
      .addRule(Long::class) { value -> SimpleLong(value) }
      .addRule(Double::class) { value -> SimpleDouble(value) }
      .addRule(Float::class) { value -> SimpleDouble(value.toDouble()) }
      .addRule(Number::class) { value -> SimpleInteger(value.toInt()) }
      .addRule(String::class) { value -> SimpleString(value) }
      .addRule(Char::class) { value -> SimpleString(value.toString()) }
  }
}
