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

package io.vproxy.dep.vjson.pl.token

enum class TokenType(val isTerminator: Boolean = false) {
  INTEGER,
  FLOAT,
  BOOL_TRUE,
  BOOL_FALSE,
  KEY_NULL,
  KEY_NEW,
  LEFT_PAR,
  RIGHT_PAR,
  LEFT_BRACKET,
  RIGHT_BRACKET,
  PLUS,
  MINUS,
  MULTIPLY,
  DIVIDE,
  MOD,
  PLUS_ASSIGN,
  MINUS_ASSIGN,
  MULTIPLY_ASSIGN,
  DIVIDE_ASSIGN,
  MOD_ASSIGN,
  CMP_GT,
  CMP_GE,
  CMP_LT,
  CMP_LE,
  CMP_NE,
  CMP_EQ,
  LOGIC_NOT,
  LOGIC_AND,
  LOGIC_OR,
  VAR_NAME,
  DOT, // .
  COMMA(isTerminator = true), // ,
  COLON, // :
  STRING, // 'xxx', the token value is xxx without `'`
}
