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

package io.vproxy.dep.vjson.pl.type

import io.vproxy.dep.vjson.cs.LineCol
import io.vproxy.dep.vjson.ex.ParserException
import io.vproxy.dep.vjson.pl.ast.*
import io.vproxy.dep.vjson.pl.inst.StackInfo

class TypeContext {
  private val contextType: TypeInstance?
  private val ast: AST?
  private val parent: TypeContext?
  private val memoryAllocator: MemoryAllocator
  private val typeNameMap: MutableMap<Type, TypeInstance> = HashMap()
  private val functionDescriptorSet: MutableSet<FunctionDescriptor> = HashSet()
  private val variableMap: MutableMap<String, Variable> = HashMap()
  private val memoryDepth: Int

  constructor(parent: TypeContext, contextType: TypeInstance? = null, ast: AST? = null) {
    this.contextType = contextType ?: parent.contextType
    this.ast = ast
    this.parent = parent
    this.memoryAllocator = if (ast is MemoryAllocatorProvider) ast.memoryAllocator() else parent.memoryAllocator
    this.memoryDepth = parent.memoryDepth + (if (ast is MemoryAllocatorProvider) 1 else 0)
  }

  constructor(globalMemory: MemoryAllocator) {
    this.contextType = null
    this.ast = null
    this.parent = rootContext
    this.memoryAllocator = globalMemory
    this.memoryDepth = 0
  }

  @Suppress("UNUSED_PARAMETER")
  private constructor(i: Int) {
    this.contextType = null
    this.ast = null
    this.parent = null
    this.memoryAllocator = MemoryAllocator() // will never be used
    this.memoryDepth = -1
  }

  fun getContextType(): TypeInstance? {
    return contextType
  }

  fun hasType(type: Type): Boolean {
    return if (hasTypeInThisContext(type)) true else parent?.hasType(type) ?: false
  }

  fun hasTypeInThisContext(type: Type): Boolean {
    return typeNameMap.containsKey(type)
  }

  fun getType(type: Type): TypeInstance {
    return typeNameMap[type] ?: (parent?.getType(type) ?: throw NoSuchElementException(type.toString()))
  }

  fun addType(astType: Type, type: TypeInstance) {
    if (typeNameMap.containsKey(astType)) {
      throw IllegalStateException("type $astType already exists")
    }
    typeNameMap[astType] = type
  }

  fun getFunctionDescriptor(
    params: List<ParamInstance>,
    returnType: TypeInstance,
    mem: MemoryAllocatorProvider
  ): FunctionDescriptor {
    val desc = FunctionDescriptor(params, returnType, mem)
    val res = functionDescriptorSet.find { it == desc }
    if (res == null) {
      functionDescriptorSet.add(desc)
      return desc
    }
    return res
  }

  fun getFunctionDescriptorAsInstance(
    params: List<ParamInstance>,
    returnType: TypeInstance,
    mem: MemoryAllocatorProvider,
  ): FunctionDescriptorTypeInstance {
    return FunctionDescriptorTypeInstance(getFunctionDescriptor(params, returnType, mem))
  }

  fun hasVariable(name: String): Boolean {
    return if (hasVariableInThisContext(name)) true else parent?.hasVariable(name) ?: false
  }

  fun hasVariableInThisContext(name: String): Boolean {
    return variableMap.containsKey(name)
  }

  fun getVariable(name: String): Variable {
    return variableMap[name] ?: (parent?.getVariable(name) ?: throw NoSuchElementException())
  }

  fun addVariable(variable: Variable) {
    if (variableMap.containsKey(variable.name)) {
      throw IllegalStateException("variable ${variable.name} already exists")
    }
    variableMap[variable.name] = variable
  }

  fun checkStatements(code: List<Statement>) {
    for ((index, stmt) in code.withIndex()) {
      stmt.checkAST(this)
      if (stmt.functionTerminationCheck() || stmt is BreakStatement || stmt is ContinueStatement) {
        // no code should exist after this stmt
        if (code.size != index + 1) {
          // build error message
          val nextStmts = ArrayList<Statement>(code.size - index - 1)
          val ite = code.listIterator(index + 1)
          while (ite.hasNext()) {
            nextStmts.add(ite.next())
          }
          throw ParserException("no statement should appear after $stmt, but got: $nextStmts")
        }
      }
    }
  }

  fun getContextAST(func: (AST) -> Boolean): AST? {
    if (ast != null && func(ast)) {
      return ast
    }
    if (parent != null) {
      return parent.getContextAST(func)
    }
    return null
  }

  fun getContextByAST(func: (AST) -> Boolean): TypeContext? {
    if (ast != null && func(ast)) {
      return this
    }
    if (parent != null) {
      return parent.getContextByAST(func)
    }
    return null
  }

  fun getMemoryAllocator(): MemoryAllocator {
    return memoryAllocator
  }

  fun getMemoryDepth(): Int {
    return memoryDepth
  }

  fun stackInfo(lineCol: LineCol): StackInfo {
    val funcCtx = getContextByAST { it is FunctionDefinition }
    val clsCtx = getContextByAST { it is ClassDefinition }
    return if (clsCtx != null && funcCtx != null) {
      if (clsCtx.memoryDepth < funcCtx.memoryDepth) {
        StackInfo(cls = (clsCtx.ast as ClassDefinition).name, function = (funcCtx.ast as FunctionDefinition).name, lineCol)
      } else {
        StackInfo(cls = (clsCtx.ast as ClassDefinition).name, function = null, lineCol)
      }
    } else if (clsCtx == null && funcCtx != null) {
      StackInfo(cls = null, function = (funcCtx.ast as FunctionDefinition).name, lineCol)
    } else if (clsCtx != null) {
      StackInfo(cls = (clsCtx.ast as ClassDefinition).name, function = null, lineCol)
    } else {
      StackInfo(cls = null, function = null, lineCol)
    }
  }

  private constructor(
    contextType: TypeInstance?,
    ast: AST?,
    parent: TypeContext?,
    memoryAllocator: MemoryAllocator,
    typeNameMap: Map<Type, TypeInstance>,
    functionDescriptorSet: Set<FunctionDescriptor>,
    variableMap: Map<String, Variable>,
    memoryDepth: Int
  ) {
    this.contextType = contextType
    this.ast = ast
    this.parent = parent
    this.memoryAllocator = memoryAllocator
    this.typeNameMap.putAll(typeNameMap)
    this.functionDescriptorSet.addAll(functionDescriptorSet)
    this.variableMap.putAll(variableMap)
    this.memoryDepth = memoryDepth
  }

  fun copy(): TypeContext {
    return TypeContext(contextType, ast, parent, memoryAllocator, typeNameMap, functionDescriptorSet, variableMap, memoryDepth)
  }

  companion object {
    private val rootContext = TypeContext(0)

    init {
      rootContext.addType(Type("int"), IntType)
      rootContext.addType(Type("long"), LongType)
      rootContext.addType(Type("float"), FloatType)
      rootContext.addType(Type("double"), DoubleType)
      rootContext.addType(Type("string"), StringType)
      rootContext.addType(Type("bool"), BoolType)
      rootContext.addType(Type("void"), VoidType)
      rootContext.addType(Type("error"), ErrorType)
    }
  }
}
