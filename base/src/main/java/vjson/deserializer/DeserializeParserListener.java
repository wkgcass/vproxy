/*
 * The MIT License
 *
 * Copyright 2019 wkgcass (https://github.com/wkgcass)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package vjson.deserializer;

import vjson.JSON;
import vjson.deserializer.rule.*;
import vjson.ex.JsonParseException;
import vjson.listener.AbstractParserListener;
import vjson.parser.*;

import java.util.LinkedList;
import java.util.function.BiConsumer;

@SuppressWarnings("unchecked")
public class DeserializeParserListener<T> extends AbstractParserListener {

    private final LinkedList<ParseContext> parseStack = new LinkedList<>();
    private final LinkedList<Rule> nextRuleStack = new LinkedList<>();
    private boolean begin = false;
    private Object lastObject = null;

    public DeserializeParserListener(Rule<T> rule) {
        if (!(rule instanceof ObjectRule) && !(rule instanceof ArrayRule)) {
            throw new IllegalArgumentException("can only accept ObjectRule or ArrayRule");
        }
        nextRuleStack.push(rule);
    }

    @Override
    public void onObjectBegin(ObjectParser object) {
        Rule rule = nextRuleStack.peek();
        if (!(rule instanceof ObjectRule)) {
            throw new JsonParseException("expect: array, actual: object");
        }
        parseStack.push(new ParseContext(rule, true, ((ObjectRule) rule).construct.get()));
        begin = true;
    }

    @Override
    public void onObjectKey(ObjectParser object, String key) {
        ParseContext ctx = parseStack.peek();
        ObjectRule rule = (ObjectRule) ctx.rule;
        ObjectField field = rule.getRule(key);
        if (field == null) {
            return; // ignore if the field is not registered
        }
        nextRuleStack.push(field.rule);
    }

    private void set(Rule rule, Object holder, BiConsumer set, Object value) {
        if (value == null) {
            if (rule instanceof NullableStringRule || rule instanceof ArrayRule || rule instanceof ObjectRule) {
                set.accept(holder, null);
                return;
            }
        } else if (value instanceof Boolean) {
            if (rule instanceof BoolRule) {
                set.accept(holder, value);
                return;
            }
        } else if (rule instanceof DoubleRule && value instanceof Number) {
            set.accept(holder, ((Number) value).doubleValue());
            return;
        } else if (rule instanceof LongRule && value instanceof Number) {
            if (value instanceof Long || value instanceof Integer) {
                set.accept(holder, ((Number) value).longValue());
                return;
            }
        } else if (rule instanceof IntRule && value instanceof Number) {
            if (value instanceof Integer) {
                set.accept(holder, value);
                return;
            }
        } else if (value instanceof String) {
            if (rule instanceof StringRule || rule instanceof NullableStringRule) {
                set.accept(holder, value);
                return;
            }
        } else {
            // assert rule instanceof ArrayRule || rule instanceof ObjectRule
            set.accept(holder, value);
            return;
        }
        throw new JsonParseException("invalid type: expecting: " + rule + ", value=" + value + "(" + (value == null ? "nil" : value.getClass()) + ")");
    }

    @Override
    public void onObjectValue(ObjectParser object, String key, JSON.Instance value) {
        ParseContext ctx = parseStack.peek();
        ObjectRule rule = (ObjectRule) ctx.rule;
        ObjectField field = rule.getRule(key);
        if (field == null) {
            return; // ignore if the field is not registered
        }
        set(field.rule, ctx.object, field.set, lastObject);
        nextRuleStack.pop();
    }

    @Override
    public void onObjectValueJavaObject(ObjectParser object, String key, Object value) {
        onObjectValue(object, key, null);
    }

    @Override
    public void onObjectEnd(ObjectParser object) {
        ParseContext ctx = parseStack.pop();
        lastObject = ctx.object;
    }

    @Override
    public void onArrayBegin(ArrayParser array) {
        Rule rule = nextRuleStack.peek();
        if (!(rule instanceof ArrayRule)) {
            throw new JsonParseException("expect: object, actual: array");
        }
        parseStack.push(new ParseContext(rule, false, ((ArrayRule) rule).construct.get()));
        nextRuleStack.push(((ArrayRule) rule).elementRule);
        begin = true;
    }

    @Override
    public void onArrayValue(ArrayParser array, JSON.Instance value) {
        ParseContext ctx = parseStack.peek();
        ArrayRule rule = (ArrayRule) ctx.rule;
        set(rule.elementRule, ctx.object, rule.add, lastObject);
    }

    @Override
    public void onArrayValueJavaObject(ArrayParser array, Object value) {
        onArrayValue(array, null);
    }

    @Override
    public void onArrayEnd(ArrayParser array) {
        ParseContext ctx = parseStack.pop();
        nextRuleStack.pop();
        lastObject = ctx.object;
    }

    @Override
    public void onBool(JSON.Bool bool) {
        lastObject = bool.toJavaObject();
    }

    @Override
    public void onBool(Boolean bool) {
        lastObject = bool;
    }

    @Override
    public void onNull(JSON.Null n) {
        lastObject = null;
    }

    @Override
    public void onNull(Void n) {
        lastObject = null;
    }

    @Override
    public void onNumber(JSON.Number number) {
        lastObject = number.toJavaObject();
    }

    @Override
    public void onNumber(Number number) {
        lastObject = number;
    }

    @Override
    public void onString(JSON.String string) {
        lastObject = string.toJavaObject();
    }

    @Override
    public void onString(String string) {
        lastObject = string;
    }

    public boolean completed() {
        return begin && parseStack.isEmpty();
    }

    public T get() throws IllegalStateException {
        if (!completed()) {
            throw new IllegalStateException("not completed yet");
        }
        //noinspection unchecked
        return (T) lastObject;
    }
}
