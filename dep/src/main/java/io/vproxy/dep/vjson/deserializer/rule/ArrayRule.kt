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

import io.vproxy.dep.vjson.util.CastUtils.cast
import io.vproxy.dep.vjson.util.functional.BiConsumer_

class ArrayRule<L : Any, E : Any?> : Rule<L> {
  val construct: () -> Any
  val build: (Any) -> Any
  val add: (Any, E) -> Unit
  val elementRule: Rule<E>

  // for java
  constructor(construct: () -> L, add: BiConsumer_<L, E>, elementRule: Rule<E>)
    : this(construct, add as (L, E) -> Unit, elementRule)

  // translate java version to kotlin version
  constructor(construct: () -> L, add: L.(E) -> Unit, elementRule: Rule<E>) {
    this.construct = construct
    this.build = { it }
    this.add = cast(add)
    this.elementRule = cast(elementRule)
  }

  // for simple rule but complex adder
  constructor(construct: () -> L, elementRule: Rule<E>, add: L.(E) -> Unit) : this(construct, add, elementRule)

  // for simple adder but complex rule
  constructor(construct: () -> L, add: L.(E) -> Unit, elementRuleFunc: () -> Rule<E>)
    : this(construct, add, elementRuleFunc())

  // for builder
  private constructor(
    construct: () -> Any,
    build: (Any) -> Any,
    add: (Any, Any?) -> Unit,
    elementRule: Rule<E>
  ) {
    this.construct = construct
    this.build = build
    this.add = add
    this.elementRule = elementRule
  }

  companion object {
    // for java
    /*#ifndef KOTLIN_NATIVE {{ */@JvmStatic/*}}*/
    fun <T_LIST : Any, T_BUILDER : Any, T_ELEM : Any?> builder(
      construct: () -> T_BUILDER,
      build: (T_BUILDER) -> T_LIST,
      add: BiConsumer_<T_BUILDER, T_ELEM>,
      elementRule: Rule<T_ELEM>
    ): ArrayRule<T_LIST, T_ELEM> = ArrayRule(construct, cast(build), cast(add), elementRule)

    // for kotlin
    /*#ifndef KOTLIN_NATIVE {{ */@JvmStatic/*}}*/
    fun <T_LIST : Any, T_BUILDER : Any, T_ELEM : Any?> builder(
      construct: () -> T_BUILDER,
      build: T_BUILDER.() -> T_LIST,
      add: T_BUILDER.(T_ELEM) -> Unit,
      elementRuleFunc: () -> Rule<T_ELEM>
    ): ArrayRule<T_LIST, T_ELEM> = ArrayRule(construct, cast(build), cast(add), elementRuleFunc())
  }

  override fun toString(sb: StringBuilder, processedListsOrObjects: MutableSet<Rule<*>>) {
    if (!processedListsOrObjects.add(this)) {
      sb.append("Array[...recursive...]")
      return
    }
    sb.append("Array[")
    elementRule.toString(sb, processedListsOrObjects)
    sb.append("]")
  }
}
