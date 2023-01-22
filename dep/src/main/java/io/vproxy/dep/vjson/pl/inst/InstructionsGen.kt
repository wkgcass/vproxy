package io.vproxy.dep.vjson.pl.inst

data class LiteralInt(
  val value: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    exec.values.intValue = value
  }
}

data class LiteralLong(
  val value: Long,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    exec.values.longValue = value
  }
}

data class LiteralFloat(
  val value: Float,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    exec.values.floatValue = value
  }
}

data class LiteralDouble(
  val value: Double,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    exec.values.doubleValue = value
  }
}

data class LiteralBool(
  val value: Boolean,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    exec.values.boolValue = value
  }
}

data class LiteralRef(
  val value: Any?,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    exec.values.refValue = value
  }
}

data class GetInt(
  val depth: Int,
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    exec.values.intValue = ctx.getMem(depth).getInt(index)
  }
}

data class GetLong(
  val depth: Int,
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    exec.values.longValue = ctx.getMem(depth).getLong(index)
  }
}

data class GetFloat(
  val depth: Int,
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    exec.values.floatValue = ctx.getMem(depth).getFloat(index)
  }
}

data class GetDouble(
  val depth: Int,
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    exec.values.doubleValue = ctx.getMem(depth).getDouble(index)
  }
}

data class GetBool(
  val depth: Int,
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    exec.values.boolValue = ctx.getMem(depth).getBool(index)
  }
}

data class GetRef(
  val depth: Int,
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    exec.values.refValue = ctx.getMem(depth).getRef(index)
  }
}

data class GetFieldInt(
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    val mem = exec.values.refValue as ActionContext
    exec.values.intValue = mem.getCurrentMem().getInt(index)
  }
}

data class GetFieldLong(
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    val mem = exec.values.refValue as ActionContext
    exec.values.longValue = mem.getCurrentMem().getLong(index)
  }
}

data class GetFieldFloat(
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    val mem = exec.values.refValue as ActionContext
    exec.values.floatValue = mem.getCurrentMem().getFloat(index)
  }
}

data class GetFieldDouble(
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    val mem = exec.values.refValue as ActionContext
    exec.values.doubleValue = mem.getCurrentMem().getDouble(index)
  }
}

data class GetFieldBool(
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    val mem = exec.values.refValue as ActionContext
    exec.values.boolValue = mem.getCurrentMem().getBool(index)
  }
}

data class GetFieldRef(
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    val mem = exec.values.refValue as ActionContext
    exec.values.refValue = mem.getCurrentMem().getRef(index)
  }
}

data class GetIndexInt(
  val array: Instruction,
  val index: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    array.execute(ctx, exec)
    val arrayValue = exec.values.refValue as IntArray
    index.execute(ctx, exec)
    val indexValue = exec.values.intValue
    exec.values.intValue = arrayValue[indexValue]
  }
}

data class GetIndexLong(
  val array: Instruction,
  val index: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    array.execute(ctx, exec)
    val arrayValue = exec.values.refValue as LongArray
    index.execute(ctx, exec)
    val indexValue = exec.values.intValue
    exec.values.longValue = arrayValue[indexValue]
  }
}

data class GetIndexFloat(
  val array: Instruction,
  val index: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    array.execute(ctx, exec)
    val arrayValue = exec.values.refValue as FloatArray
    index.execute(ctx, exec)
    val indexValue = exec.values.intValue
    exec.values.floatValue = arrayValue[indexValue]
  }
}

data class GetIndexDouble(
  val array: Instruction,
  val index: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    array.execute(ctx, exec)
    val arrayValue = exec.values.refValue as DoubleArray
    index.execute(ctx, exec)
    val indexValue = exec.values.intValue
    exec.values.doubleValue = arrayValue[indexValue]
  }
}

data class GetIndexBool(
  val array: Instruction,
  val index: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    array.execute(ctx, exec)
    val arrayValue = exec.values.refValue as BooleanArray
    index.execute(ctx, exec)
    val indexValue = exec.values.intValue
    exec.values.boolValue = arrayValue[indexValue]
  }
}

data class GetIndexRef(
  val array: Instruction,
  val index: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    array.execute(ctx, exec)
    val arrayValue = exec.values.refValue as Array<Any?>
    index.execute(ctx, exec)
    val indexValue = exec.values.intValue
    exec.values.refValue = arrayValue[indexValue]
  }
}

data class SetInt(
  val depth: Int,
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    ctx.getMem(depth).setInt(index, exec.values.intValue)
  }
}

data class SetLong(
  val depth: Int,
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    ctx.getMem(depth).setLong(index, exec.values.longValue)
  }
}

data class SetFloat(
  val depth: Int,
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    ctx.getMem(depth).setFloat(index, exec.values.floatValue)
  }
}

data class SetDouble(
  val depth: Int,
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    ctx.getMem(depth).setDouble(index, exec.values.doubleValue)
  }
}

data class SetBool(
  val depth: Int,
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    ctx.getMem(depth).setBool(index, exec.values.boolValue)
  }
}

data class SetRef(
  val depth: Int,
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    ctx.getMem(depth).setRef(index, exec.values.refValue)
  }
}

data class SetIndexInt(
  val array: Instruction,
  val index: Instruction,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    val value = exec.values.intValue
    array.execute(ctx, exec)
    val arrayValue = exec.values.refValue as IntArray
    index.execute(ctx, exec)
    val indexValue = exec.values.intValue
    arrayValue[indexValue] = value
  }
}

data class SetIndexLong(
  val array: Instruction,
  val index: Instruction,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    val value = exec.values.longValue
    array.execute(ctx, exec)
    val arrayValue = exec.values.refValue as LongArray
    index.execute(ctx, exec)
    val indexValue = exec.values.intValue
    arrayValue[indexValue] = value
  }
}

data class SetIndexFloat(
  val array: Instruction,
  val index: Instruction,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    val value = exec.values.floatValue
    array.execute(ctx, exec)
    val arrayValue = exec.values.refValue as FloatArray
    index.execute(ctx, exec)
    val indexValue = exec.values.intValue
    arrayValue[indexValue] = value
  }
}

data class SetIndexDouble(
  val array: Instruction,
  val index: Instruction,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    val value = exec.values.doubleValue
    array.execute(ctx, exec)
    val arrayValue = exec.values.refValue as DoubleArray
    index.execute(ctx, exec)
    val indexValue = exec.values.intValue
    arrayValue[indexValue] = value
  }
}

data class SetIndexBool(
  val array: Instruction,
  val index: Instruction,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    val value = exec.values.boolValue
    array.execute(ctx, exec)
    val arrayValue = exec.values.refValue as BooleanArray
    index.execute(ctx, exec)
    val indexValue = exec.values.intValue
    arrayValue[indexValue] = value
  }
}

data class SetIndexRef(
  val array: Instruction,
  val index: Instruction,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    val value = exec.values.refValue
    array.execute(ctx, exec)
    val arrayValue = exec.values.refValue as Array<Any?>
    index.execute(ctx, exec)
    val indexValue = exec.values.intValue
    arrayValue[indexValue] = value
  }
}

data class SetFieldInt(
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    val mem = exec.values.refValue as ActionContext
    mem.getCurrentMem().setInt(index, exec.values.intValue)
  }
}

data class SetFieldLong(
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    val mem = exec.values.refValue as ActionContext
    mem.getCurrentMem().setLong(index, exec.values.longValue)
  }
}

data class SetFieldFloat(
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    val mem = exec.values.refValue as ActionContext
    mem.getCurrentMem().setFloat(index, exec.values.floatValue)
  }
}

data class SetFieldDouble(
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    val mem = exec.values.refValue as ActionContext
    mem.getCurrentMem().setDouble(index, exec.values.doubleValue)
  }
}

data class SetFieldBool(
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    val mem = exec.values.refValue as ActionContext
    mem.getCurrentMem().setBool(index, exec.values.boolValue)
  }
}

data class SetFieldRef(
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    val mem = exec.values.refValue as ActionContext
    mem.getCurrentMem().setRef(index, exec.values.refValue)
  }
}

data class PlusInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.intValue
    right.execute(ctx, exec)
    val rightValue = exec.values.intValue
    exec.values.intValue = leftValue + rightValue
  }
}

data class PlusLong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.longValue
    right.execute(ctx, exec)
    val rightValue = exec.values.longValue
    exec.values.longValue = leftValue + rightValue
  }
}

data class PlusFloat(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.floatValue
    right.execute(ctx, exec)
    val rightValue = exec.values.floatValue
    exec.values.floatValue = leftValue + rightValue
  }
}

data class PlusDouble(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.doubleValue
    right.execute(ctx, exec)
    val rightValue = exec.values.doubleValue
    exec.values.doubleValue = leftValue + rightValue
  }
}

data class MinusInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.intValue
    right.execute(ctx, exec)
    val rightValue = exec.values.intValue
    exec.values.intValue = leftValue - rightValue
  }
}

data class MinusLong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.longValue
    right.execute(ctx, exec)
    val rightValue = exec.values.longValue
    exec.values.longValue = leftValue - rightValue
  }
}

data class MinusFloat(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.floatValue
    right.execute(ctx, exec)
    val rightValue = exec.values.floatValue
    exec.values.floatValue = leftValue - rightValue
  }
}

data class MinusDouble(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.doubleValue
    right.execute(ctx, exec)
    val rightValue = exec.values.doubleValue
    exec.values.doubleValue = leftValue - rightValue
  }
}

data class MultiplyInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.intValue
    right.execute(ctx, exec)
    val rightValue = exec.values.intValue
    exec.values.intValue = leftValue * rightValue
  }
}

data class MultiplyLong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.longValue
    right.execute(ctx, exec)
    val rightValue = exec.values.longValue
    exec.values.longValue = leftValue * rightValue
  }
}

data class MultiplyFloat(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.floatValue
    right.execute(ctx, exec)
    val rightValue = exec.values.floatValue
    exec.values.floatValue = leftValue * rightValue
  }
}

data class MultiplyDouble(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.doubleValue
    right.execute(ctx, exec)
    val rightValue = exec.values.doubleValue
    exec.values.doubleValue = leftValue * rightValue
  }
}

data class DivideInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.intValue
    right.execute(ctx, exec)
    val rightValue = exec.values.intValue
    exec.values.intValue = leftValue / rightValue
  }
}

data class DivideLong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.longValue
    right.execute(ctx, exec)
    val rightValue = exec.values.longValue
    exec.values.longValue = leftValue / rightValue
  }
}

data class DivideFloat(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.floatValue
    right.execute(ctx, exec)
    val rightValue = exec.values.floatValue
    exec.values.floatValue = leftValue / rightValue
  }
}

data class DivideDouble(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.doubleValue
    right.execute(ctx, exec)
    val rightValue = exec.values.doubleValue
    exec.values.doubleValue = leftValue / rightValue
  }
}

data class ModInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.intValue
    right.execute(ctx, exec)
    val rightValue = exec.values.intValue
    exec.values.intValue = leftValue % rightValue
  }
}

data class ModLong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.longValue
    right.execute(ctx, exec)
    val rightValue = exec.values.longValue
    exec.values.longValue = leftValue % rightValue
  }
}

data class CmpGTInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.intValue
    right.execute(ctx, exec)
    val rightValue = exec.values.intValue
    exec.values.boolValue = leftValue > rightValue
  }
}

data class CmpGTLong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.longValue
    right.execute(ctx, exec)
    val rightValue = exec.values.longValue
    exec.values.boolValue = leftValue > rightValue
  }
}

data class CmpGTFloat(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.floatValue
    right.execute(ctx, exec)
    val rightValue = exec.values.floatValue
    exec.values.boolValue = leftValue > rightValue
  }
}

data class CmpGTDouble(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.doubleValue
    right.execute(ctx, exec)
    val rightValue = exec.values.doubleValue
    exec.values.boolValue = leftValue > rightValue
  }
}

data class CmpGEInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.intValue
    right.execute(ctx, exec)
    val rightValue = exec.values.intValue
    exec.values.boolValue = leftValue >= rightValue
  }
}

data class CmpGELong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.longValue
    right.execute(ctx, exec)
    val rightValue = exec.values.longValue
    exec.values.boolValue = leftValue >= rightValue
  }
}

data class CmpGEFloat(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.floatValue
    right.execute(ctx, exec)
    val rightValue = exec.values.floatValue
    exec.values.boolValue = leftValue >= rightValue
  }
}

data class CmpGEDouble(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.doubleValue
    right.execute(ctx, exec)
    val rightValue = exec.values.doubleValue
    exec.values.boolValue = leftValue >= rightValue
  }
}

data class CmpLTInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.intValue
    right.execute(ctx, exec)
    val rightValue = exec.values.intValue
    exec.values.boolValue = leftValue < rightValue
  }
}

data class CmpLTLong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.longValue
    right.execute(ctx, exec)
    val rightValue = exec.values.longValue
    exec.values.boolValue = leftValue < rightValue
  }
}

data class CmpLTFloat(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.floatValue
    right.execute(ctx, exec)
    val rightValue = exec.values.floatValue
    exec.values.boolValue = leftValue < rightValue
  }
}

data class CmpLTDouble(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.doubleValue
    right.execute(ctx, exec)
    val rightValue = exec.values.doubleValue
    exec.values.boolValue = leftValue < rightValue
  }
}

data class CmpLEInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.intValue
    right.execute(ctx, exec)
    val rightValue = exec.values.intValue
    exec.values.boolValue = leftValue <= rightValue
  }
}

data class CmpLELong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.longValue
    right.execute(ctx, exec)
    val rightValue = exec.values.longValue
    exec.values.boolValue = leftValue <= rightValue
  }
}

data class CmpLEFloat(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.floatValue
    right.execute(ctx, exec)
    val rightValue = exec.values.floatValue
    exec.values.boolValue = leftValue <= rightValue
  }
}

data class CmpLEDouble(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.doubleValue
    right.execute(ctx, exec)
    val rightValue = exec.values.doubleValue
    exec.values.boolValue = leftValue <= rightValue
  }
}

data class LogicAndBool(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.boolValue
    right.execute(ctx, exec)
    val rightValue = exec.values.boolValue
    exec.values.boolValue = leftValue && rightValue
  }
}

data class LogicOrBool(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.boolValue
    right.execute(ctx, exec)
    val rightValue = exec.values.boolValue
    exec.values.boolValue = leftValue || rightValue
  }
}

data class CmpNEInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.intValue
    right.execute(ctx, exec)
    val rightValue = exec.values.intValue
    exec.values.boolValue = leftValue != rightValue
  }
}

data class CmpNELong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.longValue
    right.execute(ctx, exec)
    val rightValue = exec.values.longValue
    exec.values.boolValue = leftValue != rightValue
  }
}

data class CmpNEFloat(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.floatValue
    right.execute(ctx, exec)
    val rightValue = exec.values.floatValue
    exec.values.boolValue = leftValue != rightValue
  }
}

data class CmpNEDouble(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.doubleValue
    right.execute(ctx, exec)
    val rightValue = exec.values.doubleValue
    exec.values.boolValue = leftValue != rightValue
  }
}

data class CmpNEBool(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.boolValue
    right.execute(ctx, exec)
    val rightValue = exec.values.boolValue
    exec.values.boolValue = leftValue != rightValue
  }
}

data class CmpNERef(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.refValue
    right.execute(ctx, exec)
    val rightValue = exec.values.refValue
    exec.values.boolValue = leftValue != rightValue
  }
}

data class CmpEQInt(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.intValue
    right.execute(ctx, exec)
    val rightValue = exec.values.intValue
    exec.values.boolValue = leftValue == rightValue
  }
}

data class CmpEQLong(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.longValue
    right.execute(ctx, exec)
    val rightValue = exec.values.longValue
    exec.values.boolValue = leftValue == rightValue
  }
}

data class CmpEQFloat(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.floatValue
    right.execute(ctx, exec)
    val rightValue = exec.values.floatValue
    exec.values.boolValue = leftValue == rightValue
  }
}

data class CmpEQDouble(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.doubleValue
    right.execute(ctx, exec)
    val rightValue = exec.values.doubleValue
    exec.values.boolValue = leftValue == rightValue
  }
}

data class CmpEQBool(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.boolValue
    right.execute(ctx, exec)
    val rightValue = exec.values.boolValue
    exec.values.boolValue = leftValue == rightValue
  }
}

data class CmpEQRef(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    left.execute(ctx, exec)
    val leftValue = exec.values.refValue
    right.execute(ctx, exec)
    val rightValue = exec.values.refValue
    exec.values.boolValue = leftValue == rightValue
  }
}

data class NegativeInt(
  private val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    exec.values.intValue = -exec.values.intValue
  }
}

data class NegativeLong(
  private val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    exec.values.longValue = -exec.values.longValue
  }
}

data class NegativeFloat(
  private val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    exec.values.floatValue = -exec.values.floatValue
  }
}

data class NegativeDouble(
  private val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    valueInst.execute(ctx, exec)
    exec.values.doubleValue = -exec.values.doubleValue
  }
}

data class NewArrayInt(
  val lenInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    lenInst.execute(ctx, exec)
    exec.values.refValue = IntArray(exec.values.intValue)
  }
}

data class NewArrayLong(
  val lenInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    lenInst.execute(ctx, exec)
    exec.values.refValue = LongArray(exec.values.intValue)
  }
}

data class NewArrayFloat(
  val lenInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    lenInst.execute(ctx, exec)
    exec.values.refValue = FloatArray(exec.values.intValue)
  }
}

data class NewArrayDouble(
  val lenInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    lenInst.execute(ctx, exec)
    exec.values.refValue = DoubleArray(exec.values.intValue)
  }
}

data class NewArrayBool(
  val lenInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    lenInst.execute(ctx, exec)
    exec.values.refValue = BooleanArray(exec.values.intValue)
  }
}

data class NewArrayRef(
  val lenInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override fun execute0(ctx: ActionContext, exec: Execution) {
    lenInst.execute(ctx, exec)
    exec.values.refValue = Array<Any?>(exec.values.intValue) { null }
  }
}
