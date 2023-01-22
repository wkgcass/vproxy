package io.vproxy.dep.vjson.pl.type.lang

import io.vproxy.dep.vjson.cs.LineCol
import io.vproxy.dep.vjson.pl.inst.*
import io.vproxy.dep.vjson.pl.type.*

class ArrayWrapperType(
  private val typeContext: TypeContext,
  private val templateType: TypeInstance,
  private val elementType: TypeInstance,
) : TypeInstance {
  companion object {
    private val TO_STRING_STACK_INFO = StackInfo("ArrayWrapper", "toString", LineCol.EMPTY)
  }

  private val arrayType = ArrayTypeInstance(elementType)
  private val constructorDescriptor = object : ExecutableConstructorFunctionDescriptor(
    listOf(ParamInstance("array", arrayType, 0)),
    VoidType,
    FixedMemoryAllocatorProvider(RuntimeMemoryTotal(refTotal = 1))
  ) {
    override fun execute(ctx: ActionContext, exec: Execution) {}
  }

  override fun constructor(ctx: TypeContext): FunctionDescriptor {
    return constructorDescriptor
  }

  override fun field(ctx: TypeContext, name: String, accessFrom: TypeInstance?): Field? {
    return when (name) {
      "array" -> object : ExecutableField(name, arrayType) {
        override fun execute(ctx: ActionContext, exec: Execution) {
          val obj = exec.values.refValue as ActionContext
          exec.values.refValue = obj.getCurrentMem().getRef(0)
        }
      }
      "toString" -> {
        val type = ctx.getFunctionDescriptorAsInstance(listOf(), StringType, DummyMemoryAllocatorProvider)
        val toStringField = elementType.field(ctx, "toString", this)
        object : ExecutableField(name, type) {
          override fun execute(ctx: ActionContext, exec: Execution) {
            val obj = exec.values.refValue as ActionContext
            val array = obj.getCurrentMem().getRef(0)
            exec.values.refValue = object : InstructionWithStackInfo(TO_STRING_STACK_INFO) {
              override fun execute0(ctx: ActionContext, exec: Execution) {
                if (array is IntArray) exec.values.refValue = array.contentToString()
                else if (array is LongArray) exec.values.refValue = array.contentToString()
                else if (array is FloatArray) exec.values.refValue = array.contentToString()
                else if (array is DoubleArray) exec.values.refValue = array.contentToString()
                else if (array is BooleanArray) exec.values.refValue = array.contentToString()
                else {
                  array as Array<*>
                  exec.values.refValue = array.contentToString()
                }
              }
            }
          }
        }
      }
      else -> null
    }
  }

  override fun templateType(): TypeInstance {
    return templateType
  }

  override fun templateTypeParams(): List<TypeInstance> {
    return listOf(elementType)
  }
}
