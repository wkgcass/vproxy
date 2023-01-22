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

import io.vproxy.dep.vjson.pl.ast.Statement
import io.vproxy.dep.vjson.pl.inst.*
import io.vproxy.dep.vjson.pl.type.MemoryAllocator
import io.vproxy.dep.vjson.pl.type.TypeContext
import io.vproxy.dep.vjson.pl.type.lang.Types

class Interpreter(private val types: List<Types>, ast: List<Statement>) {
  private val typesOffset = ArrayList<RuntimeMemoryTotal>()
  private val typeContext = TypeContext(MemoryAllocator())
  private val valueForTypes = HashMap<Types, RuntimeMemory>()
  private val explorer: RuntimeMemoryExplorer
  private val instructions: List<Instruction>

  init {
    var offset = RuntimeMemoryTotal()
    for (t in types) {
      typesOffset.add(offset)
      offset = t.initiateType(typeContext, offset)
    }

    typeContext.checkStatements(ast)

    val explorerBuilder = RuntimeMemoryExplorer.Builder()
    explorerBuilder.feed(ast)
    explorer = explorerBuilder.build()

    instructions = ast.map { it.generateInstruction() }
  }

  fun putValues(t: Types, values: RuntimeMemory) {
    valueForTypes[t] = values
  }

  fun removeValues(t: Types) {
    valueForTypes.remove(t)
  }

  fun getExplorer(): RuntimeMemoryExplorer = explorer

  fun execute(): RuntimeMemory {
    val actionContext = ActionContext(typeContext.getMemoryAllocator().getTotal(), null)
    for (i in types.indices) {
      val t = types[i]
      t.initiateValues(actionContext, typesOffset[i], valueForTypes[t])
    }

    val exec = Execution()
    for (inst in instructions) {
      try {
        inst.execute(actionContext, exec)
      } catch (e: InstructionException) {
        throw Exception(e.formatException(), e.cause)
      }
    }

    return actionContext.getCurrentMem()
  }
}
