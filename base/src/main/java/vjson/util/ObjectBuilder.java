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

package vjson.util;

import vjson.JSON;
import vjson.simple.*;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

public class ObjectBuilder {
    private final List<SimpleObjectEntry<JSON.Instance>> map = new LinkedList<>();

    public ObjectBuilder putInst(String key, JSON.Instance inst) {
        if (key.equals("@type")) { // always add @type to the most front
            map.add(0, new SimpleObjectEntry<>(key, inst));
        }
        map.add(new SimpleObjectEntry<>(key, inst));
        return this;
    }

    public ObjectBuilder put(String key, boolean bool) {
        return putInst(key, new SimpleBool(bool));
    }

    public ObjectBuilder put(String key, int integer) {
        return putInst(key, new SimpleInteger(integer));
    }

    public ObjectBuilder put(String key, long longV) {
        return putInst(key, new SimpleLong(longV));
    }

    public ObjectBuilder put(String key, double doubleV) {
        return putInst(key, new SimpleDouble(doubleV));
    }

    public ObjectBuilder put(String key, double num, int exponent) {
        return putInst(key, new SimpleExp(num, exponent));
    }

    public ObjectBuilder put(String key, String string) {
        if (string == null) {
            return putInst(key, new SimpleNull());
        } else {
            return putInst(key, new SimpleString(string));
        }
    }

    public ObjectBuilder putObject(String key, Consumer<ObjectBuilder> func) {
        ObjectBuilder builder = new ObjectBuilder();
        func.accept(builder);
        return putInst(key, builder.build());
    }

    public ObjectBuilder putArray(String key, Consumer<ArrayBuilder> func) {
        ArrayBuilder builder = new ArrayBuilder();
        func.accept(builder);
        return putInst(key, builder.build());
    }

    public ObjectBuilder type(String type) {
        return putInst("@type", new SimpleString(type));
    }

    public ObjectBuilder type(Class<?> aClass) {
        return type(aClass.getName());
    }

    public JSON.Object build() {
        return new SimpleObject(map, TrustedFlag.FLAG) {
        };
    }
}
