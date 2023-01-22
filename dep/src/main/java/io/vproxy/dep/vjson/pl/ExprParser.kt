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
import io.vproxy.dep.vjson.pl.token.Token
import io.vproxy.dep.vjson.pl.token.TokenType
import io.vproxy.dep.vjson.simple.SimpleString
import io.vproxy.dep.vjson.util.CastUtils.cast

class ExprParser(private val tokenizer: ExprTokenizer) {
  fun parse(): Expr {
    val ctx = ParserContext(null, null)
    exprEntry(ctx)
    return ctx.exprStack.pop()
  }

  private fun exprEntry(ctx: ParserContext) {
    val token = tokenizer.peek() ?: throw ParserException("unexpected end of expression", tokenizer.offset)
    when (token.type) {
      TokenType.INTEGER -> integer(ctx)
      TokenType.FLOAT -> float(ctx)
      TokenType.BOOL_TRUE -> bool(ctx)
      TokenType.BOOL_FALSE -> bool(ctx)
      TokenType.KEY_NULL -> exprNull(ctx)
      TokenType.KEY_NEW -> exprNew(ctx)
      TokenType.VAR_NAME -> accessVar(ctx)
      TokenType.LEFT_PAR -> par(ctx)
      TokenType.PLUS -> positive(ctx)
      TokenType.MINUS -> negative(ctx)
      TokenType.LOGIC_NOT -> logicNot(ctx)
      TokenType.STRING -> string(ctx)
      else -> throw ParserException("unexpected token $token", token.lineCol)
    }
  }

  private fun exprBinOp(ctx: ParserContext) {
    if (!ctx.unaryOpStack.isEmpty()) {
      return
    }
    if (ctx.exprStack.isEmpty()) {
      exprEntry(ctx)
      return
    }
    val token = tokenizer.peek()
    if (token == null) {
      ctx.foldBinOp(0)
      return
    }
    when (token.type) {
      TokenType.PLUS -> binOp(ctx, BinOpType.PLUS)
      TokenType.MINUS -> binOp(ctx, BinOpType.MINUS)
      TokenType.MULTIPLY -> binOp(ctx, BinOpType.MULTIPLY)
      TokenType.DIVIDE -> binOp(ctx, BinOpType.DIVIDE)
      TokenType.MOD -> binOp(ctx, BinOpType.MOD)
      TokenType.CMP_GT -> binOp(ctx, BinOpType.CMP_GT)
      TokenType.CMP_GE -> binOp(ctx, BinOpType.CMP_GE)
      TokenType.CMP_LT -> binOp(ctx, BinOpType.CMP_LT)
      TokenType.CMP_LE -> binOp(ctx, BinOpType.CMP_LE)
      TokenType.CMP_NE -> binOp(ctx, BinOpType.CMP_NE)
      TokenType.CMP_EQ -> binOp(ctx, BinOpType.CMP_EQ)
      TokenType.LOGIC_AND -> binOp(ctx, BinOpType.LOGIC_AND)
      TokenType.LOGIC_OR -> binOp(ctx, BinOpType.LOGIC_OR)
      else -> exprEntry(ctx)
    }
  }

  private fun exprContinue(ctx: ParserContext) {
    val token = tokenizer.peek()
    if (token == null) {
      ctx.foldBinOp(0)
      return
    }
    when (token.type) {
      TokenType.PLUS, TokenType.MINUS, TokenType.MULTIPLY, TokenType.DIVIDE, TokenType.MOD,
      TokenType.CMP_GT, TokenType.CMP_GE, TokenType.CMP_LT, TokenType.CMP_LE, TokenType.CMP_NE, TokenType.CMP_EQ,
      TokenType.LOGIC_AND, TokenType.LOGIC_OR,
      -> exprBinOp(ctx)
      TokenType.PLUS_ASSIGN -> opAssign(ctx, BinOpType.PLUS)
      TokenType.MINUS_ASSIGN -> opAssign(ctx, BinOpType.MINUS)
      TokenType.MULTIPLY_ASSIGN -> opAssign(ctx, BinOpType.MULTIPLY)
      TokenType.DIVIDE_ASSIGN -> opAssign(ctx, BinOpType.DIVIDE)
      TokenType.MOD_ASSIGN -> opAssign(ctx, BinOpType.MOD)
      TokenType.RIGHT_PAR -> parEnd(ctx)
      TokenType.RIGHT_BRACKET -> bracketEnd(ctx)
      TokenType.DOT -> accessField(ctx)
      TokenType.COLON -> methodInvocation(ctx)
      TokenType.LEFT_BRACKET -> accessIndex(ctx)
      TokenType.COMMA -> terminate(ctx)
      else -> throw ParserException("unexpected token $token, expecting bin-operators, assignments or dot", token.lineCol)
    }
  }

  private fun parEnd(ctx: ParserContext) {
    if (ctx.beginToken == null) {
      throw ParserException("unexpected `)`, no matching `(` found for it", tokenizer.next()!!.lineCol)
    }
    if (ctx.beginToken != TokenType.LEFT_PAR) {
      throw ParserException("unexpected `)`, the begin token is not `(`: ${ctx.beginToken}", tokenizer.next()!!.lineCol)
    }
    tokenizer.next()
    ctx.foldBinOp(0)
    ctx.ends = true
    return
  }

  private fun bracketEnd(ctx: ParserContext) {
    if (ctx.beginToken == null) {
      throw ParserException("unexpected `]`, no matching `[` found for it", tokenizer.next()!!.lineCol)
    }
    if (ctx.beginToken != TokenType.LEFT_BRACKET) {
      throw ParserException("unexpected `]`, the begin token is not `[`: ${ctx.beginToken}", tokenizer.next()!!.lineCol)
    }
    tokenizer.next()
    ctx.foldBinOp(0)
    ctx.ends = true
    return
  }

  private fun accessField(ctx: ParserContext) {
    val token = tokenizer.next()!! // .
    val exp = ctx.exprStack.pop()
    val next = tokenizer.next() ?: throw ParserException("unexpected end of expression when trying to get field of $exp", token.lineCol)
    if (next.type != TokenType.VAR_NAME) {
      throw ParserException("unexpected token $next, expecting field name for accessing $exp", next.lineCol)
    }
    val access = Access(next.raw, from = exp)
    access.lineCol = token.lineCol
    ctx.exprStack.push(access)

    exprContinue(ctx)
  }

  private fun methodInvocation(ctx: ParserContext) {
    val token = tokenizer.next()!! // :
    val exp = ctx.exprStack.pop()
    val next = tokenizer.next() ?: throw ParserException("unexpected end of expression when trying to invoke function $exp", token.lineCol)
    if (next.type != TokenType.LEFT_BRACKET) {
      throw ParserException("unexpected token $next, expecting `[` for invoking $exp", next.lineCol)
    }
    val args = parseArguments(ctx, token.lineCol, "invoking function $exp")
    val funcInvoke = FunctionInvocation(exp, args)
    funcInvoke.lineCol = token.lineCol
    ctx.exprStack.push(funcInvoke)
    exprContinue(ctx)
  }

  private fun parseArguments(ctx: ParserContext, lineCol: LineCol, handlingTarget: String): List<Expr> {
    var next =
      tokenizer.peek() ?: throw ParserException("unexpected end of expression when preparing arguments for $handlingTarget", lineCol)
    if (next.type == TokenType.RIGHT_BRACKET) {
      tokenizer.next()
      return emptyList()
    }

    val subCtx = ParserContext(ctx, TokenType.LEFT_BRACKET)
    var argIdx = 0
    val args = ArrayList<Expr>()
    while (true) {
      exprEntry(subCtx)
      val arg = subCtx.exprStack.pop()
      args.add(arg)

      if (subCtx.ends) {
        break
      }
      next = tokenizer.peek() ?: throw ParserException(
        "unexpected end of expression when preparing arguments[$argIdx] for $handlingTarget",
        next.lineCol
      )
      if (next.type == TokenType.RIGHT_BRACKET) {
        tokenizer.next()
        break
      }
      ++argIdx
    }
    return args
  }

  private fun accessIndex(ctx: ParserContext) {
    val token = tokenizer.next()!! // [

    val expr = ctx.exprStack.pop()
    val next = tokenizer.peek() ?: throw ParserException("unexpected end of expression when trying to access index of $expr", token.lineCol)
    if (next.type == TokenType.RIGHT_BRACKET) {
      throw ParserException("unexpected token $next, index must be specified for accessing $expr", next.lineCol)
    }
    val subCtx = ParserContext(ctx, TokenType.LEFT_BRACKET)
    exprEntry(subCtx)
    if (!subCtx.ends) {
      throw ParserException(
        "only one element can be used to access index of $expr, the next token is " + (if (tokenizer.peek() == null) "eof" else tokenizer.peek()),
        expr.lineCol
      )
    }
    val indexExpr = subCtx.exprStack.pop()
    val accessIndex = AccessIndex(expr, indexExpr)
    accessIndex.lineCol = token.lineCol
    ctx.exprStack.push(accessIndex)

    exprContinue(ctx)
  }

  private fun terminate(ctx: ParserContext) {
    tokenizer.next()
    ctx.foldBinOp(0)
    return
  }

  private fun binOp(ctx: ParserContext, op: BinOpType) {
    val token = tokenizer.next()!!
    if (ctx.opStack.isEmpty() || ctx.opStack.peek().type.precedence < op.precedence) {
      ctx.opStack.push(ParserContext.OpInfo(op, token.lineCol))
      exprEntry(ctx)
    } else {
      ctx.foldBinOp(op.precedence)
      ctx.opStack.push(ParserContext.OpInfo(op, token.lineCol))
      exprEntry(ctx)
    }
  }

  private fun opAssign(ctx: ParserContext, op: BinOpType) {
    val token0 = tokenizer.next()!!
    if (ctx.exprStack.size() != 1) {
      throw ParserException("unable to handle assignment with multiple pending expressions ${ctx.exprStack}", token0.lineCol)
    }
    val variable = ctx.exprStack.pop()
    exprEntry(ctx)
    val next = ctx.exprStack.pop()
    if (variable !is AssignableExpr) {
      throw ParserException("$variable is not assignable while trying to $op=$next to it", token0.lineCol)
    }
    val opAssign = OpAssignment(op, variable = variable, value = next)
    opAssign.lineCol = token0.lineCol
    ctx.exprStack.push(opAssign)

    val token = tokenizer.peek()
    if (!isTerminator(token)) {
      if (token == null) {
        throw ParserException("expression not terminating after parsing ${ctx.exprStack.peek()}, got eof")
      } else {
        throw ParserException("expression not terminating after parsing ${ctx.exprStack.peek()}, got token $token", token.lineCol)
      }
    }
  }

  private fun integer(ctx: ParserContext) {
    val token = tokenizer.next()!!
    if (token.value is JSON.Integer || token.value is JSON.Long) {
      val intLiteral = IntegerLiteral(cast(token.value))
      intLiteral.lineCol = token.lineCol
      ctx.exprStack.push(intLiteral)
    } else {
      throw ParserException("unexpected value in token $token, expecting JSON.Integer or JSON.Long, but got ${token.value}", token.lineCol)
    }

    exprContinue(ctx)
  }

  private fun float(ctx: ParserContext) {
    val token = tokenizer.next()!!
    if (token.value is JSON.Double) {
      val floatLiteral = FloatLiteral(token.value)
      floatLiteral.lineCol = token.lineCol
      ctx.exprStack.push(floatLiteral)
    } else {
      throw ParserException("unexpected value in token $token, expecting JSON.Double, but got ${token.value}", token.lineCol)
    }

    exprContinue(ctx)
  }

  private fun bool(ctx: ParserContext) {
    val token = tokenizer.next()!!
    if (token.value is JSON.Bool) {
      val boolLiteral = BoolLiteral(token.value.booleanValue())
      boolLiteral.lineCol = token.lineCol
      ctx.exprStack.push(boolLiteral)
    } else {
      throw ParserException("unexpected value in token $token, expecting JSON.Bool, but got ${token.value}", token.lineCol)
    }

    exprContinue(ctx)
  }

  private fun exprNull(ctx: ParserContext) {
    val token = tokenizer.next()!!
    val nullLiteral = NullLiteral()
    nullLiteral.lineCol = token.lineCol
    ctx.exprStack.push(nullLiteral)

    exprContinue(ctx)
  }

  private fun exprNew(ctx: ParserContext) {
    val lineCol = tokenizer.next()!!.lineCol
    val typeToken =
      tokenizer.next() ?: throw ParserException("unexpected end of expression when trying to get the type to be instantiated", lineCol)
    if (typeToken.type != TokenType.VAR_NAME) {
      throw ParserException("unexpected token $typeToken, expecting the type to be instantiated", typeToken.lineCol)
    }
    val typeStr = typeToken.raw
    val mightBeBracketOrColon =
      tokenizer.peek() ?: throw ParserException(
        "unexpected end of expression when trying to identify type instantiation or array creation",
        lineCol
      )
    if (mightBeBracketOrColon.type == TokenType.COLON) {
      // call constructor
      tokenizer.next()
      val bracket = tokenizer.peek() ?: throw ParserException(
        "unexpected end of expression when invoking constructor of $typeStr, expecting `[`",
        lineCol
      )
      if (bracket.type == TokenType.LEFT_BRACE) {
        parseNewInstanceWithJson(ctx, typeStr, lineCol)
      } else {
        tokenizer.next()
        if (bracket.type != TokenType.LEFT_BRACKET) {
          throw ParserException("unexpected token $bracket, expecting `[` for invoking constructor of $typeStr", bracket.lineCol)
        }
        val args = parseArguments(ctx, lineCol, "invoking constructor of $typeStr")
        val newInst = NewInstance(Type(typeStr), args)
        newInst.lineCol = lineCol
        ctx.exprStack.push(newInst)
      }
      exprContinue(ctx)
    } else if (mightBeBracketOrColon.type == TokenType.LEFT_BRACE) {
      parseNewInstanceWithJson(ctx, typeStr, lineCol)
    } else if (mightBeBracketOrColon.type == TokenType.LEFT_BRACKET) {
      // new array
      tokenizer.next()
      val subCtx = ParserContext(ctx, TokenType.LEFT_BRACKET)
      exprEntry(subCtx)
      if (!subCtx.ends) {
        throw ParserException(
          "only one element can be used to create $typeStr array, the next token is " + (if (tokenizer.peek() == null) "eof" else tokenizer.peek()),
          mightBeBracketOrColon.lineCol
        )
      }
      val lenExpr = subCtx.exprStack.pop()
      var dimension = 1
      while (true) {
        val nx = tokenizer.peek()
        if (nx == null || nx.type != TokenType.LEFT_BRACKET) {
          break
        }
        tokenizer.next()
        val nxnx = tokenizer.next() ?: throw ParserException(
          "unexpected end of expression when trying to determine dimension of the new array",
          nx.lineCol
        )
        if (nxnx.type != TokenType.RIGHT_BRACKET) {
          throw ParserException("unexpected token $nxnx, expecting `]` when trying to determine dimension of the new array", nxnx.lineCol)
        }
        ++dimension
      }
      val newArray = NewArray(Type(typeStr + "[]".repeat(dimension)), lenExpr)
      newArray.lineCol = lineCol
      ctx.exprStack.push(newArray)
      exprContinue(ctx)
    } else {
      throw ParserException(
        "unexpected token $mightBeBracketOrColon, expecting `:` or `[` for the `new` expression",
        mightBeBracketOrColon.lineCol
      )
    }
  }

  private fun parseNewInstanceWithJson(ctx: ParserContext, typeStr: String, lineCol: LineCol) {
    val jsonObj = tokenizer.nextJsonObject()
    val expr = NewInstanceWithJson(Type(typeStr), newJsonConvert(ctx, jsonObj))
    expr.lineCol = lineCol
    ctx.exprStack.push(expr)
  }

  private fun newJsonConvert(ctx: ParserContext, v: JSON.Instance<*>): Any {
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
      is JSON.Object -> newJsonConvert(ctx, v)
      is JSON.Array -> newJsonConvert(ctx, v)
      is JSON.String -> newJsonConvert(ctx, v)
      else -> throw ParserException("unknown json instance $v", v.lineCol())
    }
  }

  private fun newJsonConvert(ctx: ParserContext, jsonObj: JSON.Object): LinkedHashMap<String, Any> {
    val map = LinkedHashMap<String, Any>()
    for (k in jsonObj.keyList()) {
      val v = jsonObj[k]
      map[k] = newJsonConvert(ctx, v)
    }
    return map
  }

  private fun newJsonConvert(ctx: ParserContext, jsonArr: JSON.Array): ArrayList<Any> {
    val ls = ArrayList<Any>()
    for (i in 0 until jsonArr.length()) {
      val e = jsonArr[i]
      ls.add(newJsonConvert(ctx, e))
    }
    return ls
  }

  private fun newJsonConvert(ctx: ParserContext, jsonStr: JSON.String): Expr {
    var str = jsonStr.toJavaObject()
    if (!str.startsWith("\${") || !str.endsWith("}")) {
      return StringLiteral(str)
    }
    str = str.substring(2, str.length - 1)
    val subCtx = ParserContext(ctx, null)
    val parser = ExprParser(ExprTokenizer(str, jsonStr.lineCol().inner()))
    parser.exprEntry(subCtx)
    if (subCtx.exprStack.isEmpty()) {
      throw ParserException("empty expression", jsonStr.lineCol())
    }
    if (subCtx.exprStack.size() > 1) {
      throw ParserException("early end of expression before finishing parsing", jsonStr.lineCol())
    }
    return subCtx.exprStack.pop()
  }

  private fun accessVar(ctx: ParserContext) {
    val token = tokenizer.next()!!
    val varname = token.raw
    val access = Access(varname)
    access.lineCol = token.lineCol
    ctx.exprStack.push(access)

    exprContinue(ctx)
  }

  private fun par(ctx: ParserContext) {
    val token = tokenizer.next()!!
    val nextCtx = ParserContext(ctx, token.type)
    exprEntry(nextCtx)
    ctx.exprStack.push(nextCtx.exprStack.pop())

    exprContinue(ctx)
  }

  private fun positive(ctx: ParserContext) {
    val token = tokenizer.next()!!
    ctx.unaryOpStack.push(1)
    exprEntry(ctx)
    val expr = ctx.exprStack.pop()
    val positive = Positive(expr)
    positive.lineCol = token.lineCol
    ctx.exprStack.push(positive)
    ctx.unaryOpStack.pop()

    exprContinue(ctx)
  }

  private fun negative(ctx: ParserContext) {
    val token = tokenizer.next()!!
    ctx.unaryOpStack.push(1)
    exprEntry(ctx)
    val expr = ctx.exprStack.pop()
    val negative = Negative(expr)
    negative.lineCol = token.lineCol
    ctx.exprStack.push(negative)
    ctx.unaryOpStack.pop()

    exprContinue(ctx)
  }

  private fun logicNot(ctx: ParserContext) {
    val token = tokenizer.next()!!
    ctx.unaryOpStack.push(1)
    exprEntry(ctx)
    val expr = ctx.exprStack.pop()
    val logicNot = LogicNot(expr)
    logicNot.lineCol = token.lineCol
    ctx.exprStack.push(logicNot)
    ctx.unaryOpStack.pop()

    exprContinue(ctx)
  }

  private fun string(ctx: ParserContext) {
    val token = tokenizer.next()
    val str = token!!.value as SimpleString
    val strLiteral = StringLiteral(str.toJavaObject())
    strLiteral.lineCol = token.lineCol
    ctx.exprStack.push(strLiteral)

    exprContinue(ctx)
  }

  private fun isTerminator(token: Token?): Boolean {
    if (token == null) {
      return true
    }
    return token.type.isTerminator
  }
}
