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
import io.vproxy.dep.vjson.cs.LineCol
import io.vproxy.dep.vjson.ex.ParserException
import io.vproxy.dep.vjson.pl.ast.*
import io.vproxy.dep.vjson.util.CastUtils.cast

class ASTGen(_prog: JSON.Object) {
  private val prog = _prog.entryList().listIterator()
  private val result = ArrayList<Statement>()
  fun parse(): List<Statement> {
    while (prog.hasNext()) {
      val entry = prog.next()
      val stmt = when (entry.key) {
        "class" -> aClass(entry)
        "public" -> modifier(entry, ModifierEnum.PUBLIC)
        "private" -> modifier(entry, ModifierEnum.PRIVATE)
        "const" -> modifier(entry, ModifierEnum.CONST)
        "executable" -> modifier(entry, ModifierEnum.EXECUTABLE)
        "function" -> function(entry)
        "var" -> aVar(entry)
        "new" -> aNew(entry)
        "for" -> aFor(entry)
        "while" -> aWhile(entry)
        "if" -> aIf(entry)
        "break" -> aBreak(entry)
        "continue" -> aContinue(entry)
        "return" -> aReturn(entry)
        "throw" -> aThrow(entry)
        "template" -> template(entry)
        "let" -> aLet(entry)
        else -> exprKey(entry)
      }
      stmt.lineCol = entry.lineCol
      result.add(stmt)
    }
    return result
  }

  /**
   * ```
   * class ClassName: { param: "type" } do: {
   *   statements
   * }
   * ```
   */
  private fun aClass(entry: JSON.ObjectEntry): ClassDefinition {
    if (entry.value !is JSON.Null) {
      throw ParserException("unexpected token ${entry.value}, expecting null as value for key `class`", entry.value.lineCol())
    }
    if (!prog.hasNext()) {
      throw ParserException("unexpected end of object, expecting class name", entry.lineCol)
    }
    val nameAndParams = prog.next()
    val className = nameAndParams.key
    if (nameAndParams.value !is JSON.Object) {
      throw ParserException("expecting parameters for class `$className`, but got ${nameAndParams.value}", nameAndParams.value.lineCol())
    }
    val params = nameAndParams.value
    if (params.keySet().size != params.keyList().size) {
      throw ParserException("duplicated parameter name for class `$className`", nameAndParams.value.lineCol())
    }
    val astParams = ArrayList<Param>()
    params.entryList().forEachIndexed { idx, e ->
      if (e.value !is JSON.String) {
        throw ParserException(
          "parameter type must be a string, type for parameters[$idx] is ${e.value} in class `$className`",
          e.value.lineCol()
        )
      }
      var type = e.value.toJavaObject()
      var defaultValue: Expr? = null
      if (type.contains("=")) {
        val index = type.indexOf("=")
        val defaultValueExprStr = type.substring(index + 1).trim()
        type = type.substring(0, index).trim()
        defaultValue = exprString(defaultValueExprStr, e.value.lineCol().addCol(index))
      }
      val typeObj = Type(type)
      astParams.add(Param(e.key, typeObj, defaultValue))
    }

    if (!prog.hasNext()) {
      throw ParserException("unexpected end of object, expecting class content", entry.lineCol)
    }
    val doAndCode = prog.next()
    if (doAndCode.key != "do") {
      throw ParserException("unexpected token $doAndCode, expecting `do` and class content", doAndCode.lineCol)
    }

    if (doAndCode.value !is JSON.Object) {
      throw ParserException(
        "class content must be encapsulated into a json object, but got ${doAndCode.value} for class `$className`",
        doAndCode.value.lineCol()
      )
    }
    val astCode = ASTGen(doAndCode.value).parse()

    return ClassDefinition(className, astParams, astCode)
  }

  private fun modifier(entry: JSON.ObjectEntry, modifier: ModifierEnum): Statement {
    if (entry.value !is JSON.Null) {
      throw ParserException("unexpected token ${entry.value}, expecting null as value for key `public`", entry.value.lineCol())
    }
    val modifiers = Modifiers(modifier.num)
    var nextEntry: JSON.ObjectEntry
    while (true) {
      if (!prog.hasNext()) {
        throw ParserException("unexpected end of object, expecting variable or function definition", entry.lineCol)
      }
      nextEntry = prog.next()
      if (!ModifierEnum.isModifier(nextEntry.key)) {
        break
      }
      @Suppress("DEPRECATION")
      modifiers.modifiers = modifiers.modifiers.or(ModifierEnum.valueOf(nextEntry.key.toUpperCase()).num)
    }
    if (modifiers.isPublic() && modifiers.isPrivate()) {
      throw ParserException("invalid modifiers: $modifiers, cannot set public and private at the same time", nextEntry.lineCol)
    }
    return when (nextEntry.key) {
      "var" -> {
        val res = aVar(nextEntry)
        VariableDefinition(res.name, res.value, modifiers)
      }
      "function" -> {
        val res = function(nextEntry)
        FunctionDefinition(res.name, res.params, res.returnType, res.code, modifiers)
      }
      else -> {
        throw ParserException("unexpected token $nextEntry, expecting variable or function definition", nextEntry.lineCol)
      }
    }
  }

  /**
   * ```
   * function funcName: { param: "type" } returnType: {
   *   statements
   * }
   * ```
   */
  private fun function(entry: JSON.ObjectEntry): FunctionDefinition {
    if (entry.value !is JSON.Null) {
      throw ParserException("unexpected token ${entry.value}, expecting null as value for key `function`", entry.value.lineCol())
    }
    if (!prog.hasNext()) {
      throw ParserException("unexpected end of object, expecting function name", entry.lineCol)
    }
    val nameAndParams = prog.next()
    val funcName = nameAndParams.key
    if (nameAndParams.value !is JSON.Object) {
      throw ParserException("expecting parameters for function `$funcName`, but got ${nameAndParams.value}", nameAndParams.value.lineCol())
    }
    val params = nameAndParams.value
    if (params.keySet().size != params.keyList().size) {
      throw ParserException("duplicated parameter name for function `$funcName`", nameAndParams.value.lineCol())
    }
    val astParams = ArrayList<Param>()
    params.entryList().forEachIndexed { idx, e ->
      if (e.value !is JSON.String) {
        throw ParserException(
          "parameter type must be a string, type for parameters[$idx] is ${e.value} in function `$funcName`",
          e.value.lineCol()
        )
      }
      val type = e.value.toJavaObject()
      astParams.add(Param(e.key, Type(type)))
    }

    if (!prog.hasNext()) {
      throw ParserException("unexpected end of object, expecting function return type", entry.lineCol)
    }
    val returnTypeAndCode = prog.next()
    val astReturnType = Type(returnTypeAndCode.key)

    if (returnTypeAndCode.value !is JSON.Object) {
      throw ParserException(
        "function payload must be encapsulated into a json object, but got ${returnTypeAndCode.value} for function `$funcName`",
        returnTypeAndCode.value.lineCol()
      )
    }
    val astCode = ASTGen(returnTypeAndCode.value).parse()

    return FunctionDefinition(funcName, astParams, astReturnType, astCode)
  }

  /**
   * ```
   * var varname: initValue
   * ```
   */
  private fun aVar(entry: JSON.ObjectEntry): VariableDefinition {
    if (entry.value !is JSON.Null) {
      throw ParserException("unexpected token ${entry.value}, expecting null as value for key `var`", entry.value.lineCol())
    }
    if (!prog.hasNext()) {
      throw ParserException("unexpected end of object, expecting variable name", entry.lineCol)
    }
    val nextEntry = prog.next()
    val varname = nextEntry.key
    val value = expr(nextEntry.value)
    return VariableDefinition(varname, value)
  }

  /**
   * ```
   * new type
   * // or
   * new type: [ ...args... ]
   * ```
   */
  private fun aNew(entry: JSON.ObjectEntry): Expr {
    if (entry.value !is JSON.Null) {
      throw ParserException("unexpected token ${entry.value}, expecting null as value for key `new`", entry.value.lineCol())
    }
    if (!prog.hasNext()) {
      throw ParserException("unexpected end of object, expecting the type to be instantiated", entry.lineCol)
    }
    val nextEntry = prog.next()
    var typeStr = nextEntry.key
    if (typeStr.startsWith("(") && typeStr.endsWith(")")) {
      typeStr = typeStr.substring(1, typeStr.length - 1)
    }
    return if (typeStr.contains("[")) {
      if (!typeStr.endsWith("]")) {
        throw ParserException(
          "unexpected type for creating array: found `[` in type string but it does not end with `]`: $typeStr",
          nextEntry.lineCol
        )
      }
      val elementType = typeStr.substring(0, typeStr.indexOf("["))
      val lenEndIndex = typeStr.indexOf("]", typeStr.indexOf("[") + 1)
      val lenStr = typeStr.substring(typeStr.indexOf("[") + 1, lenEndIndex)
      val lenExpr = exprString(lenStr, nextEntry.lineCol.inner().addCol(typeStr.indexOf("[") + 1))

      if (nextEntry.value !is JSON.Null) {
        throw ParserException(
          "unexpected token ${nextEntry.value} for new array statement, expecting null value after key `$typeStr`",
          nextEntry.value.lineCol()
        )
      }
      NewArray(Type(elementType + "[]" + typeStr.substring(lenEndIndex + 1)), lenExpr)
    } else {
      when (nextEntry.value) {
        is JSON.Null -> NewInstance(Type(typeStr), listOf())
        is JSON.Array -> NewInstance(Type(typeStr), exprArray(nextEntry.value))
        is JSON.Object -> parseNewInstanceWithJson(nextEntry.value, typeStr, entry.lineCol)
        else -> throw ParserException(
          "unexpected token ${nextEntry.value} for new instance statement, expecting null or array value after key `$typeStr`",
          nextEntry.value.lineCol()
        )
      }
    }
  }

  private fun parseNewInstanceWithJson(jsonObj: JSON.Object, typeStr: String, lineCol: LineCol): Expr {
    val expr = NewInstanceWithJson(Type(typeStr), newJsonConvert(jsonObj))
    expr.lineCol = lineCol
    return expr
  }

  private fun newJsonConvert(v: JSON.Instance<*>): Any {
    return when (v) {
      is JSON.Integer, is JSON.Long -> {
        val ret = IntegerLiteral(v as JSON.Number<*>)
        ret.lineCol = v.lineCol()
        ret
      }
      is JSON.Double -> {
        val ret = FloatLiteral(v)
        ret.lineCol = v.lineCol()
        ret
      }
      is JSON.Bool -> {
        val ret = BoolLiteral(v.booleanValue())
        ret.lineCol = v.lineCol()
        ret
      }
      is JSON.Null -> {
        val ret = NullLiteral()
        ret.lineCol = v.lineCol()
        ret
      }
      is JSON.Object -> newJsonConvert(v)
      is JSON.Array -> newJsonConvert(v)
      is JSON.String -> newJsonConvert(v)
      else -> throw ParserException("unknown json instance $v", v.lineCol())
    }
  }

  private fun newJsonConvert(jsonObj: JSON.Object): LinkedHashMap<String, Any> {
    val map = LinkedHashMap<String, Any>()
    for (k in jsonObj.keyList()) {
      val v = jsonObj[k]
      map[k] = newJsonConvert(v)
    }
    return map
  }

  private fun newJsonConvert(jsonArr: JSON.Array): ArrayList<Any> {
    val ls = ArrayList<Any>()
    for (i in 0 until jsonArr.length()) {
      val e = jsonArr[i]
      ls.add(newJsonConvert(e))
    }
    return ls
  }

  private fun newJsonConvert(jsonStr: JSON.String): Expr {
    var str = jsonStr.toJavaObject()
    if (!str.startsWith("\${") || !str.endsWith("}")) {
      return StringLiteral(str)
    }
    str = str.substring(2, str.length - 1)
    return exprString(str, jsonStr.lineCol().inner())
  }

  private fun aFor(entry: JSON.ObjectEntry): ForLoop {
    if (entry.value !is JSON.Array) {
      throw ParserException("unexpected token ${entry.value}, expecting a 3-element array for `for` loop", entry.value.lineCol())
    }
    val array = entry.value
    if (array.length() != 3) {
      throw ParserException("unexpected token ${entry.value}, expecting a 3-element array for `for` loop", entry.value.lineCol())
    }
    val init = array[0]
    val cond = array[1]
    val incr = array[2]

    val astInit = if (init is JSON.Object) {
      ASTGen(init).parse()
    } else {
      listOf(expr(init))
    }
    val astCond = expr(cond)
    val astIncr = if (incr is JSON.Object) {
      ASTGen(incr).parse()
    } else {
      listOf(expr(incr))
    }

    if (!prog.hasNext()) {
      throw ParserException("unexpected end of object, expecting code for the `for` loop", entry.lineCol)
    }
    val nextEntry = prog.next()
    if (nextEntry.key != "do") {
      throw ParserException("unexpected token $nextEntry, expecting `do` to begin the `for` loop code", nextEntry.lineCol)
    }
    if (nextEntry.value !is JSON.Object) {
      throw ParserException("unexpected token ${nextEntry.value}, expecting code block for the `for` loop", nextEntry.value.lineCol())
    }
    val code = ASTGen(nextEntry.value).parse()

    return ForLoop(astInit, astCond, astIncr, code)
  }

  private fun aWhile(entry: JSON.ObjectEntry): WhileLoop {
    val astCond = expr(entry.value)
    if (!prog.hasNext()) {
      throw ParserException("unexpected end of object, expecting code for the `while` loop", entry.lineCol)
    }
    val nextEntry = prog.next()
    if (nextEntry.key != "do") {
      throw ParserException("unexpected token $nextEntry, expecting `do` to begin the `while` loop code", nextEntry.lineCol)
    }
    if (nextEntry.value !is JSON.Object) {
      throw ParserException("unexpected token ${nextEntry.value}, expecting code block for the `while` loop", nextEntry.value.lineCol())
    }
    val code = ASTGen(nextEntry.value).parse()

    return WhileLoop(astCond, code)
  }

  private fun aIf(entry: JSON.ObjectEntry): Statement {
    val astCond = expr(entry.value)
    val isErrorHandling = (
      astCond == BinOp(BinOpType.CMP_NE, Access("err"), NullLiteral()) ||
        astCond == BinOp(BinOpType.CMP_NE, NullLiteral(), Access("err")))

    if (!prog.hasNext()) {
      throw ParserException("unexpected end of object, expecting content for `if`", entry.lineCol)
    }
    val nextEntry = prog.next()
    if (nextEntry.key != "then") {
      throw ParserException("unexpected token $nextEntry, expecting `then` after `if`", nextEntry.lineCol)
    }
    if (nextEntry.value !is JSON.Object) {
      throw ParserException("unexpected token ${nextEntry.value}, expecting code block for `if`", nextEntry.value.lineCol())
    }
    val ifCode = ASTGen(nextEntry.value).parse()

    if (!prog.hasNext()) {
      return checkAndGenerateErrorHandling(isErrorHandling, IfStatement(astCond, ifCode, listOf()))
    }
    val nextNextEntry = prog.next()
    if (nextNextEntry.key == "else") {
      when (nextNextEntry.value) {
        is JSON.Null -> {
          // expecting else if
          if (!prog.hasNext()) {
            throw ParserException(
              "unexpected end of object, found `else` without colon `:`, but not following another `if`",
              nextNextEntry.lineCol
            )
          }
          val nextNextNextEntry = prog.next()
          if (nextNextNextEntry.key != "if") {
            throw ParserException(
              "unexpected token $nextNextNextEntry, found `else: null`, but not following another `if`, you need to use `else: {...}` for else block ",
              nextNextNextEntry.lineCol
            )
          }
          if (isErrorHandling) {
            throw ParserException("unexpected else-if block, error handling can only have `else` block", nextNextEntry.lineCol)
          }
          val nextIf = aIf(nextNextNextEntry)
          return IfStatement(astCond, ifCode, listOf(nextIf))
        }
        is JSON.Object -> {
          val elseCode = ASTGen(nextNextEntry.value).parse()
          return checkAndGenerateErrorHandling(isErrorHandling, IfStatement(astCond, ifCode, elseCode))
        }
        else -> throw ParserException(
          "unexpected token ${nextNextEntry.value}, expecting code block for `else`",
          nextNextEntry.value.lineCol()
        )
      }
    } else {
      // not if statement
      prog.previous()
      return checkAndGenerateErrorHandling(isErrorHandling, IfStatement(astCond, ifCode, listOf()))
    }
  }

  private fun checkAndGenerateErrorHandling(isErrorHandling: Boolean, aIf: IfStatement): Statement {
    if (!isErrorHandling) {
      return aIf
    }
    var lastErrorHandlingIndex = -1
    for ((idx, stmt) in result.withIndex()) {
      if (stmt is ErrorHandlingStatement) {
        lastErrorHandlingIndex = idx
      }
    }
    val ls: ArrayList<Statement>
    if (result.isEmpty()) {
      ls = ArrayList()
    } else {
      ls = ArrayList(result.subList(lastErrorHandlingIndex + 1, result.size))
      val foo = ArrayList(result.subList(0, lastErrorHandlingIndex + 1))
      result.clear()
      result.addAll(foo)
    }
    return ErrorHandlingStatement(ls, aIf.ifCode, aIf.elseCode)
  }

  private fun aBreak(entry: JSON.ObjectEntry): BreakStatement {
    if (entry.value !is JSON.Null) {
      throw ParserException("unexpected token ${entry.value}, expecting null as value for key `break`", entry.value.lineCol())
    }
    return BreakStatement()
  }

  private fun aContinue(entry: JSON.ObjectEntry): ContinueStatement {
    if (entry.value !is JSON.Null) {
      throw ParserException("unexpected token ${entry.value}, expecting null as value for key `continue`", entry.value.lineCol())
    }
    return ContinueStatement()
  }

  private fun aReturn(entry: JSON.ObjectEntry): ReturnStatement {
    return if (entry.value is JSON.Null) {
      ReturnStatement()
    } else {
      ReturnStatement(expr(entry.value))
    }
  }

  private fun aThrow(entry: JSON.ObjectEntry): ThrowStatement {
    return if (entry.value is JSON.Null) {
      ThrowStatement()
    } else {
      ThrowStatement(expr(entry.value))
    }
  }

  /**
   * ```
   * template: { T, U, V } class ClassName: {} do: { }
   * ```
   */
  private fun template(entry: JSON.ObjectEntry): TemplateClassDefinition {
    if (entry.value !is JSON.Object) {
      throw ParserException(
        "unexpected token ${entry.value}, expecting { ... } for defining param type names",
        entry.value.lineCol()
      )
    }
    val paramTypesObj = entry.value
    val types = ArrayList<ParamType>()
    val typeNames = HashSet<String>()
    for (p in paramTypesObj.entryList()) {
      if (p.value !is JSON.Null) {
        throw ParserException("unexpected token ${p.value}, expecting null as value for the param type name", p.value.lineCol())
      }
      if (!typeNames.add(p.key)) {
        throw ParserException("duplicated param type name", p.lineCol)
      }
      types.add(ParamType(p.key))
    }
    if (!prog.hasNext()) {
      throw ParserException("unexpected end of object, expecting class definition after `template`", entry.lineCol)
    }
    val nextEntry = prog.next()
    if (nextEntry.key != "class") {
      throw ParserException("unexpected token $nextEntry, expecting class definition after `template`", entry.lineCol)
    }
    val cls = aClass(nextEntry)
    return TemplateClassDefinition(types, cls)
  }

  /**
   * ```
   * let Type = { TemplateType:[Type1, Type2] }
   * ```
   */
  private fun aLet(entry: JSON.ObjectEntry): TemplateTypeInstantiation {
    if (entry.value !is JSON.Null) {
      throw ParserException("unexpected token ${entry.value}, expecting null for key `let`", entry.value.lineCol())
    }
    if (!prog.hasNext()) {
      throw ParserException("unexpected end of object, expecting the type to be defined from a template type", entry.lineCol)
    }
    val next = prog.next()
    val typeName = next.key
    if (next.value !is JSON.Object) {
      throw ParserException("unexpected token ${entry.value}, expecting object for constructing the type", next.value.lineCol())
    }
    val obj = next.value.entryList()
    if (obj.isEmpty()) {
      throw ParserException("unexpected token ${entry.value}, expecting object for constructing the type", next.value.lineCol())
    }
    if (obj.size != 1) {
      throw ParserException(
        "unexpected token ${entry.value}, expecting object for constructing the type, got extra tokens",
        next.value.lineCol()
      )
    }
    val paramTypeName = obj[0].key
    if (obj[0].value !is JSON.Array) {
      throw ParserException("unexpected token ${obj[0].value}, expecting array for type parameters", obj[0].value.lineCol())
    }
    val arr = obj[0].value as JSON.Array
    val typeParams = ArrayList<Type>()
    for (i in 0 until arr.length()) {
      val x = arr[i]
      if (x !is JSON.String) {
        throw ParserException("unexpected token $x, expecting string for type parameters", x.lineCol())
      }
      typeParams.add(Type(x.toJavaObject()))
    }
    return TemplateTypeInstantiation(typeName, Type(paramTypeName), typeParams)
  }

  private fun exprKey(_entry: JSON.ObjectEntry): Expr {
    var entry = _entry
    var binOp: BinOpType? = null
    var key = entry.key
    if (key.endsWith("+") || key.endsWith("-") || key.endsWith("*") || key.endsWith("/") || key.endsWith("%")) {
      binOp = when (key.last()) {
        '+' -> BinOpType.PLUS; '-' -> BinOpType.MINUS; '*' -> BinOpType.MULTIPLY; '/' -> BinOpType.DIVIDE; else -> BinOpType.MOD
      }
      key = key.substring(0, key.length - 1)
    } else if (entry.value is JSON.Null) {
      // check this kind of statement: a += 1
      if (prog.hasNext()) {
        val nxt = prog.next()
        if (nxt.key == "+" || nxt.key == "-" || nxt.key == "*" || nxt.key == "/" || nxt.key == "%") {
          binOp = when (nxt.key) {
            "+" -> BinOpType.PLUS; "-" -> BinOpType.MINUS; "*" -> BinOpType.MULTIPLY; "/" -> BinOpType.DIVIDE; else -> BinOpType.MOD
          }
          entry = nxt
        } else {
          prog.previous() // rollback
        }
      }
    }
    val tokenizer = ExprTokenizer(key, entry.lineCol.inner())
    val parser = ExprParser(tokenizer)
    val expr = parser.parse()
    if (tokenizer.peek() != null) {
      throw ParserException(
        "only one expression can be used, but multiple found, next token: ${tokenizer.peek()}",
        tokenizer.currentLineCol()
      )
    }
    return if (entry.value is JSON.Array) {
      if (binOp != null) {
        throw ParserException("unexpected token ${entry.value}, cannot be used with $binOp", entry.value.lineCol())
      }
      callFunction(expr, cast(entry.value))
    } else if (expr is NullLiteral) {
      if (entry.value !is JSON.String) {
        throw ParserException("unexpected token ${entry.value}, expecting type for the `null` literal", entry.value.lineCol())
      }
      if (binOp != null) {
        throw ParserException("unexpected token ${entry.key}, unexpected token $binOp", entry.lineCol)
      }
      NullLiteral(Type(cast(entry.value.toJavaObject())))
    } else {
      if (expr !is AssignableExpr) {
        throw ParserException("unable to assign value to $expr", expr.lineCol)
      }
      if (binOp != null) {
        OpAssignment(binOp, expr, expr(entry.value))
      } else {
        Assignment(expr, expr(entry.value))
      }
    }
  }

  private fun callFunction(funcExpr: Expr, args: JSON.Array): FunctionInvocation {
    val exprArgs = ArrayList<Expr>(args.length())
    for (i in 0 until args.length()) {
      exprArgs.add(expr(args[i]))
    }
    return FunctionInvocation(funcExpr, exprArgs)
  }

  private fun expr(json: JSON.Instance<*>): Expr {
    return when (json) {
      is JSON.Object -> exprObject(json)
      is JSON.String -> exprString(json.toJavaObject(), json.lineCol().inner())
      is JSON.Bool -> BoolLiteral(json.booleanValue())
      is JSON.Integer, is JSON.Long -> IntegerLiteral(cast(json))
      is JSON.Double -> FloatLiteral(json)
      is JSON.Null -> NullLiteral()
      else -> throw ParserException("unexpected expression $json", json.lineCol())
    }
  }

  private fun exprObject(json: JSON.Object): Expr {
    val stmts = ASTGen(json).parse()
    if (stmts.size != 1) {
      throw ParserException("unexpected ast $stmts, expecting one and only one expression to be generated", json.lineCol())
    }
    val stmt = stmts[0]
    if (stmt !is Expr) {
      throw ParserException("unexpected ast $stmt, expecting expression", stmt.lineCol)
    }
    return stmt
  }

  private fun exprArray(json: JSON.Array): List<Expr> {
    val res = ArrayList<Expr>(json.length())
    for (i in 0 until json.length()) {
      res.add(expr(json[i]))
    }
    return res
  }

  private fun exprString(input: String, lineCol: LineCol): Expr {
    val tokenizer = ExprTokenizer(input, lineCol)
    val parser = ExprParser(tokenizer)
    val expr = parser.parse()
    if (tokenizer.peek() != null) {
      throw ParserException("only one expression can be used, but multiple found, next token: ${tokenizer.peek()}", lineCol)
    }
    return expr
  }
}
