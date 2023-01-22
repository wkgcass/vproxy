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
package io.vproxy.dep.vjson

import io.vproxy.dep.vjson.CharStream.Companion.from
import io.vproxy.dep.vjson.cs.LineCol
import io.vproxy.dep.vjson.deserializer.DeserializeParserListener
import io.vproxy.dep.vjson.deserializer.rule.Rule
import io.vproxy.dep.vjson.parser.ParserMode
import io.vproxy.dep.vjson.parser.ParserOptions
import io.vproxy.dep.vjson.parser.ParserUtils.buildFrom
import io.vproxy.dep.vjson.parser.ParserUtils.buildJavaObject
import io.vproxy.dep.vjson.pl.ScriptifyContext
import io.vproxy.dep.vjson.util.CastUtils.cast

@Suppress("DuplicatedCode")
object JSON {
  /*#ifndef KOTLIN_NATIVE {{ */
  @Throws(RuntimeException::class)
  @JvmStatic/*}}*/
  fun parse(json: kotlin.String): Instance<*> {
    return parse(from(json))
  }

  /*#ifndef KOTLIN_NATIVE {{ */
  @Throws(RuntimeException::class)
  @JvmStatic/*}}*/
  fun parse(cs: CharStream): Instance<*> {
    return buildFrom(cs)
  }

  /*#ifndef KOTLIN_NATIVE {{ */
  @Throws(RuntimeException::class)
  @JvmStatic/*}}*/
  fun <T> deserialize(json: kotlin.String, rule: Rule<T>): T {
    return deserialize(from(json), rule)
  }

  /*#ifndef KOTLIN_NATIVE {{ */
  @Throws(RuntimeException::class)
  @JvmStatic/*}}*/
  fun <T> deserialize(cs: CharStream, rule: Rule<T>): T {
    return deserialize(cs, rule, ParserOptions())
  }

  /*#ifndef KOTLIN_NATIVE {{ */
  @Throws(RuntimeException::class)
  @JvmStatic/*}}*/
  fun <T> deserialize(cs: CharStream, rule: Rule<T>, opts: ParserOptions): T {
    val listener = DeserializeParserListener(rule)
    buildFrom(
      cs, opts.setListener(listener)
        .setMode(ParserMode.JAVA_OBJECT)
        .setNullArraysAndObjects(true)
    )
    return listener.get()!!
  }

  /*#ifndef KOTLIN_NATIVE {{ */
  @Throws(RuntimeException::class)
  @JvmStatic/*}}*/
  fun parseToJavaObject(json: kotlin.String): Any? {
    return parseToJavaObject(from(json))
  }

  /*#ifndef KOTLIN_NATIVE {{ */
  @Throws(RuntimeException::class)
  @JvmStatic/*}}*/
  fun parseToJavaObject(cs: CharStream): Any? {
    return buildJavaObject(cs)
  }

  interface Instance<T> {
    fun toJavaObject(): T?
    fun stringify(): kotlin.String
    fun pretty(): kotlin.String
    fun stringify(builder: StringBuilder, sfr: Stringifier)
    fun scriptify(builder: StringBuilder, ctx: ScriptifyContext)

    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun lineCol(): LineCol {
      return LineCol.EMPTY
    }
  }

  data class ObjectEntry /*#ifndef KOTLIN_NATIVE {{ */ @JvmOverloads/*}}*/ constructor(
    val key: kotlin.String,
    val value: Instance<*>,
    val lineCol: LineCol = LineCol.EMPTY,
  ) {
    override fun toString(): kotlin.String {
      return "($key: $value)"
    }
  }

  interface Object : Instance<LinkedHashMap<kotlin.String, Any?>> {
    fun keySet(): LinkedHashSet<kotlin.String>
    fun keyList(): List<kotlin.String>
    fun entryList(): List<ObjectEntry>
    fun size(): Int
    fun containsKey(key: kotlin.String): Boolean
    override fun toJavaObject(): LinkedHashMap<kotlin.String, Any?>

    /* #ifndef KOTLIN_NATIVE {{ */ @Throws(NoSuchElementException::class) // }}
    operator fun get(key: kotlin.String): Instance<*>

    /* #ifndef KOTLIN_NATIVE {{ */ @Throws(NoSuchElementException::class) // }}
    fun getAll(key: kotlin.String): List<Instance<*>>

    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun getBool(key: kotlin.String): Boolean {
      return cast<Bool>(get(key)).booleanValue()
    }

    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun getInt(key: kotlin.String): Int {
      val inst = get(key)
      if (inst is Integer) {
        return inst.intValue()
      } else if (inst is Double) {
        return inst.doubleValue().toInt()
      } else if (inst is Long) {
        return inst.longValue().toInt()
      } else if (inst is Number<*>) {
        return inst.toJavaObject().toInt()
      } else {
        throw ClassCastException(inst::class./* #ifdef KOTLIN_JS {{ simpleName }} else {{ */qualifiedName/* }} */ + " cannot be cast to " + Number::class./* #ifdef KOTLIN_JS {{ simpleName }} else {{ */qualifiedName/* }} */)
      }
    }

    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun getDouble(key: kotlin.String): kotlin.Double {
      val inst = get(key)
      if (inst is Integer) {
        return inst.intValue().toDouble()
      } else if (inst is Double) {
        return inst.doubleValue()
      } else if (inst is Long) {
        return inst.longValue().toDouble()
      } else if (inst is Number<*>) {
        return inst.toJavaObject().toDouble()
      } else {
        throw ClassCastException(inst::class./* #ifdef KOTLIN_JS {{ simpleName }} else {{ */qualifiedName/* }} */ + " cannot be cast to " + Number::class./* #ifdef KOTLIN_JS {{ simpleName }} else {{ */qualifiedName/* }} */)
      }
    }

    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun getLong(key: kotlin.String): kotlin.Long {
      val inst = get(key)
      if (inst is Integer) {
        return inst.intValue().toLong()
      } else if (inst is Double) {
        return inst.doubleValue().toLong()
      } else if (inst is Long) {
        return inst.longValue()
      } else if (inst is Number<*>) {
        return inst.toJavaObject().toLong()
      } else {
        throw ClassCastException(inst::class./* #ifdef KOTLIN_JS {{ simpleName }} else {{ */qualifiedName/* }} */ + " cannot be cast to " + Number::class./* #ifdef KOTLIN_JS {{ simpleName }} else {{ */qualifiedName/* }} */)
      }
    }

    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun getString(key: kotlin.String): kotlin.String {
      return cast<String>(get(key)).toJavaObject()
    }

    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun getNullableString(key: kotlin.String): kotlin.String? {
      val inst = get(key)
      if (inst is Null) {
        return null
      } else {
        return cast<String>(inst).toJavaObject()
      }
    }

    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun getObject(key: kotlin.String): Object {
      return cast(get(key))
    }

    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun getNullableObject(key: kotlin.String): Object? {
      val inst = get(key)
      if (inst is Null) {
        return null
      } else {
        return cast<Object>(inst)
      }
    }

    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun getArray(key: kotlin.String): Array {
      return cast(get(key))
    }

    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun getNullableArray(key: kotlin.String): Array? {
      val inst = get(key)
      if (inst is Null) {
        return null
      } else {
        return cast<Array>(inst)
      }
    }
  }

  interface Array : Instance<List<Any?>> {
    fun length(): Int
    override fun toJavaObject(): List<Any?>

    /* #ifndef KOTLIN_NATIVE {{ */ @Throws(IndexOutOfBoundsException::class) // }}
    operator fun get(idx: Int): Instance<*>

    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun getBool(idx: Int): Boolean {
      return cast<Bool>(get(idx)).booleanValue()
    }

    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun getInt(idx: Int): Int {
      val inst = get(idx)
      if (inst is Integer) {
        return inst.intValue()
      } else if (inst is Double) {
        return inst.doubleValue().toInt()
      } else if (inst is Long) {
        return inst.longValue().toInt()
      } else if (inst is Number<*>) {
        return inst.toJavaObject().toInt()
      } else {
        throw ClassCastException(inst::class./* #ifdef KOTLIN_JS {{ simpleName }} else {{ */qualifiedName/* }} */ + " cannot be cast to " + Number::class./* #ifdef KOTLIN_JS {{ simpleName }} else {{ */qualifiedName/* }} */)
      }
    }

    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun getDouble(idx: Int): kotlin.Double {
      val inst = get(idx)
      if (inst is Integer) {
        return inst.intValue().toDouble()
      } else if (inst is Double) {
        return inst.doubleValue()
      } else if (inst is Long) {
        return inst.longValue().toDouble()
      } else if (inst is Number<*>) {
        return inst.toJavaObject().toDouble()
      } else {
        throw ClassCastException(inst::class./* #ifdef KOTLIN_JS {{ simpleName }} else {{ */qualifiedName/* }} */ + " cannot be cast to " + Number::class./* #ifdef KOTLIN_JS {{ simpleName }} else {{ */qualifiedName/* }} */)
      }
    }

    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun getLong(idx: Int): kotlin.Long {
      val inst = get(idx)
      if (inst is Integer) {
        return inst.intValue().toLong()
      } else if (inst is Double) {
        return inst.doubleValue().toLong()
      } else if (inst is Long) {
        return inst.longValue()
      } else if (inst is Number<*>) {
        return inst.toJavaObject().toLong()
      } else {
        throw ClassCastException(inst::class./* #ifdef KOTLIN_JS {{ simpleName }} else {{ */qualifiedName/* }} */ + " cannot be cast to " + Number::class./* #ifdef KOTLIN_JS {{ simpleName }} else {{ */qualifiedName/* }} */)
      }
    }

    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun getString(idx: Int): kotlin.String {
      return cast<String>(get(idx)).toJavaObject()
    }

    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun getNullableString(idx: Int): kotlin.String? {
      val inst = get(idx)
      if (inst is Null) {
        return null
      } else {
        return cast<String>(inst).toJavaObject()
      }
    }

    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun getObject(idx: Int): Object {
      return cast(get(idx))
    }

    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun getNullableObject(idx: Int): Object? {
      val inst = get(idx)
      if (inst is Null) {
        return null
      } else {
        return cast<Object>(inst)
      }
    }

    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun getArray(idx: Int): Array {
      return cast(get(idx))
    }

    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun getNullableArray(idx: Int): Array? {
      val inst = get(idx)
      if (inst is Null) {
        return null
      } else {
        return cast<Array>(inst)
      }
    }
  }

  interface String : Instance<kotlin.String> {
    override fun toJavaObject(): kotlin.String

    companion object {
      /*#ifndef KOTLIN_NATIVE {{ */
      @Suppress("DEPRECATION")
      @JvmOverloads
      @JvmStatic/*}}*/
      fun stringify(s: kotlin.String, stringOptions: Stringifier.StringOptions? = null): kotlin.String {
        val sb = StringBuilder()
        sb.append("\"")
        val chars = s.toCharArray()
        for (c in chars) {
          val printableChar = stringOptions?.printableChar
          if ((printableChar == null && c.toInt() in 32..126) || (printableChar != null && printableChar(c))) {
            when (c) {
              '\"' -> sb.append("\\\"")
              '\\' -> sb.append("\\\\")
              else -> sb.append(c)
            }
          } else if (c.toInt() < 128) {
            when (c) {
              '\b' -> sb.append("\\b")
              '\u000C' -> sb.append("\\f")
              '\n' -> sb.append("\\n")
              '\r' -> sb.append("\\r")
              '\t' -> sb.append("\\t")
              else -> {
                val foo = c.toInt().toString(16)
                if (foo.length < 2) {
                  sb.append("\\u000").append(foo)
                } else {
                  sb.append("\\u00").append(foo)
                }
              }
            }
          } else {
            val foo = c.toInt().toString(16)
            if (foo.length < 3) {
              sb.append("\\u00").append(foo)
            } else if (foo.length < 4) {
              sb.append("\\u0").append(foo)
            } else {
              sb.append("\\u").append(foo)
            }
          }
        }
        sb.append("\"")
        return sb.toString()
      }
    }
  }

  interface Number<T : kotlin.Number> : Instance<T> {
    override fun toJavaObject(): T
  }

  interface IntegerNumber<T : kotlin.Number> : Number<T>

  interface Integer : IntegerNumber<Int> {
    fun intValue(): Int
  }

  interface Long : IntegerNumber<kotlin.Long> {
    fun longValue(): kotlin.Long
  }

  interface Double : Number<kotlin.Double> {
    fun doubleValue(): kotlin.Double
  }

  interface Exp : Double {
    fun base(): kotlin.Double
    fun exponent(): Int
  }

  interface Bool : Instance<Boolean> {
    fun booleanValue(): Boolean
    override fun toJavaObject(): Boolean
  }

  interface Null : Instance<Any?> {
    override
    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")
    @JvmDefault/*}}*/
    fun toJavaObject(): Any? {
      return null
    }
  }
}
