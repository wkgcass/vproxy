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

import io.vproxy.dep.vjson.cs.LineCol
import io.vproxy.dep.vjson.pl.ast.BinOp
import io.vproxy.dep.vjson.pl.ast.BinOpType
import io.vproxy.dep.vjson.pl.ast.Expr
import io.vproxy.dep.vjson.pl.token.TokenType
import io.vproxy.dep.vjson.util.collection.Stack

class ParserContext(val parent: ParserContext?, val beginToken: TokenType?) {
  val exprStack = Stack<Expr>()
  val opStack = Stack<OpInfo>()
  val unaryOpStack = Stack<Any>()
  var ends = false

  fun foldBinOp(precedence: Int) {
    if (!unaryOpStack.isEmpty()) {
      return // do not fold if unary op is still being handled
    }
    while (!opStack.isEmpty()) {
      val op = opStack.peek()
      if (op.type.precedence < precedence) {
        break
      }
      opStack.pop()
      val right = exprStack.pop()
      val left = exprStack.pop()
      val binOp = BinOp(op.type, left, right)
      binOp.lineCol = op.lineCol
      exprStack.push(binOp)
    }
  }

  data class OpInfo(val type: BinOpType, val lineCol: LineCol)
}
