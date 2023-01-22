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
import io.vproxy.dep.vjson.util.functional.BiConsumer_
import io.vproxy.dep.vjson.util.functional.Consumer_
import io.vproxy.dep.vjson.util.functional.Supplier_

class ArrayBuilder {
  private val list: MutableList<JSON.Instance<*>> = ArrayList()

  fun addInst(inst: JSON.Instance<*>): ArrayBuilder {
    list.add(inst)
    return this
  }

  fun addNullableInst(isNull: Boolean, instSupplier: () -> JSON.Instance<*>): ArrayBuilder {
    if (isNull) {
      return add(null)
    } else {
      return addInst(instSupplier())
    }
  }

  fun addNullableInst(isNull: Boolean, instSupplier: Supplier_<JSON.Instance<*>>): ArrayBuilder {
    return addNullableInst(isNull, instSupplier as () -> JSON.Instance<*>)
  }

  fun add(bool: Boolean): ArrayBuilder {
    return addInst(SimpleBool(bool))
  }

  fun add(integer: Int): ArrayBuilder {
    return addInst(SimpleInteger(integer))
  }

  fun add(longV: Long): ArrayBuilder {
    return addInst(SimpleLong(longV))
  }

  fun add(doubleV: Double): ArrayBuilder {
    return addInst(SimpleDouble(doubleV))
  }

  fun add(num: Double, exponent: Int): ArrayBuilder {
    return addInst(SimpleExp(num, exponent))
  }

  fun add(string: String?): ArrayBuilder {
    if (string == null) {
      return addInst(SimpleNull())
    } else {
      return addInst(SimpleString(string))
    }
  }

  fun addObject(func: ObjectBuilder.() -> Unit): ArrayBuilder {
    val builder = ObjectBuilder()
    func(builder)
    return addInst(builder.build())
  }

  fun addObject(func: Consumer_<ObjectBuilder>): ArrayBuilder {
    return addObject(func as (ObjectBuilder) -> Unit)
  }

  fun addArray(func: ArrayBuilder.() -> Unit): ArrayBuilder {
    val builder = ArrayBuilder()
    func(builder)
    return addInst(builder.build())
  }

  fun addArray(func: Consumer_<ArrayBuilder>): ArrayBuilder {
    return addArray(func as (ArrayBuilder) -> Unit)
  }

  fun <T> iterable(ite: Iterable<T>, operator: ArrayBuilder.(T) -> Unit): ArrayBuilder {
    for (e in ite) {
      operator(this, e)
    }
    return this
  }

  fun <T> iterable(ite: Iterable<T>, operator: BiConsumer_<ArrayBuilder, T>): ArrayBuilder {
    return iterable(ite, operator as ArrayBuilder.(T) -> Unit)
  }

  fun build(): JSON.Array {
    return object : SimpleArray(list, TrustedFlag.FLAG) {}
  }
}
