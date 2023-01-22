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

open class BuiltInNullableRule<V>(rule: Rule<V>, opIfNull: () -> V?) : NullableRule<V>(rule, opIfNull) {
  override fun toString(): String {
    return "$rule?"
  }

  override fun toString(sb: StringBuilder, processedListsOrObjects: MutableSet<Rule<*>>) {
    sb.append(toString())
  }
}

object NullAsFalseBoolRule : BuiltInNullableRule<Boolean>(BoolRule, { false }) {
  /*#ifndef KOTLIN_NATIVE {{ */@JvmStatic/*}}*/
  fun get(): NullAsFalseBoolRule {
    return this
  }
}

object NullAsZeroDoubleRule : BuiltInNullableRule<Double>(DoubleRule, { 0.0 }) {
  /*#ifndef KOTLIN_NATIVE {{ */@JvmStatic/*}}*/
  fun get(): NullAsZeroDoubleRule {
    return this
  }
}

object NullAsZeroIntRule : BuiltInNullableRule<Int>(IntRule, { 0 }) {
  /*#ifndef KOTLIN_NATIVE {{ */@JvmStatic/*}}*/
  fun get(): NullAsZeroIntRule {
    return this
  }
}

object NullAsZeroLongRule : BuiltInNullableRule<Long>(LongRule, { 0 }) {
  /*#ifndef KOTLIN_NATIVE {{ */@JvmStatic/*}}*/
  fun get(): NullAsZeroLongRule {
    return this
  }
}
