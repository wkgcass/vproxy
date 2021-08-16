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
package vjson.deserializer.rule

import vjson.util.CastUtils.cast
import vjson.util.functional.`BiConsumer$`
import vjson.util.functional.`TriConsumer$`

open class ObjectRule<O : Any> : Rule<O> {
  val construct: () -> Any
  val build: (Any) -> Any
  protected val rules: MutableMap<String, ObjectField<Any, *>> = LinkedHashMap()
  val extraRules: MutableList<(O, String, Any?) -> Unit> = ArrayList()
    get

  // for both java and kotlin
  constructor(construct: () -> O) {
    this.construct = construct
    this.build = { it }
  }

  // for both java and kotlin with super rule
  constructor(construct: () -> O, superRule: ObjectRule<in O>) : this(construct, superRule, {})

  // with rules constructed
  constructor(construct: () -> O, f: ObjectRule<O>.() -> Unit) : this(construct) {
    f(this)
  }

  // with super rule and rules constructed
  constructor(construct: () -> O, superRule: ObjectRule<in O>, f: ObjectRule<O>.() -> Unit) : this(construct) {
    for ((key, value) in superRule.rules) {
      @Suppress("UNCHECKED_CAST")
      rules[key] = cast(value)
    }
    extraRules.addAll(superRule.extraRules)
    f(this)
  }

  // for builder
  private constructor(
    construct: () -> Any,
    superRule: ObjectRule<Any>?,
    build: (Any) -> Any,
    @Suppress("UNUSED_PARAMETER") foo: Int
  ) {
    this.construct = construct
    this.build = build
    if (superRule != null) {
      for ((key, value) in superRule.rules) {
        rules[key] = cast(value)
      }
    }
  }

  companion object {
    // for both java and kotlin
    /*#ifndef KOTLIN_NATIVE {{ */@JvmStatic/*}}*/
    fun <T_OUTPUT : Any, T_BUILDER : Any> builder(
      construct: () -> T_BUILDER,
      build: T_BUILDER.() -> T_OUTPUT,
      f: ObjectRule<T_BUILDER>.() -> ObjectRule<T_BUILDER>
    ): BuilderRule<T_OUTPUT> = builder(construct, null, build, f)

    // for both java and kotlin
    /*#ifndef KOTLIN_NATIVE {{ */@JvmStatic/*}}*/
    fun <T_OUTPUT : Any, T_BUILDER : Any> builder(
      construct: () -> T_BUILDER,
      superRule: BuilderRule<in T_OUTPUT>?,
      build: T_BUILDER.() -> T_OUTPUT,
      f: ObjectRule<T_BUILDER>.() -> ObjectRule<T_BUILDER>
    ): BuilderRule<T_OUTPUT> {
      val tmp = ObjectRule(construct)
      f(tmp)
      val ret = BuilderRule<T_OUTPUT>(
        construct,
        if (superRule == null) null else cast<ObjectRule<Any>>(superRule),
        cast(build)
      )
      for ((key, value) in tmp.rules) {
        ret.rules[key] = value
      }
      return ret
    }
  }

  class BuilderRule<O : Any>(
    construct: () -> Any,
    superRule: ObjectRule<Any>?,
    build: (Any) -> Any
  ) : ObjectRule<O>(construct, superRule, build, 0)

  // for java
  fun <V> put(key: String, set: `BiConsumer$`<O, V>, type: Rule<V>): ObjectRule<O> {
    return put(key, set as (O, V) -> Unit, type)
  }

  // translate java version to kotlin version
  fun <V> put(key: String, set: (O, V) -> Unit, type: Rule<V>): ObjectRule<O> {
    require(!rules.containsKey(key)) { "duplicated key: $key" }
    rules[key] = cast(ObjectField(set, type))
    return this
  }

  // for simple setter and complex rule
  fun <V> put(key: String, set: O.(V) -> Unit, typeFunc: () -> Rule<V>): ObjectRule<O> = put(key, set, typeFunc())

  // for simple rule and complex setter
  fun <V> put(key: String, type: Rule<V>, set: O.(V) -> Unit): ObjectRule<O> = put(key, set, type)

  // for java
  fun addExtraRule(func: `TriConsumer$`<O, String, Any?>): ObjectRule<O> {
    return addExtraRule(func as O.(String, Any?) -> Unit)
  }

  // for both kotlin
  fun addExtraRule(func: O.(String, Any?) -> Unit): ObjectRule<O> {
    extraRules.add(func)
    return this
  }

  fun getRule(key: String): ObjectField<*, *>? {
    return rules[key]
  }

  override fun toString(sb: StringBuilder, processedListsOrObjects: MutableSet<Rule<*>>) {
    if (!processedListsOrObjects.add(this)) {
      sb.append("Object{...recursive...}")
      return
    }
    sb.append("Object{")
    var isFirst = true
    for ((key, value) in rules) {
      if (isFirst) {
        isFirst = false
      } else {
        sb.append(",")
      }
      sb.append(key).append("=>")
      value.rule.toString(sb, processedListsOrObjects)
    }
    sb.append("}")
  }
}
