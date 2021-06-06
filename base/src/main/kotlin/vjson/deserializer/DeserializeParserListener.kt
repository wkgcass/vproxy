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
package vjson.deserializer

import vjson.JSON
import vjson.deserializer.rule.*
import vjson.ex.JsonParseException
import vjson.listener.AbstractParserListener
import vjson.parser.ArrayParser
import vjson.parser.ObjectParser
import vjson.simple.SimpleNull
import vjson.util.CastUtils.cast
import vjson.util.collection.Stack

class DeserializeParserListener<T>(rule: Rule<T>) : AbstractParserListener() {
  private val parseStack = Stack<ParseContext>()
  private val nextRuleStack = Stack<Rule<*>>()
  private var begin = false
  private var lastObject: Any? = null

  init {
    require(!(rule !is ObjectRule<*> && rule !is ArrayRule<*, *> && rule !is TypeRule<*>)) {
      "can only accept ObjectRule or ArrayRule or TypeRule"
    }
    nextRuleStack.push(rule)
  }

  override fun onObjectBegin(obj: ObjectParser) {
    val rule = nextRuleStack.peek()
    if (rule is TypeRule<*>) {
      parseStack.push(ParseContext(rule, null))
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
      ?: return  // ignore if the field is not registered
    nextRuleStack.push(field.rule)
  }

  private operator fun set(rule: Rule<*>, holder: Any, set: (Any, Any?) -> Unit, value: Any?) {
    if (value == null) {
      if (rule is NullableStringRule || rule is ArrayRule<*, *> || rule is ObjectRule<*>) {
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
      if (rule is StringRule || rule is NullableStringRule) {
        set(holder, value)
        return
      }
    } else {
      // assert rule instanceof ArrayRule || rule instanceof ObjectRule
      set(holder, value)
      return
    }
    throw JsonParseException(
      "invalid type: expecting: " + rule + ", value=" + value + "(" + (if (value == null) "nil" else value::class.qualifiedName) + ")"
    )
  }

  override fun onObjectValue(obj: ObjectParser, key: String, value: JSON.Instance<*>) {
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
      ?: return  // ignore if the field is not registered
    @Suppress("UNCHECKED_CAST")
    set(field.rule, ctx.`object`!!, cast(field.set), lastObject)
    nextRuleStack.pop()
  }

  override fun onObjectValueJavaObject(obj: ObjectParser, key: String, value: Any?) {
    onObjectValue(obj, key, SimpleNull.Null)
  }

  override fun onObjectEnd(obj: ObjectParser) {
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
    val rule = nextRuleStack.peek()
    if (rule !is ArrayRule<*, *>) {
      throw JsonParseException("expect: object, actual: array")
    }
    parseStack.push(ParseContext(rule, rule.construct()))
    nextRuleStack.push(rule.elementRule)
    begin = true
  }

  override fun onArrayValue(array: ArrayParser, value: JSON.Instance<*>) {
    val ctx = parseStack.peek()
    val rule = cast<ArrayRule<Any, Any?>>(ctx.rule)
    set(rule.elementRule, ctx.`object`!!, rule.add, lastObject)
  }

  override fun onArrayValueJavaObject(array: ArrayParser, value: Any?) {
    onArrayValue(array, SimpleNull.Null)
  }

  override fun onArrayEnd(array: ArrayParser) {
    val ctx = parseStack.pop()
    nextRuleStack.pop()
    val lastObject = ctx.`object`
    this.lastObject = cast<ArrayRule<*, *>>(ctx.rule).build(cast(lastObject))
  }

  override fun onBool(bool: JSON.Bool) {
    lastObject = bool.toJavaObject()
  }

  override fun onBool(bool: Boolean) {
    lastObject = bool
  }

  override fun onNull(n: JSON.Null) {
    lastObject = null
  }

  override fun onNull(n: Unit?) {
    lastObject = null
  }

  override fun onNumber(number: JSON.Number<*>) {
    lastObject = number.toJavaObject()
  }

  override fun onNumber(number: Number) {
    lastObject = number
  }

  override fun onString(string: JSON.String) {
    lastObject = string.toJavaObject()
  }

  override fun onString(string: String) {
    lastObject = string
  }

  fun completed(): Boolean {
    return begin && parseStack.isEmpty()
  }

  @Throws(IllegalStateException::class)
  fun get(): T? {
    check(completed()) { "not completed yet" }
    @Suppress("UNCHECKED_CAST")
    return lastObject as T?
  }
}
