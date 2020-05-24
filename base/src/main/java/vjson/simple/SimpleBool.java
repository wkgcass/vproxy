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
import vjson.Stringifier;

import java.util.Objects;

public class SimpleBool extends AbstractSimpleInstance<Boolean> implements JSON.Bool {
    private final boolean value;

    public SimpleBool(boolean value) {
        this.value = value;
    }

    @Override
    public boolean booleanValue() {
        return value;
    }

    @Override
    protected Boolean _toJavaObject() {
        return value;
    }

    @Override
    public void stringify(StringBuilder sb, Stringifier sfr) {
        sb.append(value);
    }

    @Override
    protected String _toString() {
        return "Bool(" + value + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JSON.Bool)) return false;
        JSON.Bool that = (JSON.Bool) o;
        return value == that.booleanValue();
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
