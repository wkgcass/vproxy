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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class SimpleArray extends AbstractSimpleInstance<List<Object>> implements JSON.Array {
    private final List<JSON.Instance> list;

    public SimpleArray(JSON.Instance... list) throws NullPointerException, IllegalArgumentException {
        this(Arrays.asList(list));
    }

    public SimpleArray(List<JSON.Instance> list) throws NullPointerException, IllegalArgumentException {
        if (list == null) {
            throw new NullPointerException();
        }
        for (JSON.Instance inst : list) {
            if (inst == null) {
                throw new IllegalArgumentException("element should not be null");
            }
        }
        this.list = new ArrayList<>(list);
    }

    protected SimpleArray(List<JSON.Instance> list, vjson.parser.TrustedFlag flag) {
        if (flag == null) {
            throw new UnsupportedOperationException();
        }
        this.list = list;
    }

    protected SimpleArray(List<JSON.Instance> list, vjson.util.TrustedFlag flag) {
        if (flag == null) {
            throw new UnsupportedOperationException();
        }
        this.list = list;
    }

    @Override
    public List<Object> toJavaObject() {
        return new ArrayList<>(super.toJavaObject());
    }

    @Override
    protected List<Object> _toJavaObject() {
        List<Object> javaObject = new ArrayList<>();
        for (JSON.Instance inst : list) {
            javaObject.add(inst.toJavaObject());
        }
        return javaObject;
    }

    @Override
    public void stringify(StringBuilder sb, Stringifier sfr) {
        sfr.beforeArrayBegin(sb, this);
        sb.append("[");
        sfr.afterArrayBegin(sb, this);
        if (list.size() > 0) {
            JSON.Instance inst = list.get(0);
            sfr.beforeArrayValue(sb, this, inst);
            inst.stringify(sb, sfr);
            sfr.afterArrayValue(sb, this, inst);
        }
        for (int i = 1; i < list.size(); ++i) {
            sfr.beforeArrayComma(sb, this);
            sb.append(",");
            sfr.afterArrayComma(sb, this);
            JSON.Instance inst = list.get(i);
            sfr.beforeArrayValue(sb, this, inst);
            inst.stringify(sb, sfr);
            sfr.afterArrayValue(sb, this, inst);
        }
        sfr.beforeArrayEnd(sb, this);
        sb.append("]");
        sfr.afterArrayEnd(sb, this);
    }

    @Override
    protected String _toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Array[");
        if (list.size() > 0) {
            sb.append(list.get(0));
        }
        for (int i = 1; i < list.size(); ++i) {
            sb.append(", ").append(list.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public int length() {
        return list.size();
    }

    @Override
    public JSON.Instance get(int idx) throws IndexOutOfBoundsException {
        return list.get(idx);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JSON.Array)) return false;
        JSON.Array that = (JSON.Array) o;
        if (that.length() != length()) return false;
        int len = length();
        for (int i = 0; i < len; ++i) {
            if (!that.get(i).equals(get(i))) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(list);
    }
}
