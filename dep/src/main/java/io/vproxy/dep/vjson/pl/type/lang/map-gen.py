#!/usr/bin/env python

'''
The MIT License

Copyright 2021 wkgcass (https://github.com/wkgcass)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
'''

result = '''
@file:Suppress("USELESS_ELVIS_RIGHT_IS_NULL")

package vjson.pl.type.lang

import vjson.cs.LineCol
import vjson.pl.inst.*
import vjson.pl.type.*

internal val MAP_PUT_STACK_INFO = StackInfo("Map", "put", LineCol.EMPTY)
internal val MAP_GET_STACK_INFO = StackInfo("Map", "get", LineCol.EMPTY)
internal val MAP_REMOVE_STACK_INFO = StackInfo("Map", "remove", LineCol.EMPTY)
'''.strip() + '\n\n'

templates = []

MAP_PUT_TEMPLATE = """
val type = ctx.getFunctionDescriptorAsInstance(
  listOf(ParamInstance({{KeyType}}, 0), ParamInstance({{ValueType}}, {{valueIndex}})), {{ValueType}},
  FixedMemoryAllocatorProvider(RuntimeMemoryTotal({{memoryTotal}}))
)
object : ExecutableField(name, type, memPos) {
  override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
    val obj = values.refValue as ActionContext
    @Suppress("UNCHECKED_CAST") val map = obj.getCurrentMem().getRef(0) as MutableMap<{{KtKeyType}}, {{KtValueType}}>
    values.refValue = object : InstructionWithStackInfo(MAP_PUT_STACK_INFO) {
      override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
        values.{{valueType}}Value = map.put(ctx.getCurrentMem().get{{Key}}(0), ctx.getCurrentMem().get{{Value}}({{valueIndex}})) ?: {{value}}
      }
    }
  }
}
""".strip()
templates.append({
    'name': 'put',
    'tmpl': MAP_PUT_TEMPLATE
})

MAP_GET_TEMPLATE = """
val type = ctx.getFunctionDescriptorAsInstance(
  listOf(ParamInstance({{KeyType}}, 0)), {{ValueType}},
  FixedMemoryAllocatorProvider(RuntimeMemoryTotal({{keyType}}Total = 1))
)
object : ExecutableField(name, type, memPos) {
  override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
    val obj = values.refValue as ActionContext
    @Suppress("UNCHECKED_CAST") val map = obj.getCurrentMem().getRef(0) as MutableMap<{{KtKeyType}}, {{KtValueType}}>
    values.refValue = object : InstructionWithStackInfo(MAP_GET_STACK_INFO) {
      override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
        values.{{valueType}}Value = map[ctx.getCurrentMem().get{{Key}}(0)] ?: {{value}}
      }
    }
  }
}
""".strip()
templates.append({
    'name': 'get',
    'tmpl': MAP_GET_TEMPLATE
})

MAP_REMOVE_TEMPLATE = """
val type = ctx.getFunctionDescriptorAsInstance(
  listOf(ParamInstance({{KeyType}}, 0)), {{ValueType}},
  FixedMemoryAllocatorProvider(RuntimeMemoryTotal({{keyType}}Total = 1))
)
object : ExecutableField(name, type, memPos) {
  override suspend fun execute(ctx: ActionContext, values: ValueHolder) {
    val obj = values.refValue as ActionContext
    @Suppress("UNCHECKED_CAST") val map = obj.getCurrentMem().getRef(0) as MutableMap<{{KtKeyType}}, {{KtValueType}}>
    values.refValue = object : InstructionWithStackInfo(MAP_REMOVE_STACK_INFO) {
      override suspend fun execute0(ctx: ActionContext, values: ValueHolder) {
        values.{{valueType}}Value = map.remove(ctx.getCurrentMem().get{{Key}}(0)) ?: {{value}}
      }
    }
  }
}
""".strip()
templates.append({
    'name': 'remove',
    'tmpl': MAP_REMOVE_TEMPLATE
})

tInt = {
    'Type': 'Int',
    'type': 'int',
    'KtType': 'Int',
    'value': '0'
}

tLong = {
    'Type': 'Long',
    'type': 'long',
    'KtType': 'Long',
    'value': '0L'
}

tFloat = {
    'Type': 'Float',
    'type': 'float',
    'KtType': 'Float',
    'value': '0f'
}

tDouble = {
    'Type': 'Double',
    'type': 'double',
    'KtType': 'Double',
    'value': '0.0'
}

tBool = {
    'Type': 'Bool',
    'type': 'bool',
    'KtType': 'Boolean',
    'value': 'false'
}

tRef = {
    'Type': 'Ref',
    'type': 'ref',
    'KtType': 'Any?',
    'value': 'null'
}

allTypes = [tInt, tLong, tFloat, tDouble, tBool, tRef]

result += '// ----- BEGIN -----\n'
result += 'internal fun generatedForMap0(key: TypeInstance, value: TypeInstance, ctx: TypeContext, name: String): Field {\n' # 1
result += '  val memPos = MemPos(0, 0)\n'
result += '  return when (name) {\n' # 2
for template in templates:
    result += '    "' + template['name'] + '" -> {\n' # 3
    result += ('      when (key) {\n') # 4
    for keyType in allTypes:
        TypeForKey = keyType['Type']
        if TypeForKey == 'Ref':
            result += '        else -> when (value) {\n' # 5
        else:
            result += '        ' + TypeForKey + 'Type -> when (value) {\n' # 5
        for valueType in allTypes:
            KeyType = keyType['Type']
            if KeyType == 'Ref':
                KeyType = 'key'
            else:
                KeyType += 'Type'
            ValueType = valueType['Type']
            if ValueType == 'Ref':
                ValueType = 'value'
            else:
                ValueType += 'Type'
            KtKeyType = keyType['KtType']
            KtValueType = valueType['KtType']
            Key = keyType['Type']
            Value = valueType['Type']
            if keyType == valueType:
                memoryTotal = keyType['type'] + 'Total = 2'
                valueIndex = '1'
            else:
                memoryTotal = keyType['type'] + 'Total = 1, ' + valueType['type'] + 'Total = 1'
                valueIndex = '0'
            value = valueType['value']

            if ValueType == 'value':
                result += '          else -> {\n' # 6
            else:
                result += '          ' + ValueType + ' -> {\n' # 6

            lines = template['tmpl'].split('\n')
            join = ''
            for line in lines:
                join += '            ' # 7
                join += line + '\n'
            x = join \
                .replace('{{KeyType}}', KeyType) \
                .replace('{{ValueType}}', ValueType) \
                .replace('{{memoryTotal}}', memoryTotal) \
                .replace('{{valueIndex}}', valueIndex) \
                .replace('{{KtKeyType}}', KtKeyType) \
                .replace('{{KtValueType}}', KtValueType) \
                .replace('{{keyType}}', keyType['type']) \
                .replace('{{valueType}}', valueType['type']) \
                .replace('{{Key}}', Key) \
                .replace('{{Value}}', Value) \
                .replace('{{value}}', value)
            result += x
            result += '          }\n' # -6
        result += '        }\n' # -5
    result += '      }\n' # -4
    result += '    }\n' # -3
result += '    else -> throw IllegalStateException()\n' # 3
result += '  }\n' # -2
result += '}\n' # -1
result += '// ----- END -----\n'

f = open('./MapTypeGen.kt', 'w+')
f.write(result)
f.flush()
f.close()
