package io.vproxy.dep.vjson.pl.inst

data class LiteralInt(
  val value: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    values.intValue = value
  }
}

data class LiteralLong(
  val value: Long,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    values.longValue = value
  }
}

data class LiteralFloat(
  val value: Float,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    values.floatValue = value
  }
}

data class LiteralDouble(
  val value: Double,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    values.doubleValue = value
  }
}

data class LiteralBool(
  val value: Boolean,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    values.boolValue = value
  }
}

data class LiteralRef(
  val value: Any?,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    values.refValue = value
  }
}

data class GetInt(
  val depth: Int,
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    values.intValue = ctx.getMem(depth).getInt(index)
  }
}

data class GetLong(
  val depth: Int,
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    values.longValue = ctx.getMem(depth).getLong(index)
  }
}

data class GetFloat(
  val depth: Int,
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    values.floatValue = ctx.getMem(depth).getFloat(index)
  }
}

data class GetDouble(
  val depth: Int,
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    values.doubleValue = ctx.getMem(depth).getDouble(index)
  }
}

data class GetBool(
  val depth: Int,
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    values.boolValue = ctx.getMem(depth).getBool(index)
  }
}

data class GetRef(
  val depth: Int,
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    values.refValue = ctx.getMem(depth).getRef(index)
  }
}

data class GetFieldInt(
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    val mem = values.refValue as ActionContext
    values.intValue = mem.getCurrentMem().getInt(index)
  }
}

data class GetFieldLong(
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    val mem = values.refValue as ActionContext
    values.longValue = mem.getCurrentMem().getLong(index)
  }
}

data class GetFieldFloat(
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    val mem = values.refValue as ActionContext
    values.floatValue = mem.getCurrentMem().getFloat(index)
  }
}

data class GetFieldDouble(
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    val mem = values.refValue as ActionContext
    values.doubleValue = mem.getCurrentMem().getDouble(index)
  }
}

data class GetFieldBool(
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    val mem = values.refValue as ActionContext
    values.boolValue = mem.getCurrentMem().getBool(index)
  }
}

data class GetFieldRef(
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    val mem = values.refValue as ActionContext
    values.refValue = mem.getCurrentMem().getRef(index)
  }
}

data class GetIndexInt(
  val array: Instruction,
  val index: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    array.execute(ctx, values)
    val arrayValue = values.refValue as IntArray
    index.execute(ctx, values)
    val indexValue = values.intValue
    values.intValue = arrayValue[indexValue]
  }
}

data class GetIndexLong(
  val array: Instruction,
  val index: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    array.execute(ctx, values)
    val arrayValue = values.refValue as LongArray
    index.execute(ctx, values)
    val indexValue = values.intValue
    values.longValue = arrayValue[indexValue]
  }
}

data class GetIndexFloat(
  val array: Instruction,
  val index: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    array.execute(ctx, values)
    val arrayValue = values.refValue as FloatArray
    index.execute(ctx, values)
    val indexValue = values.intValue
    values.floatValue = arrayValue[indexValue]
  }
}

data class GetIndexDouble(
  val array: Instruction,
  val index: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    array.execute(ctx, values)
    val arrayValue = values.refValue as DoubleArray
    index.execute(ctx, values)
    val indexValue = values.intValue
    values.doubleValue = arrayValue[indexValue]
  }
}

data class GetIndexBool(
  val array: Instruction,
  val index: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    array.execute(ctx, values)
    val arrayValue = values.refValue as BooleanArray
    index.execute(ctx, values)
    val indexValue = values.intValue
    values.boolValue = arrayValue[indexValue]
  }
}

data class GetIndexRef(
  val array: Instruction,
  val index: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    array.execute(ctx, values)
    val arrayValue = values.refValue as Array<Any?>
    index.execute(ctx, values)
    val indexValue = values.intValue
    values.refValue = arrayValue[indexValue]
  }
}

data class SetInt(
  val depth: Int,
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    ctx.getMem(depth).setInt(index, values.intValue)
  }
}

data class SetLong(
  val depth: Int,
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    ctx.getMem(depth).setLong(index, values.longValue)
  }
}

data class SetFloat(
  val depth: Int,
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    ctx.getMem(depth).setFloat(index, values.floatValue)
  }
}

data class SetDouble(
  val depth: Int,
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    ctx.getMem(depth).setDouble(index, values.doubleValue)
  }
}

data class SetBool(
  val depth: Int,
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    ctx.getMem(depth).setBool(index, values.boolValue)
  }
}

data class SetRef(
  val depth: Int,
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    ctx.getMem(depth).setRef(index, values.refValue)
  }
}

data class SetIndexInt(
  val array: Instruction,
  val index: Instruction,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    val value = values.intValue
    array.execute(ctx, values)
    val arrayValue = values.refValue as IntArray
    index.execute(ctx, values)
    val indexValue = values.intValue
    arrayValue[indexValue] = value
  }
}

data class SetIndexLong(
  val array: Instruction,
  val index: Instruction,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    val value = values.longValue
    array.execute(ctx, values)
    val arrayValue = values.refValue as LongArray
    index.execute(ctx, values)
    val indexValue = values.intValue
    arrayValue[indexValue] = value
  }
}

data class SetIndexFloat(
  val array: Instruction,
  val index: Instruction,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    val value = values.floatValue
    array.execute(ctx, values)
    val arrayValue = values.refValue as FloatArray
    index.execute(ctx, values)
    val indexValue = values.intValue
    arrayValue[indexValue] = value
  }
}

data class SetIndexDouble(
  val array: Instruction,
  val index: Instruction,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    val value = values.doubleValue
    array.execute(ctx, values)
    val arrayValue = values.refValue as DoubleArray
    index.execute(ctx, values)
    val indexValue = values.intValue
    arrayValue[indexValue] = value
  }
}

data class SetIndexBool(
  val array: Instruction,
  val index: Instruction,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    val value = values.boolValue
    array.execute(ctx, values)
    val arrayValue = values.refValue as BooleanArray
    index.execute(ctx, values)
    val indexValue = values.intValue
    arrayValue[indexValue] = value
  }
}

data class SetIndexRef(
  val array: Instruction,
  val index: Instruction,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    val value = values.refValue
    array.execute(ctx, values)
    val arrayValue = values.refValue as Array<Any?>
    index.execute(ctx, values)
    val indexValue = values.intValue
    arrayValue[indexValue] = value
  }
}

data class SetFieldInt(
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    val mem = values.refValue as ActionContext
    mem.getCurrentMem().setInt(index, values.intValue)
  }
}

data class SetFieldLong(
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    val mem = values.refValue as ActionContext
    mem.getCurrentMem().setLong(index, values.longValue)
  }
}

data class SetFieldFloat(
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    val mem = values.refValue as ActionContext
    mem.getCurrentMem().setFloat(index, values.floatValue)
  }
}

data class SetFieldDouble(
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    val mem = values.refValue as ActionContext
    mem.getCurrentMem().setDouble(index, values.doubleValue)
  }
}

data class SetFieldBool(
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    val mem = values.refValue as ActionContext
    mem.getCurrentMem().setBool(index, values.boolValue)
  }
}

data class SetFieldRef(
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    val mem = values.refValue as ActionContext
    mem.getCurrentMem().setRef(index, values.refValue)
  }
}

data class PlusInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.intValue
    right.execute(ctx, values)
    val rightValue = values.intValue
    values.intValue = leftValue + rightValue
  }
}

data class PlusLong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.longValue
    right.execute(ctx, values)
    val rightValue = values.longValue
    values.longValue = leftValue + rightValue
  }
}

data class PlusFloat(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.floatValue
    right.execute(ctx, values)
    val rightValue = values.floatValue
    values.floatValue = leftValue + rightValue
  }
}

data class PlusDouble(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.doubleValue
    right.execute(ctx, values)
    val rightValue = values.doubleValue
    values.doubleValue = leftValue + rightValue
  }
}

data class MinusInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.intValue
    right.execute(ctx, values)
    val rightValue = values.intValue
    values.intValue = leftValue - rightValue
  }
}

data class MinusLong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.longValue
    right.execute(ctx, values)
    val rightValue = values.longValue
    values.longValue = leftValue - rightValue
  }
}

data class MinusFloat(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.floatValue
    right.execute(ctx, values)
    val rightValue = values.floatValue
    values.floatValue = leftValue - rightValue
  }
}

data class MinusDouble(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.doubleValue
    right.execute(ctx, values)
    val rightValue = values.doubleValue
    values.doubleValue = leftValue - rightValue
  }
}

data class MultiplyInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.intValue
    right.execute(ctx, values)
    val rightValue = values.intValue
    values.intValue = leftValue * rightValue
  }
}

data class MultiplyLong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.longValue
    right.execute(ctx, values)
    val rightValue = values.longValue
    values.longValue = leftValue * rightValue
  }
}

data class MultiplyFloat(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.floatValue
    right.execute(ctx, values)
    val rightValue = values.floatValue
    values.floatValue = leftValue * rightValue
  }
}

data class MultiplyDouble(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.doubleValue
    right.execute(ctx, values)
    val rightValue = values.doubleValue
    values.doubleValue = leftValue * rightValue
  }
}

data class DivideInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.intValue
    right.execute(ctx, values)
    val rightValue = values.intValue
    values.intValue = leftValue / rightValue
  }
}

data class DivideLong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.longValue
    right.execute(ctx, values)
    val rightValue = values.longValue
    values.longValue = leftValue / rightValue
  }
}

data class DivideFloat(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.floatValue
    right.execute(ctx, values)
    val rightValue = values.floatValue
    values.floatValue = leftValue / rightValue
  }
}

data class DivideDouble(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.doubleValue
    right.execute(ctx, values)
    val rightValue = values.doubleValue
    values.doubleValue = leftValue / rightValue
  }
}

data class ModInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.intValue
    right.execute(ctx, values)
    val rightValue = values.intValue
    values.intValue = leftValue % rightValue
  }
}

data class ModLong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.longValue
    right.execute(ctx, values)
    val rightValue = values.longValue
    values.longValue = leftValue % rightValue
  }
}

data class CmpGTInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.intValue
    right.execute(ctx, values)
    val rightValue = values.intValue
    values.boolValue = leftValue > rightValue
  }
}

data class CmpGTLong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.longValue
    right.execute(ctx, values)
    val rightValue = values.longValue
    values.boolValue = leftValue > rightValue
  }
}

data class CmpGTFloat(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.floatValue
    right.execute(ctx, values)
    val rightValue = values.floatValue
    values.boolValue = leftValue > rightValue
  }
}

data class CmpGTDouble(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.doubleValue
    right.execute(ctx, values)
    val rightValue = values.doubleValue
    values.boolValue = leftValue > rightValue
  }
}

data class CmpGEInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.intValue
    right.execute(ctx, values)
    val rightValue = values.intValue
    values.boolValue = leftValue >= rightValue
  }
}

data class CmpGELong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.longValue
    right.execute(ctx, values)
    val rightValue = values.longValue
    values.boolValue = leftValue >= rightValue
  }
}

data class CmpGEFloat(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.floatValue
    right.execute(ctx, values)
    val rightValue = values.floatValue
    values.boolValue = leftValue >= rightValue
  }
}

data class CmpGEDouble(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.doubleValue
    right.execute(ctx, values)
    val rightValue = values.doubleValue
    values.boolValue = leftValue >= rightValue
  }
}

data class CmpLTInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.intValue
    right.execute(ctx, values)
    val rightValue = values.intValue
    values.boolValue = leftValue < rightValue
  }
}

data class CmpLTLong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.longValue
    right.execute(ctx, values)
    val rightValue = values.longValue
    values.boolValue = leftValue < rightValue
  }
}

data class CmpLTFloat(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.floatValue
    right.execute(ctx, values)
    val rightValue = values.floatValue
    values.boolValue = leftValue < rightValue
  }
}

data class CmpLTDouble(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.doubleValue
    right.execute(ctx, values)
    val rightValue = values.doubleValue
    values.boolValue = leftValue < rightValue
  }
}

data class CmpLEInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.intValue
    right.execute(ctx, values)
    val rightValue = values.intValue
    values.boolValue = leftValue <= rightValue
  }
}

data class CmpLELong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.longValue
    right.execute(ctx, values)
    val rightValue = values.longValue
    values.boolValue = leftValue <= rightValue
  }
}

data class CmpLEFloat(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.floatValue
    right.execute(ctx, values)
    val rightValue = values.floatValue
    values.boolValue = leftValue <= rightValue
  }
}

data class CmpLEDouble(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.doubleValue
    right.execute(ctx, values)
    val rightValue = values.doubleValue
    values.boolValue = leftValue <= rightValue
  }
}

data class LogicAndBool(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.boolValue
    right.execute(ctx, values)
    val rightValue = values.boolValue
    values.boolValue = leftValue && rightValue
  }
}

data class LogicOrBool(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.boolValue
    right.execute(ctx, values)
    val rightValue = values.boolValue
    values.boolValue = leftValue || rightValue
  }
}

data class CmpNEInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.intValue
    right.execute(ctx, values)
    val rightValue = values.intValue
    values.boolValue = leftValue != rightValue
  }
}

data class CmpNELong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.longValue
    right.execute(ctx, values)
    val rightValue = values.longValue
    values.boolValue = leftValue != rightValue
  }
}

data class CmpNEFloat(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.floatValue
    right.execute(ctx, values)
    val rightValue = values.floatValue
    values.boolValue = leftValue != rightValue
  }
}

data class CmpNEDouble(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.doubleValue
    right.execute(ctx, values)
    val rightValue = values.doubleValue
    values.boolValue = leftValue != rightValue
  }
}

data class CmpNEBool(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.boolValue
    right.execute(ctx, values)
    val rightValue = values.boolValue
    values.boolValue = leftValue != rightValue
  }
}

data class CmpNERef(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.refValue
    right.execute(ctx, values)
    val rightValue = values.refValue
    values.boolValue = leftValue != rightValue
  }
}

data class CmpEQInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.intValue
    right.execute(ctx, values)
    val rightValue = values.intValue
    values.boolValue = leftValue == rightValue
  }
}

data class CmpEQLong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.longValue
    right.execute(ctx, values)
    val rightValue = values.longValue
    values.boolValue = leftValue == rightValue
  }
}

data class CmpEQFloat(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.floatValue
    right.execute(ctx, values)
    val rightValue = values.floatValue
    values.boolValue = leftValue == rightValue
  }
}

data class CmpEQDouble(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.doubleValue
    right.execute(ctx, values)
    val rightValue = values.doubleValue
    values.boolValue = leftValue == rightValue
  }
}

data class CmpEQBool(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.boolValue
    right.execute(ctx, values)
    val rightValue = values.boolValue
    values.boolValue = leftValue == rightValue
  }
}

data class CmpEQRef(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.refValue
    right.execute(ctx, values)
    val rightValue = values.refValue
    values.boolValue = leftValue == rightValue
  }
}

data class NegativeInt(
  private val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    values.intValue = -values.intValue
  }
}

data class NegativeLong(
  private val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    values.longValue = -values.longValue
  }
}

data class NegativeFloat(
  private val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    values.floatValue = -values.floatValue
  }
}

data class NegativeDouble(
  private val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    values.doubleValue = -values.doubleValue
  }
}

data class NewArrayInt(
  val lenInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    lenInst.execute(ctx, values)
    values.refValue = IntArray(values.intValue)
  }
}

data class NewArrayLong(
  val lenInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    lenInst.execute(ctx, values)
    values.refValue = LongArray(values.intValue)
  }
}

data class NewArrayFloat(
  val lenInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    lenInst.execute(ctx, values)
    values.refValue = FloatArray(values.intValue)
  }
}

data class NewArrayDouble(
  val lenInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    lenInst.execute(ctx, values)
    values.refValue = DoubleArray(values.intValue)
  }
}

data class NewArrayBool(
  val lenInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    lenInst.execute(ctx, values)
    values.refValue = BooleanArray(values.intValue)
  }
}

data class NewArrayRef(
  val lenInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    lenInst.execute(ctx, values)
    values.refValue = Array<Any?>(values.intValue) { null }
  }
}
