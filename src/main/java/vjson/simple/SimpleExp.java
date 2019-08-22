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

public class SimpleExp extends AbstractSimpleInstance<Double> implements JSON.Exp {
    private final double base;
    private final int exponent;
    private final double value;

    public SimpleExp(double base, int exponent) {
        this.base = base;
        this.exponent = exponent;
        this.value = base * Math.pow(10.0, exponent);
    }

    @Override
    public Double _toJavaObject() {
        return value;
    }

    @Override
    public void stringify(StringBuilder sb, Stringifier sfr) {
        sb.append(base).append("e").append(exponent);
    }

    @Override
    protected String _toString() {
        return "Exp(" + base + "e" + exponent + "=" + value + ")";
    }

    @Override
    public double doubleValue() {
        return value;
    }

    @Override
    public double base() {
        return base;
    }

    @Override
    public int exponent() {
        return exponent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JSON.Double)) return false;
        JSON.Double that = (JSON.Double) o;
        return Double.compare(that.doubleValue(), doubleValue()) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(base, exponent);
    }
}
