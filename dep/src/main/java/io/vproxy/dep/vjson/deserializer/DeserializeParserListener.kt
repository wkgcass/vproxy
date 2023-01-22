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
package io.vproxy.dep.vjson.deserializer

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.deserializer.rule.*
import io.vproxy.dep.vjson.ex.JsonParseException
import io.vproxy.dep.vjson.listener.AbstractParserListener
import io.vproxy.dep.vjson.parser.ArrayParser
import io.vproxy.dep.vjson.parser.ObjectParser
import io.vproxy.dep.vjson.simple.SimpleNull
import io.vproxy.dep.vjson.util.CastUtils.cast
import io.vproxy.dep.vjson.util.collection.Stack

class DeserializeParserListener<T>(rule: Rule<T>) : AbstractParserListener() {
  private val parseStack = Stack<ParseContext>()
  private val nextRuleStack = Stack<Rule<*>>()
  private var begin = false
  private var skip: Int = 0 // this field is used to skip non-registered fields
  private var lastObject: Any? = null

  init {
    val real = rule.real()
    require(!(real !is ObjectRule<*> && real !is ArrayRule<*, *> && real !is TypeRule<*>)) {
      "can only accept ObjectRule or ArrayRule or TypeRule"
    }
    nextRuleStack.push(rule)
  }

  override fun onObjectBegin(obj: ObjectParser) {
    if (skip != 0) {
      ++skip
      return
    }

    val rule = nextRuleStack.peek().real()
    if (rule is TypeRule<*>) {
      parseStack.push(ParseContext(rule, null))
    } else if (rule is NothingRule) {
      skip = 1
    } else if (rule !is ObjectRule<*>) {
      throw JsonParseException("expect: array, actual: object")
    } else {
      parseStack.push(ParseContext(rule, rule.construct()))
    }
    begin = true
  }

  private fun applyObjectRuleForTypeRule(orule: ObjectRule<*>): ParseContext {
    parseStack.pop()
    val newCtx = ParseContext(orule, orule.construct())
    parseStack.push(newCtx)
    return newCtx
  }

  override fun onObjectKey(obj: ObjectParser, key: String) {
    if (skip != 0) {
      return
    }

    var ctx = parseStack.peek()

    if (ctx.rule is TypeRule<*>) {
      if (key == "@type") {
        return  // will use @type to deserialize the rest k/v
      }
      // nothing found, try to use default rule
      val orule = cast<TypeRule<*>>(ctx.rule).defaultRule
        ?: throw JsonParseException("cannot determine type for " + ctx.rule)
      // use the default rule
      ctx = applyObjectRuleForTypeRule(orule)
      // fall through
    }

    val rule = cast<ObjectRule<*>>(ctx.rule)
    val field = rule.getRule(key)
    if (field == null) {
      nextRuleStack.push(NothingRule)
    } else {
      nextRuleStack.push(field.rule)
    }
  }

  private operator fun set(_rule: Rule<*>, holder: Any, set: (Any, Any?) -> Unit, value: Any?) {
    val rule = _rule.real()
    if (value == null) {
      if (_rule is NullableRule) {
        set(holder, _rule.opIfNull())
        return
      }
      if (rule is ArrayRule<*, *> || rule is ObjectRule<*>) {
        set(holder, null)
        return
      }
    } else if (value is Boolean) {
      if (rule is BoolRule) {
        set(holder, value)
        return
      }
    } else if (rule is DoubleRule && value is Number) {
      set(holder, value.toDouble())
      return
    } else if (rule is LongRule && value is Number) {
      if (value is Long || value is Int) {
        set(holder, value.toLong())
        return
      }
    } else if (rule is IntRule && value is Number) {
      if (value is Int) {
        set(holder, value)
        return
      }
    } else if (value is String) {
      if (rule is StringRule) {
        set(holder, value)
        return
      }
    } else {
      // assert rule instanceof ArrayRule || rule instanceof ObjectRule
      set(holder, value)
      return
    }
    throw JsonParseException(
      "invalid type: expecting: " + rule + ", value=" + value + "(" + (if (value == null) "nil" else value::class./* #ifdef KOTLIN_JS {{ simpleName }} else {{ */qualifiedName/* }} */) + ")"
    )
  }

  override fun onObjectValue(obj: ObjectParser, key: String, value: JSON.Instance<*>) {
    if (skip != 0) {
      return
    }

    val ctx = parseStack.peek()

    if (ctx.rule is TypeRule<*>) {
      // assert key.equals("@type");
      if (lastObject !is String) {
        throw JsonParseException("invalid type: expecting type name for " + ctx.rule + " but got " + lastObject)
      }
      val type = cast<String>(lastObject)
      val orule = ctx.rule.getRule(type)
        ?: // rule not found
        throw JsonParseException("cannot find type " + type + " in " + ctx.rule)
      applyObjectRuleForTypeRule(orule)
      return
    }

    val rule = cast<ObjectRule<*>>(ctx.rule)
    val field = rule.getRule(key)
    if (field == null) {
      // handle extra
      for (f in rule.extraRules) {
        cast<(Any, String, Any?) -> Unit>(f)(ctx.`object`!!, key, lastObject)
      }
      // pop NothingRule
      nextRuleStack.pop()
      return
    }
    @Suppress("UNCHECKED_CAST")
    set(field.rule, ctx.`object`!!, cast(field.set), lastObject)
    nextRuleStack.pop()
  }

  override fun onObjectValueJavaObject(obj: ObjectParser, key: String, value: Any?) {
    onObjectValue(obj, key, SimpleNull.Null)
  }

  override fun onObjectEnd(obj: ObjectParser) {
    if (skip != 0) {
      --skip
      return
    }

    val ctx = parseStack.pop()
    if (ctx.rule is TypeRule<*>) {
      val orule = ctx.rule.defaultRule
        ?: throw JsonParseException("type for " + ctx.rule + " is still not determined when reaching the object end")
      // use the default rule to construct an empty object
      val constructed = orule.construct()
      this.lastObject = orule.build(constructed)
    } else {
      val lastObject = ctx.`object`
      this.lastObject = cast<ObjectRule<*>>(ctx.rule).build(cast(lastObject))
    }
  }

  override fun onArrayBegin(array: ArrayParser) {
    if (skip != 0) {
      ++skip
      return
    }

    val rule = nextRuleStack.peek().real()
    if (rule is NothingRule) {
      skip = 1
      return
    }
    if (rule !is ArrayRule<*, *>) {
      throw JsonParseException("expect: object, actual: array")
    }
    parseStack.push(ParseContext(rule, rule.construct()))
    nextRuleStack.push(rule.elementRule)
    begin = true
  }

  override fun onArrayValue(array: ArrayParser, value: JSON.Instance<*>) {
    if (skip != 0) {
      return
    }

    val ctx = parseStack.peek()
    val rule = cast<ArrayRule<Any, Any?>>(ctx.rule)
    set(rule.elementRule, ctx.`object`!!, rule.add, lastObject)
  }

  override fun onArrayValueJavaObject(array: ArrayParser, value: Any?) {
    onArrayValue(array, SimpleNull.Null)
  }

  override fun onArrayEnd(array: ArrayParser) {
    if (skip != 0) {
      skip -= 1
      return
    }

    val ctx = parseStack.pop()
    nextRuleStack.pop()
    val lastObject = ctx.`object`
    this.lastObject = cast<ArrayRule<*, *>>(ctx.rule).build(cast(lastObject))
  }

  override fun onBool(bool: JSON.Bool) {
    if (skip != 0) {
      return
    }

    lastObject = bool.toJavaObject()
  }

  override fun onBool(bool: Boolean) {
    if (skip != 0) {
      return
    }

    lastObject = bool
  }

  override fun onNull(n: JSON.Null) {
    if (skip != 0) {
      return
    }

    lastObject = null
  }

  override fun onNull(n: Unit?) {
    if (skip != 0) {
      return
    }

    lastObject = null
  }

  override fun onNumber(number: JSON.Number<*>) {
    if (skip != 0) {
      return
    }

    lastObject = number.toJavaObject()
  }

  override fun onNumber(number: Number) {
    if (skip != 0) {
      return
    }

    lastObject = number
  }

  override fun onString(string: JSON.String) {
    if (skip != 0) {
      return
    }

    lastObject = string.toJavaObject()
  }

  override fun onString(string: String) {
    if (skip != 0) {
      return
    }

    lastObject = string
  }

  fun completed(): Boolean {
    return begin && parseStack.isEmpty()
  }

  /* #ifndef KOTLIN_NATIVE {{ */ @Throws(IllegalStateException::class) // }}
  fun get(): T? {
    check(completed()) { "not completed yet" }
    @Suppress("UNCHECKED_CAST")
    return lastObject as T?
  }
}
