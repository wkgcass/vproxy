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
package io.vproxy.dep.vjson.pl

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.ex.ParserException
import io.vproxy.dep.vjson.pl.ast.*
import io.vproxy.dep.vjson.pl.inst.ActionContext
import io.vproxy.dep.vjson.pl.inst.RuntimeMemory
import io.vproxy.dep.vjson.pl.type.*
import io.vproxy.dep.vjson.pl.type.lang.CollectionType
import io.vproxy.dep.vjson.pl.type.lang.MapType
import io.vproxy.dep.vjson.simple.*
import io.vproxy.dep.vjson.util.ArrayBuilder
import io.vproxy.dep.vjson.util.ObjectBuilder

class RuntimeMemoryExplorer(private val builder: Builder) {
  fun getExplorerByType(name: String): RuntimeMemoryExplorer {
    return builder.getExplorerByType(name)
      ?: throw NoSuchElementException()
  }

  fun getExplorerByVariable(name: String): RuntimeMemoryExplorer? {
    val type = builder.variableTypes[name]
      ?: throw NoSuchElementException(name)
    return if (type is ClassTypeInstance) {
      val clsName = type._concreteTypeName ?: type.cls.name
      builder.getExplorerByType(clsName)
    } else null
  }

  fun listVariables(): List<String> {
    return ArrayList(builder.variableOrder)
  }

  fun getModifiersOfVariable(name: String): Modifiers {
    return builder.variableModifiers[name]
      ?: throw NoSuchElementException(name)
  }

  fun getVariable(name: String, mem: RuntimeMemory): Any? {
    val type = builder.variableTypes[name]
      ?: throw NoSuchElementException(name)
    val index = builder.variableIndexes[name]!!
    return when (type) {
      is IntType -> mem.getInt(index)
      is LongType -> mem.getLong(index)
      is FloatType -> mem.getFloat(index)
      is DoubleType -> mem.getDouble(index)
      is BoolType -> mem.getBool(index)
      else -> {
        val ret = mem.getRef(index)
        if (ret is ActionContext) ret.getCurrentMem()
        else ret
      }
    }
  }

  fun getTypeByVariable(name: String): TypeInstance {
    return builder.variableTypes[name]
      ?: throw NoSuchElementException(name)
  }

  fun toJson(mem: RuntimeMemory): JSON.Object {
    val o = ObjectBuilder()
    for (f in builder.variableOrder) {
      if (builder.variableModifiers[f]!!.isPublic()) {
        val v = getVariable(f, mem)
        val type = builder.variableTypes[f]!!
        o.putInst(f, toJsonInstance(type, v))
      }
    }
    return o.build()
  }

  private fun toJsonInstance(type: TypeInstance, v: Any?): JSON.Instance<*> {
    return when (v) {
      null -> SimpleNull.Null
      is Int -> SimpleInteger(v)
      is Long -> SimpleLong(v)
      is Float -> SimpleDouble(v.toDouble())
      is Double -> SimpleDouble(v)
      is Boolean -> SimpleBool(v)
      is String -> SimpleString(v)
      else -> refToJsonInstance(type, v)
    }
  }

  private fun refToJsonInstance(type: TypeInstance, v: Any): JSON.Instance<*> {
    return when (type) {
      is ClassTypeInstance -> {
        val explorer = builder.getExplorerByType(type._concreteTypeName ?: type.cls.name)
          ?: throw ParserException("unable to convert the instance to json, class definition ${type.cls.name} is not found")
        explorer.toJson(v as RuntimeMemory)
      }
      is ArrayTypeInstance -> {
        val array = ArrayBuilder()
        when (val elementType = type.elementType(TypeContext(MemoryAllocator()))) {
          is IntType -> {
            v as IntArray
            for (n in v) array.add(n)
          }
          is LongType -> {
            v as LongArray
            for (n in v) array.add(n)
          }
          is FloatType -> {
            v as IntArray
            for (n in v) array.add(n)
          }
          is DoubleType -> {
            v as IntArray
            for (n in v) array.add(n)
          }
          is BoolType -> {
            v as IntArray
            for (n in v) array.add(n)
          }
          else -> {
            v as Array<*>
            for (n in v) {
              val e = if (n is ActionContext) n.getCurrentMem() else n
              array.addInst(toJsonInstance(elementType, e))
            }
          }
        }
        array.build()
      }
      else -> throw ParserException("unable to convert the instance to json, $type does not support conversion")
    }
  }

  /*#ifndef KOTLIN_NATIVE {{ */ @JvmOverloads/*}}*/
  fun inspect(mem: RuntimeMemory, sb: StringBuilder = StringBuilder()): StringBuilder {
    inspect(mem, sb, 0)
    sb.deleteAt(sb.length - 1)
    return sb
  }

  /*#ifndef KOTLIN_NATIVE {{ */ @JvmOverloads/*}}*/
  fun inspectVariable(name: String, mem: RuntimeMemory, sb: StringBuilder = StringBuilder()): StringBuilder {
    val v = getVariable(name, mem)
    val type = builder.variableTypes[name]!!
    inspectValue(v, type, sb, 0)
    sb.deleteAt(sb.length - 1)
    return sb
  }

  private fun inspect(mem: RuntimeMemory, sb: StringBuilder, indent: Int) {
    for (name in builder.variableOrder) {
      sb.append(" ".repeat(indent))
      val modifiers = builder.variableModifiers[name]!!.toStringWithSpace()
      sb.append(modifiers).append(name).append(" = ")
      val v = getVariable(name, mem)
      val type = builder.variableTypes[name]!!
      inspectValue(v, type, sb, indent)
    }
  }

  private fun inspectValue(v: Any?, type: TypeInstance, sb: StringBuilder, indent: Int, addPreIndent: Boolean = false) {
    if (addPreIndent) {
      sb.append(" ".repeat(indent))
    }
    when (v) {
      null, is Int, Long, is Long, is Float, is Double, is Boolean ->
        sb.append(v).append("\n")
      is String -> sb.append(SimpleString(v).stringify()).append("\n")
      else -> inspectComplexValue(v, type, sb, indent)
    }
  }

  private fun inspectComplexValue(v: Any, type: TypeInstance, sb: StringBuilder, indent: Int) {
    when (type) {
      is ClassTypeInstance -> {
        val tName = type._concreteTypeName ?: type.cls.name
        val explorer = builder.getExplorerByType(tName)
        if (explorer == null) {
          sb.append("<no info: $tName $v>\n")
        } else {
          sb.append("{\n")
          explorer.inspect(v as RuntimeMemory, sb, indent + 2)
          sb.append(" ".repeat(indent)).append("}\n")
        }
      }
      is ArrayTypeInstance -> {
        sb.append("[\n")
        val elementType = type.elementType(TypeContext(MemoryAllocator()))
        if (v is IntArray) {
          for (e in v) inspectValue(e, elementType, sb, indent + 2, true)
        } else if (v is LongArray) {
          for (e in v) inspectValue(e, elementType, sb, indent + 2, true)
        } else if (v is FloatArray) {
          for (e in v) inspectValue(e, elementType, sb, indent + 2, true)
        } else if (v is DoubleArray) {
          for (e in v) inspectValue(e, elementType, sb, indent + 2, true)
        } else if (v is BooleanArray) {
          for (e in v) inspectValue(e, elementType, sb, indent + 2, true)
        } else {
          v as Array<*>
          for (n in v) {
            val e = if (n is ActionContext) n.getCurrentMem() else n
            inspectValue(e, elementType, sb, indent + 2, true)
          }
        }
        sb.append(" ".repeat(indent)).append("]\n")
      }
      is CollectionType -> {
        val coll = (v as RuntimeMemory).getRef(0) as Collection<*>
        val elementType = type.templateTypeParams()!![0]
        sb.append("[\n")
        for (n in coll) {
          val e = if (n is ActionContext) n.getCurrentMem() else n
          inspectValue(e, elementType, sb, indent + 2, true)
        }
        sb.append(" ".repeat(indent)).append("]\n")
      }
      is MapType -> {
        val map = (v as RuntimeMemory).getRef(0) as Map<*, *>
        val keyType = type.templateTypeParams()!![0]
        val valueType = type.templateTypeParams()!![1]

        sb.append("{\n")
        for ((key, value) in map) {
          sb.append(" ".repeat(indent + 2))
          val k = if (key is ActionContext) key.getCurrentMem() else key
          inspectValue(k, keyType, sb, indent + 2)
          sb.deleteAt(sb.length - 1)
          sb.append(" = ")
          val vv = if (value is ActionContext) value.getCurrentMem() else value
          inspectValue(vv, valueType, sb, indent + 2)
        }
        sb.append(" ".repeat(indent)).append("}\n")
      }
      else -> sb.append("<no info: $type $v>")
    }
  }

  class Builder(val parent: Builder? = null) {
    val classes = HashMap<String, RuntimeMemoryExplorer>()
    val variableTypes = HashMap<String, TypeInstance>()
    val variableIndexes = HashMap<String, Int>()
    val variableModifiers = HashMap<String, Modifiers>()
    val variableOrder = ArrayList<String>()

    fun build(): RuntimeMemoryExplorer {
      return RuntimeMemoryExplorer(this)
    }

    fun feed(ast: List<Statement>) {
      for (stmt in ast) {
        when (stmt) {
          is ClassDefinition -> feedClassDef(stmt)
          is TemplateTypeInstantiation -> feedTemplateTypeInstantiation(stmt)
          is VariableDefinition -> feedVariableDef(stmt)
          is ErrorHandlingStatement -> feed(stmt.tryCode)
        }
      }
    }

    private fun feedTemplateTypeInstantiation(let: TemplateTypeInstantiation) {
      val builder = Builder(this)
      val instantiated = let.instantiatedTypeInstance!!
      if (instantiated !is ClassTypeInstance) {
        return
      }
      builder.feed(instantiated.cls.code)
      classes[let.typeName] = builder.build()
    }

    private fun feedClassDef(clsDef: ClassDefinition) {
      val builder = Builder(this)
      builder.feed(clsDef.code)
      val explorer = builder.build()
      classes[clsDef.name] = explorer
    }

    private fun feedVariableDef(varDef: VariableDefinition) {
      variableTypes[varDef.name] = varDef.typeInstance()
      variableIndexes[varDef.name] = varDef.variableIndex
      variableModifiers[varDef.name] = varDef.modifiers
      variableOrder.add(varDef.name)
    }

    internal fun getExplorerByType(name: String): RuntimeMemoryExplorer? {
      val ret = classes[name]
      if (ret != null) return ret
      if (parent != null) return parent.getExplorerByType(name)
      return null
    }
  }
}
