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

package vjson.simple;

import vjson.JSON;
import vjson.stringifier.EmptyStringifier;
import vjson.stringifier.PrettyStringifier;

public abstract class AbstractSimpleInstance<T> implements JSON.Instance<T> {
    private T javaObject;
    private String stringified;
    private String pretty;
    private String javaToString;

    @Override
    public T toJavaObject() {
        if (javaObject == null) {
            javaObject = _toJavaObject();
        }
        return javaObject;
    }

    protected abstract T _toJavaObject();

    @Override
    public String stringify() {
        if (stringified == null) {
            StringBuilder sb = new StringBuilder();
            stringify(sb, EmptyStringifier.INSTANCE);
            stringified = sb.toString();
        }
        return stringified;
    }

    @Override
    public String pretty() {
        if (pretty == null) {
            StringBuilder sb = new StringBuilder();
            stringify(sb, new PrettyStringifier());
            pretty = sb.toString();
        }
        return pretty;
    }

    @Override
    public final String toString() {
        if (javaToString == null) {
            javaToString = _toString();
        }
        return javaToString;
    }

    protected abstract String _toString();
}
