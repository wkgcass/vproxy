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
package vjson.parser

import vjson.CharStream
import vjson.JSON
import vjson.Parser
import vjson.ex.JsonParseException
import vjson.ex.ParserFinishedException
import vjson.simple.SimpleDouble
import vjson.simple.SimpleExp
import vjson.simple.SimpleInteger
import vjson.simple.SimpleLong
import kotlin.math.pow

class NumberParser /*#ifndef KOTLIN_NATIVE {{ */ @JvmOverloads/*}}*/ constructor(
  opts: ParserOptions = ParserOptions.DEFAULT
) : Parser<JSON.Number<*>> {
  private val opts: ParserOptions = ParserOptions.ensureNotModifiedByOutside(opts)
  private var state = 0
  // 0->negative_or_1-9_or_0,
  // 1->got-negative(1-9_or_0),
  // 2->0-9_or_dot_or_e,
  // 3->after-dot(0-9),
  // 4->fraction(0-9)_or_e,
  // 5->after-e(symbol_or_0-9),
  // 6->after-e-got-symbol(0-9),
  // 7->after-e(0-9),
  // 8->finish,
  // 9->already_returned

  // this is a sub state indicating that the parser want to peek a character,
  // but not provided yet
  private var wantPeek = false

  companion object {
    private const val MAX_DIVISOR_ZEROS = 9
    private const val MAX_DIVISOR: Long = 1000000000
  }

  var isNegative = false
  var integer: Long = 0
    set(value) {
      require(value >= 0)
      field = value
    }
  private var hasFraction = false
  var fraction: Long = 0
    private set
  var fractionDivisorZeros = 0
    private set
  private var hasExponent = false
  var isExponentNegative = false
  var exponent = 0
    private set

  init {
    reset()
  }

  override fun reset() {
    state = 0
    wantPeek = false
    isNegative = false
    integer = 0
    hasFraction = false
    fraction = 0
    fractionDivisorZeros = 0
    hasExponent = false
    isExponentNegative = false
    exponent = 0
  }

  fun hasFraction(): Boolean {
    return hasFraction
  }

  fun clearFraction() {
    hasFraction = false
    fraction = 0
    fractionDivisorZeros = 0
  }

  fun setFraction(fraction: Long, divisorZeros: Int) {
    require(!(fraction < 0 || divisorZeros < 1))
    hasFraction = true
    this.fraction = fraction
    fractionDivisorZeros = divisorZeros
  }

  fun hasExponent(): Boolean {
    return hasExponent
  }

  fun setExponent(exponent: Int) {
    require(exponent >= 0)
    hasExponent = true
    this.exponent = exponent
  }

  fun clearExponent() {
    hasExponent = false
    isExponentNegative = false
    exponent = 0
  }

  private fun parseDigit(c: Char): Int {
    if (c in '0'..'9') {
      return c.toInt() - '0'.toInt()
    } else {
      return -1
    }
  }

  private fun fractionBegin() {
    hasFraction = true
    state = 3
    opts.listener.onNumberFractionBegin(this, integer)
  }

  private fun exponentBegin() {
    hasExponent = true
    state = 5
    opts.listener.onNumberExponentBegin(this, integer + (if (hasFraction) calcFraction() else 0.toDouble()))
  }

  private fun calcFraction(): Double {
    if (fractionDivisorZeros < MAX_DIVISOR_ZEROS) {
      var divisor: Long = 1
      for (i in 0 until fractionDivisorZeros) {
        divisor *= 10
      }
      return fraction / divisor.toDouble()
    }
    var fraction = fraction / MAX_DIVISOR.toDouble()
    var fractionDivisorZeros = fractionDivisorZeros - MAX_DIVISOR_ZEROS
    while (true) {
      if (fractionDivisorZeros < MAX_DIVISOR_ZEROS) {
        var divisor: Long = 1
        for (i in 0 until fractionDivisorZeros) {
          divisor *= 10
        }
        return fraction / divisor
      }
      fraction /= MAX_DIVISOR.toDouble()
      fractionDivisorZeros -= MAX_DIVISOR_ZEROS
    }
  }

  private fun gotoFractionExponentEnd(cs: CharStream, isComplete: Boolean) {
    if (cs.hasNext()) {
      val peek = cs.peekNext()
      if (peek == '.') {
        cs.moveNextAndGet()
        fractionBegin()
      } else if (peek == 'e' || peek == 'E') {
        cs.moveNextAndGet()
        exponentBegin()
      } else {
        // not number character, consider it ends the number literal
        state = 8
      }
    } else {
      if (isComplete) {
        // ends
        state = 8
      } else {
        wantPeek = true
      }
    }
  }

  @Suppress("DuplicatedCode")
  private fun tryParse(cs: CharStream, isComplete: Boolean): Boolean {
    if (wantPeek) {
      wantPeek = false
      gotoFractionExponentEnd(cs, isComplete)
    }

    var c: Char
    val err: String
    if (state == 0) {
      cs.skipBlank()
      if (cs.hasNext()) {
        opts.listener.onNumberBegin(this)
        c = cs.moveNextAndGet()
        if (c == '-') {
          isNegative = true
          state = 1
        } else {
          val d = parseDigit(c)
          if (d == -1) {
            err = "invalid digit in number: $c"
            throw ParserUtils.err(opts, err)
          }
          if (d == 0) {
            integer = 0
            gotoFractionExponentEnd(cs, isComplete)
          } else {
            integer = d.toLong()
            state = 2
          }
        }
      }
    }
    if (state == 1) {
      if (cs.hasNext()) {
        c = cs.moveNextAndGet()
        val d = parseDigit(c)
        if (d == -1) {
          err = "invalid digit in number: $c"
          throw ParserUtils.err(opts, err)
        }
        if (d == 0) {
          integer = 0
          gotoFractionExponentEnd(cs, isComplete)
        } else {
          integer = d.toLong()
          state = 2
        }
      }
    }
    while (cs.hasNext()) {
      if (state == 2) {
        c = cs.peekNext()
        if (c == '.') {
          cs.moveNextAndGet()
          fractionBegin()
        } else if (c == 'e' || c == 'E') {
          cs.moveNextAndGet()
          exponentBegin()
        } else {
          val d = parseDigit(c)
          if (d == -1) {
            state = 8
          } else {
            cs.moveNextAndGet()
            integer *= 10
            integer += d.toLong()
            // state not changed
            state = 2
          }
        }
      }
      if (state == 3) {
        if (cs.hasNext()) {
          c = cs.moveNextAndGet()
          val d = parseDigit(c)
          if (d == -1) {
            err = "invalid digit in fraction: $c"
            throw ParserUtils.err(opts, err)
          }
          // assert fraction = 0
          fraction = d.toLong()
          ++fractionDivisorZeros
          state = 4
        }
      }
      if (state == 4) {
        if (cs.hasNext()) {
          c = cs.peekNext()
          if (c == 'e' || c == 'E') {
            cs.moveNextAndGet()
            exponentBegin()
          } else {
            val d = parseDigit(c)
            if (d == -1) {
              state = 8
            } else {
              cs.moveNextAndGet()
              val nextFraction = fraction * 10
              if (nextFraction >= 0) {
                fraction = nextFraction
                fraction += d.toLong()
                ++fractionDivisorZeros
              }
              state = 4
              continue
            }
          }
        }
      }
      if (state == 5) {
        if (cs.hasNext()) {
          c = cs.moveNextAndGet()
          if (c == '+') {
            isExponentNegative = false
            state = 6
          } else if (c == '-') {
            isExponentNegative = true
            state = 6
          } else {
            val d = parseDigit(c)
            if (d == -1) {
              err = "invalid digit in exponent: $c"
              throw ParserUtils.err(opts, err)
            }
            exponent *= 10
            exponent += d
            state = 7
          }
        }
      }
      if (state == 6) {
        if (cs.hasNext()) {
          c = cs.moveNextAndGet()
          val d = parseDigit(c)
          if (d == -1) {
            err = "invalid digit in exponent: $c"
            throw ParserUtils.err(opts, err)
          }
          exponent *= 10
          exponent += d
          state = 7
        }
      }
      if (state == 7) {
        if (cs.hasNext()) {
          val peek = cs.peekNext()
          val d = parseDigit(peek)
          if (d == -1) {
            state = 8
          } else {
            cs.moveNextAndGet()
            exponent *= 10
            exponent += d
            state = 7
          }
        }
      }
      if (state == 8) {
        break
      }
      if (state == 9) {
        break
      }
    }
    if (state == 8) {
      ++state
      return true
    } else if (state == 9) {
      cs.skipBlank()
      if (cs.hasNext()) {
        throw ParserFinishedException()
      }
      return false
    } else if (isComplete) {
      // note: cs.hasNext() is false when reaches here
      if (state == 2 || state == 4 || state == 7) {
        state = 9
        return true
      } else {
        err = "expecting more characters to build number"
        throw ParserUtils.err(opts, err)
      }
    } else {
      return false
    }
  }

  @Throws(JsonParseException::class, ParserFinishedException::class)
  override fun build(cs: CharStream, isComplete: Boolean): JSON.Number<*>? {
    if (tryParse(cs, isComplete)) {
      opts.listener.onNumberEnd(this)
      val ret: JSON.Number<*>
      if (hasFraction) {
        var num = integer + calcFraction()
        num = if (isNegative) -num else num
        if (hasExponent) {
          ret = SimpleExp(num, if (isExponentNegative) -exponent else exponent)
        } else {
          ret = SimpleDouble(num)
        }
      } else {
        val num = if (isNegative) -integer else integer
        if (hasExponent) {
          ret = SimpleExp(num.toDouble(), if (isExponentNegative) -exponent else exponent)
        } else {
          if (isNegative) {
            if (num < Int.MIN_VALUE) {
              ret = SimpleLong(num)
            } else {
              ret = SimpleInteger(num.toInt())
            }
          } else {
            if (num > Int.MAX_VALUE) {
              ret = SimpleLong(num)
            } else {
              ret = SimpleInteger(num.toInt())
            }
          }
        }
      }
      opts.listener.onNumber(ret)

      ParserUtils.checkEnd(cs, opts, "number")
      return ret
    } else {
      return null
    }
  }

  @Throws(JsonParseException::class, ParserFinishedException::class)
  override fun buildJavaObject(cs: CharStream, isComplete: Boolean): Number? {
    if (tryParse(cs, isComplete)) {
      opts.listener.onNumberEnd(this)
      val ret: Number
      if (hasFraction) {
        var num = integer + calcFraction()
        num = if (isNegative) -num else num
        if (hasExponent) {
          ret = num * 10.0.pow(if (isExponentNegative) -exponent.toDouble() else exponent.toDouble())
        } else {
          ret = num
        }
      } else {
        val num = if (isNegative) -integer else integer
        if (hasExponent) {
          ret = num * 10.0.pow(if (isExponentNegative) -exponent.toDouble() else exponent.toDouble())
        } else {
          if (isNegative) {
            if (num < Int.MIN_VALUE) {
              ret = num
            } else {
              ret = num.toInt()
            }
          } else {
            if (num > Int.MAX_VALUE) {
              ret = num
            } else {
              ret = num.toInt()
            }
          }
        }
      }
      opts.listener.onNumber(ret)

      ParserUtils.checkEnd(cs, opts, "number")
      return ret
    } else {
      return null
    }
  }

  override fun completed(): Boolean {
    return state == 9
  }
}
