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

/* #ifndef KOTLIN_NATIVE {{*/import kotlinx.coroutines.runBlocking/*}}*/
import io.vproxy.dep.vjson.pl.ast.Statement
import io.vproxy.dep.vjson.pl.inst.*
import io.vproxy.dep.vjson.pl.type.MemoryAllocator
import io.vproxy.dep.vjson.pl.type.TypeContext
import io.vproxy.dep.vjson.pl.type.lang.Types

class Interpreter(private val types: List<Types>, private val ast: List<Statement>) {
  private val typesOffset = ArrayList<RuntimeMemoryTotal>()
  private val typeContext = TypeContext(MemoryAllocator())
  private val valueForTypes = HashMap<Types, RuntimeMemory>()

  init {
    var offset = RuntimeMemoryTotal()
    for (t in types) {
      typesOffset.add(offset)
      offset = t.initiateType(typeContext, offset)
    }

    typeContext.checkStatements(ast)
  }

  fun putValues(t: Types, values: RuntimeMemory) {
    valueForTypes[t] = values
  }

  fun removeValues(t: Types) {
    valueForTypes.remove(t)
  }

  // #ifndef KOTLIN_NATIVE {{
  fun executeBlock(): RuntimeMemory {
    return runBlocking {
      execute()
    }
  }
  // }}

  suspend fun execute(): RuntimeMemory {
    val actionContext = ActionContext(typeContext.getMemoryAllocator().getTotal(), null)
    for (i in types.indices) {
      val t = types[i]
      t.initiateValues(actionContext, typesOffset[i], valueForTypes[t])
    }

    val valueHolder = ValueHolder()
    for (stmt in ast) {
      val inst = stmt.generateInstruction()
      try {
        inst.execute(actionContext, valueHolder)
      } catch (e: InstructionException) {
        throw formatException(e)
      }
    }

    return actionContext.getCurrentMem()
  }

  private fun formatException(e: InstructionException): Exception {
    val sb = StringBuilder()
    if (e.message != null) {
      sb.append(e.message).append("\n")
    }
    var isFirst = true
    for (info in e.stackTrace) {
      if (isFirst) {
        isFirst = false
      } else {
        sb.append("\n")
      }
      sb.append("  ").append(info)
    }
    return Exception(sb.toString(), e.cause)
  }
}
