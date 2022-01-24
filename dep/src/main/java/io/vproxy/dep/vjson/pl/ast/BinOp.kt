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

package io.vproxy.dep.vjson.pl.ast

import io.vproxy.dep.vjson.ex.ParserException
import io.vproxy.dep.vjson.pl.ast.BinOpType.*
import io.vproxy.dep.vjson.pl.inst.*
import io.vproxy.dep.vjson.pl.type.*

data class BinOp(
  val op: BinOpType,
  val left: Expr,
  val right: Expr,
) : Expr() {
  override fun copy(): BinOp {
    val ret = BinOp(op, left.copy(), right.copy())
    ret.lineCol = lineCol
    return ret
  }

  override fun check(ctx: TypeContext): TypeInstance {
    this.ctx = ctx
    val leftType = left.check(ctx)
    val rightType = right.check(ctx)
    return when (op) {
      PLUS, MINUS, MULTIPLY, DIVIDE, MOD, CMP_GT, CMP_GE, CMP_LT, CMP_LE -> {
        if (op == PLUS && (leftType is StringType || rightType is StringType)) {
          if (leftType !is StringType || rightType !is StringType) {
            val typeToStringCheck: TypeInstance
            val variableToStringCheck: Expr
            if (leftType !is StringType) {
              typeToStringCheck = leftType
              variableToStringCheck = left
            } else {
              typeToStringCheck = rightType
              variableToStringCheck = right
            }
            val toStringField = typeToStringCheck.field(ctx, "toString", ctx.getContextType())
              ?: throw ParserException(
                "$this: cannot concat string, $variableToStringCheck ($typeToStringCheck) does not have `toString` field",
                lineCol
              )
            val toStringFunc = toStringField.type.functionDescriptor(ctx)
              ?: throw ParserException(
                "$this: cannot concat string, $variableToStringCheck ($typeToStringCheck) `toString` field is not a function",
                lineCol
              )
            if (toStringFunc.params.isNotEmpty())
              throw ParserException(
                "$this: cannot concat string, $variableToStringCheck ($typeToStringCheck) `toString` function parameters list is not empty",
                lineCol
              )
            if (toStringFunc.returnType !is StringType) {
              throw ParserException(
                "$this: cannot concat string, $variableToStringCheck ($typeToStringCheck) `toString` function return type (${toStringField.type}) is not $StringType",
                lineCol
              )
            }
          }
          StringType
        } else {
          if (leftType != rightType) {
            throw ParserException("$this: cannot calculate $leftType $op $rightType, type mismatch", lineCol)
          }

          if (leftType !is NumericTypeInstance) {
            throw ParserException("$this: cannot calculate $leftType $op $rightType, not numeric values", lineCol)
          }
          if (op == MOD) {
            if (leftType !is IntType && leftType !is LongType) {
              throw ParserException("$this: cannot calculate $leftType $op $rightType, must be int or long", lineCol)
            }
          }
          when (op) {
            PLUS, MINUS, MULTIPLY, DIVIDE, MOD -> leftType
            else -> BoolType
          }
        }
      }
      LOGIC_AND, LOGIC_OR -> {
        if (leftType != BoolType) {
          throw ParserException("$this: cannot calculate $leftType $op $rightType, not boolean values", lineCol)
        }
        if (rightType != BoolType) {
          throw ParserException("$this: cannot calculate $leftType $op $rightType, not boolean values", lineCol)
        }
        BoolType
      }
      CMP_NE, CMP_EQ -> {
        if (leftType != rightType) {
          if (leftType is NullType || rightType is NullType) {
            if (leftType !is PrimitiveTypeInstance && rightType !is PrimitiveTypeInstance) {
              // non-primitive types can compare to null
              return BoolType
            }
          }
          throw ParserException("$this: cannot calculate $leftType $op $rightType, type mismatch", lineCol)
        }
        BoolType
      }
    }
  }

  override fun typeInstance(): TypeInstance {
    return when (op) {
      PLUS -> if (left.typeInstance() is StringType || right.typeInstance() is StringType) StringType else left.typeInstance()
      MINUS, MULTIPLY, DIVIDE, MOD -> left.typeInstance()
      else -> BoolType
    }
  }

  override fun generateInstruction(): Instruction {
    val lType = left.typeInstance()
    val rType = right.typeInstance()
    val leftInst = left.generateInstruction()
    val rightInst = right.generateInstruction()
    return when (op) {
      MULTIPLY -> when (lType) {
        is IntType -> MultiplyInt(leftInst, rightInst, ctx.stackInfo(lineCol))
        is LongType -> MultiplyLong(leftInst, rightInst, ctx.stackInfo(lineCol))
        is FloatType -> MultiplyFloat(leftInst, rightInst, ctx.stackInfo(lineCol))
        is DoubleType -> MultiplyDouble(leftInst, rightInst, ctx.stackInfo(lineCol))
        else -> throw IllegalStateException("$lType $op $rType")
      }
      DIVIDE -> when (lType) {
        is IntType -> DivideInt(leftInst, rightInst, ctx.stackInfo(lineCol))
        is LongType -> DivideLong(leftInst, rightInst, ctx.stackInfo(lineCol))
        is FloatType -> DivideFloat(leftInst, rightInst, ctx.stackInfo(lineCol))
        is DoubleType -> DivideDouble(leftInst, rightInst, ctx.stackInfo(lineCol))
        else -> throw IllegalStateException("$lType $op $rType")
      }
      MOD -> when (lType) {
        is IntType -> ModInt(leftInst, rightInst, ctx.stackInfo(lineCol))
        is LongType -> ModLong(leftInst, rightInst, ctx.stackInfo(lineCol))
        else -> throw IllegalStateException("$lType $op $rType")
      }
      PLUS -> {
        if (lType is StringType || rType is StringType) {
          if (lType is StringType && rType is StringType) {
            StringConcat(leftInst, rightInst, ctx.stackInfo(lineCol))
          } else {
            val toStringFuncInst = Access.buildGetFieldInstruction(
              ctx,
              (if (lType is StringType) rightInst else leftInst),
              (if (lType is StringType) rType else lType),
              "toString",
              lineCol
            )
            val callToStringFuncInst = buildToStringInstruction(ctx, (if (lType is StringType) rType else lType), toStringFuncInst)
            if (lType is StringType)
              StringConcat(leftInst, callToStringFuncInst, ctx.stackInfo(lineCol))
            else
              StringConcat(callToStringFuncInst, rightInst, ctx.stackInfo(lineCol))
          }
        } else
          when (lType) {
            is IntType -> PlusInt(leftInst, rightInst, ctx.stackInfo(lineCol))
            is LongType -> PlusLong(leftInst, rightInst, ctx.stackInfo(lineCol))
            is FloatType -> PlusFloat(leftInst, rightInst, ctx.stackInfo(lineCol))
            is DoubleType -> PlusDouble(leftInst, rightInst, ctx.stackInfo(lineCol))
            else -> throw IllegalStateException("$lType $op $rType")
          }
      }
      MINUS -> when (lType) {
        is IntType -> MinusInt(leftInst, rightInst, ctx.stackInfo(lineCol))
        is LongType -> MinusLong(leftInst, rightInst, ctx.stackInfo(lineCol))
        is FloatType -> MinusFloat(leftInst, rightInst, ctx.stackInfo(lineCol))
        is DoubleType -> MinusDouble(leftInst, rightInst, ctx.stackInfo(lineCol))
        else -> throw IllegalStateException("$lType $op $rType")
      }
      CMP_GT -> when (lType) {
        is IntType -> CmpGTInt(leftInst, rightInst, ctx.stackInfo(lineCol))
        is LongType -> CmpGTLong(leftInst, rightInst, ctx.stackInfo(lineCol))
        is FloatType -> CmpGTFloat(leftInst, rightInst, ctx.stackInfo(lineCol))
        is DoubleType -> CmpGTDouble(leftInst, rightInst, ctx.stackInfo(lineCol))
        else -> throw IllegalStateException("$lType $op $rType")
      }
      CMP_GE -> when (lType) {
        is IntType -> CmpGEInt(leftInst, rightInst, ctx.stackInfo(lineCol))
        is LongType -> CmpGELong(leftInst, rightInst, ctx.stackInfo(lineCol))
        is FloatType -> CmpGEFloat(leftInst, rightInst, ctx.stackInfo(lineCol))
        is DoubleType -> CmpGEDouble(leftInst, rightInst, ctx.stackInfo(lineCol))
        else -> throw IllegalStateException("$lType $op $rType")
      }
      CMP_LT -> when (lType) {
        is IntType -> CmpLTInt(leftInst, rightInst, ctx.stackInfo(lineCol))
        is LongType -> CmpLTLong(leftInst, rightInst, ctx.stackInfo(lineCol))
        is FloatType -> CmpLTFloat(leftInst, rightInst, ctx.stackInfo(lineCol))
        is DoubleType -> CmpLTDouble(leftInst, rightInst, ctx.stackInfo(lineCol))
        else -> throw IllegalStateException("$lType $op $rType")
      }
      CMP_LE -> when (lType) {
        is IntType -> CmpLEInt(leftInst, rightInst, ctx.stackInfo(lineCol))
        is LongType -> CmpLELong(leftInst, rightInst, ctx.stackInfo(lineCol))
        is FloatType -> CmpLEFloat(leftInst, rightInst, ctx.stackInfo(lineCol))
        is DoubleType -> CmpLEDouble(leftInst, rightInst, ctx.stackInfo(lineCol))
        else -> throw IllegalStateException("$lType $op $rType")
      }
      CMP_NE -> when (lType) {
        is IntType -> CmpNEInt(leftInst, rightInst, ctx.stackInfo(lineCol))
        is LongType -> CmpNELong(leftInst, rightInst, ctx.stackInfo(lineCol))
        is FloatType -> CmpNEFloat(leftInst, rightInst, ctx.stackInfo(lineCol))
        is DoubleType -> CmpNEDouble(leftInst, rightInst, ctx.stackInfo(lineCol))
        is BoolType -> CmpNEBool(leftInst, rightInst, ctx.stackInfo(lineCol))
        else -> CmpNERef(leftInst, rightInst, ctx.stackInfo(lineCol))
      }
      CMP_EQ -> when (lType) {
        is IntType -> CmpEQInt(leftInst, rightInst, ctx.stackInfo(lineCol))
        is LongType -> CmpEQLong(leftInst, rightInst, ctx.stackInfo(lineCol))
        is FloatType -> CmpEQFloat(leftInst, rightInst, ctx.stackInfo(lineCol))
        is DoubleType -> CmpEQDouble(leftInst, rightInst, ctx.stackInfo(lineCol))
        is BoolType -> CmpEQBool(leftInst, rightInst, ctx.stackInfo(lineCol))
        else -> CmpEQRef(leftInst, rightInst, ctx.stackInfo(lineCol))
      }
      LOGIC_AND -> LogicAndBool(leftInst, rightInst, ctx.stackInfo(lineCol))
      LOGIC_OR -> LogicOrBool(leftInst, rightInst, ctx.stackInfo(lineCol))
    }
  }

  private fun buildToStringInstruction(ctx: TypeContext, variableType: TypeInstance, getFuncInst: Instruction): Instruction {
    val toStringField = variableType.field(ctx, "toString", ctx.getContextType())!!
    val toStringFunc = toStringField.type.functionDescriptor(ctx)!!
    val total = toStringFunc.mem.memoryAllocator().getTotal()

    val depth = if (variableType is ClassTypeInstance) {
      variableType.cls.getMemDepth()
    } else 0

    return object : InstructionWithStackInfo(ctx.stackInfo(lineCol)) {
      override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
        if (getFuncInst is FunctionInstance) {
          getFuncInst.ctxBuilder = { ActionContext(total, it) }
          getFuncInst.execute(ctx, values)
        } else {
          getFuncInst.execute(ctx, values)
          val func = values.refValue as Instruction
          val newCtx = ActionContext(total, ctx.getContext(depth))
          func.execute(newCtx, values)
        }
      }
    }
  }

  override fun toString(indent: Int): String {
    return "($left $op $right)"
  }

  override fun toString(): String {
    return toString(0)
  }
}
