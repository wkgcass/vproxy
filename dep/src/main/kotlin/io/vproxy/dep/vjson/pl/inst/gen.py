#!/usr/bin/env python

'''
The MIT License

Copyright 2021 wkgcass (https://github.com/wkgcass)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
'''

PACKAGE = 'vjson.pl.inst'

tInt = {'Type': 'Int', 'type': 'int', 'ArrayType': 'IntArray'}
tLong = {'Type': 'Long', 'type': 'long', 'ArrayType': 'LongArray'}
tFloat = {'Type': 'Float', 'type': 'float', 'ArrayType': 'FloatArray'}
tDouble = {'Type': 'Double', 'type': 'double', 'ArrayType': 'DoubleArray'}
tBool = {'Type': 'Bool', 'KtType': 'Boolean', 'type': 'bool', 'ArrayType': 'BooleanArray'}
tRef = {'Type': 'Ref', 'KtType': 'Any?', 'type': 'ref', 'ArrayType': 'Array<Any?>'}

allTypes = [tInt, tLong, tFloat, tDouble, tBool, tRef]
numTypes = [tInt, tLong, tFloat, tDouble]

data = []

LITERAL = '''
data class Literal{{Type}}(
  val value: {{KtType}},
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    values.{{type}}Value = value
  }
}
'''.strip()
data.append({
    'tmpl': LITERAL,
    'types': allTypes
})

GET = '''
data class Get{{Type}}(
  val depth: Int,
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    values.{{type}}Value = ctx.getMem(depth).get{{Type}}(index)
  }
}
'''.strip()
data.append({
    'tmpl': GET,
    'types': allTypes
})

GET_FIELD = '''
data class GetField{{Type}}(
  val index: Int,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    val mem = values.refValue as ActionContext
    values.{{type}}Value = mem.getCurrentMem().get{{Type}}(index)
  }
}
'''.strip()
data.append({
    'tmpl': GET_FIELD,
    'types': allTypes
})

GET_INDEX = '''
data class GetIndex{{Type}}(
  val array: Instruction,
  val index: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    array.execute(ctx, values)
    val arrayValue = values.refValue as {{ArrayType}}
    index.execute(ctx, values)
    val indexValue = values.intValue
    values.{{type}}Value = arrayValue[indexValue]
  }
}
'''.strip()
data.append({
    'tmpl': GET_INDEX,
    'types': allTypes
})

SET = '''
data class Set{{Type}}(
  val depth: Int,
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    ctx.getMem(depth).set{{Type}}(index, values.{{type}}Value)
  }
}
'''.strip()
data.append({
    'tmpl': SET,
    'types': allTypes
})

SET_INDEX = '''
data class SetIndex{{Type}}(
  val array: Instruction,
  val index: Instruction,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    val value = values.{{type}}Value
    array.execute(ctx, values)
    val arrayValue = values.refValue as {{ArrayType}}
    index.execute(ctx, values)
    val indexValue = values.intValue
    arrayValue[indexValue] = value
  }
}
'''.strip()
data.append({
    'tmpl': SET_INDEX,
    'types': allTypes
})

SET_FIELD = '''
data class SetField{{Type}}(
  val index: Int,
  val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    val mem = values.refValue as ActionContext
    mem.getCurrentMem().set{{Type}}(index, values.{{type}}Value)
  }
}
'''.strip()
data.append({
    'tmpl': SET_FIELD,
    'types': allTypes
})

BIN_OP = '''
data class {{Op}}{{Type}}(
  val left: Instruction,
  val right: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    left.execute(ctx, values)
    val leftValue = values.{{type}}Value
    right.execute(ctx, values)
    val rightValue = values.{{type}}Value
    values.{{opResType}}Value = leftValue {{op}} rightValue
  }
}
'''.strip()
data.append({
    'tmpl': BIN_OP,
    'op': [
        {
            'Op': 'Plus',
            'op': '+',
            'types': numTypes
        },
        {
            'Op': 'Minus',
            'op': '-',
            'types': numTypes
        },
        {
            'Op': 'Multiply',
            'op': '*',
            'types': numTypes
        },
        {
            'Op': 'Divide',
            'op': '/',
            'types': numTypes
        },
        {
            'Op': 'Mod',
            'op': '%',
            'types': [tInt, tLong]
        },
        {
            'Op': 'CmpGT',
            'op': '>',
            'opResType': 'bool',
            'types': numTypes
        },
        {
            'Op': 'CmpGE',
            'op': '>=',
            'opResType': 'bool',
            'types': numTypes
        },
        {
            'Op': 'CmpLT',
            'op': '<',
            'opResType': 'bool',
            'types': numTypes
        },
        {
            'Op': 'CmpLE',
            'op': '<=',
            'opResType': 'bool',
            'types': numTypes
        },
        {
            'Op': 'LogicAnd',
            'op': '&&',
            'types': [tBool]
        },
        {
            'Op': 'LogicOr',
            'op': '||',
            'types': [tBool]
        },
        {
            'Op': 'CmpNE',
            'op': '!=',
            'opResType': 'bool',
            'types': allTypes
        },
        {
            'Op': 'CmpEQ',
            'op': '==',
            'opResType': 'bool',
            'types': allTypes
        }
    ]
})

NEGATIVE = '''
data class Negative{{Type}}(
  private val valueInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    valueInst.execute(ctx, values)
    values.{{type}}Value = -values.{{type}}Value
  }
}
'''.strip()
data.append({
    'tmpl': NEGATIVE,
    'types': numTypes
})

NEW_ARRAY_PRIMITIVES = '''
data class NewArray{{Type}}(
  val lenInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    lenInst.execute(ctx, values)
    values.refValue = {{ArrayType}}(values.intValue)
  }
}
'''.strip()
data.append({
    'tmpl': NEW_ARRAY_PRIMITIVES,
    'types': [tInt, tLong, tFloat, tDouble, tBool]
})

NEW_ARRAY_REF = '''
data class NewArray{{Type}}(
  val lenInst: Instruction,
  override val stackInfo: StackInfo
) : Instruction() {
  override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
    lenInst.execute(ctx, values)
    values.refValue = Array<Any?>(values.intValue) { null }
  }
}
'''.strip()
data.append({
    'tmpl': NEW_ARRAY_REF,
    'types': [tRef]
})

result = []


def generate(tmpl, types):
    for t in types:
        res = tmpl.replace('{{type}}', t['type']) \
            .replace('{{Type}}', t['Type']) \
            .replace('{{ArrayType}}', t['ArrayType']) \
            .replace('{{opResType}}', t['type'])
        if 'KtType' in t:
            res = res.replace('{{KtType}}', t['KtType'])
        else:
            res = res.replace('{{KtType}}', t['Type'])
        result.append(res)


for d in data:
    tmpl = d['tmpl']
    if 'op' in d:
        for op in d['op']:
            Op = op['Op']
            symbol = op['op']
            tmpl1 = tmpl.replace('{{op}}', symbol).replace('{{Op}}', Op)
            if 'opResType' in op:
                tmpl1 = tmpl1.replace('{{opResType}}', op['opResType'])
            types = op['types']
            generate(tmpl1, types)
    else:
        generate(tmpl, d['types'])

output = 'package ' + PACKAGE + '\n\n'
isFirst = True
for x in result:
    if isFirst:
        isFirst = False
    else:
        output += '\n'
    output += x
    output += '\n'

f = open('./InstructionsGen.kt', 'w+')
f.write(output)
f.flush()
f.close()
