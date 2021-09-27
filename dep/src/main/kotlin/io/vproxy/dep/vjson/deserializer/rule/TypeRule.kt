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
package io.vproxy.dep.vjson.deserializer.rule

import kotlin.reflect.KClass

class TypeRule<T : Any> : Rule<T> {
  val defaultRule: ObjectRule<out T>?

  private val rules: MutableMap<String, ObjectRule<out T>> = LinkedHashMap()

  constructor() {
    defaultRule = null
  }

  constructor(defaultTypeName: String, defaultRule: ObjectRule<out T>) {
    this.defaultRule = defaultRule
    rules[defaultTypeName] = defaultRule
  }

  constructor(kClass: KClass<*>, defaultRule: ObjectRule<out T>) : this(kClass.qualifiedName!!, defaultRule)

  fun type(typeName: String, rule: ObjectRule<out T>): TypeRule<T> {
    rules[typeName] = rule
    return this
  }

  // for kotlin
  fun type(typeName: String, ruleFunc: () -> ObjectRule<out T>): TypeRule<T> = type(typeName, ruleFunc())

  fun type(kClass: KClass<out T>, rule: ObjectRule<out T>): TypeRule<T> {
    return type(kClass.qualifiedName!!, rule)
  }

  // for kotlin
  fun type(kClass: KClass<out T>, ruleFunc: () -> ObjectRule<out T>): TypeRule<T> = type(kClass, ruleFunc())

  fun getRule(typeName: String): ObjectRule<out T>? {
    return rules[typeName]
  }

  override fun toString(sb: StringBuilder, processedListsOrObjects: MutableSet<Rule<*>>) {
    if (!processedListsOrObjects.add(this)) {
      sb.append("TypeRule{...recursive...}")
      return
    }
    sb.append("TypeRule{")
    var isFirst = true
    for ((key, value) in rules) {
      if (isFirst) {
        isFirst = false
      } else {
        sb.append(",")
      }
      sb.append("@type[").append(key)
      if (value == defaultRule) {
        sb.append("*")
      }
      sb.append("]=>")
      value.toString(sb, processedListsOrObjects)
    }
    sb.append("}")
  }
}
