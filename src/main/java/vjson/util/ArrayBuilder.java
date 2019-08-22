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

public class ArrayBuilder {
    private List<JSON.Instance> list = new LinkedList<>();

    public ArrayBuilder addInst(JSON.Instance inst) {
        list.add(inst);
        return this;
    }

    public ArrayBuilder add(boolean bool) {
        return addInst(new SimpleBool(bool));
    }

    public ArrayBuilder add(int integer) {
        return addInst(new SimpleInteger(integer));
    }

    public ArrayBuilder add(long longV) {
        return addInst(new SimpleLong(longV));
    }

    public ArrayBuilder add(double doubleV) {
        return addInst(new SimpleDouble(doubleV));
    }

    public ArrayBuilder add(double num, int exponent) {
        return addInst(new SimpleExp(num, exponent));
    }

    public ArrayBuilder add(String string) {
        if (string == null) {
            return addInst(new SimpleNull());
        } else {
            return addInst(new SimpleString(string));
        }
    }

    public ArrayBuilder addObject(Consumer<ObjectBuilder> func) {
        ObjectBuilder builder = new ObjectBuilder();
        func.accept(builder);
        return addInst(builder.build());
    }

    public ArrayBuilder addArray(Consumer<ArrayBuilder> func) {
        ArrayBuilder builder = new ArrayBuilder();
        func.accept(builder);
        return addInst(builder.build());
    }

    public JSON.Array build() {
        return new SimpleArray(list, TrustedFlag.FLAG) {
        };
    }
}
