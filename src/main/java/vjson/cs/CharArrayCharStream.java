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

package vjson.cs;

import vjson.CharStream;

public class CharArrayCharStream implements CharStream {
    public static final CharArrayCharStream EMPTY = new CharArrayCharStream(new char[0]);

    private final char[] cs;
    private int idx = -1;

    public CharArrayCharStream(char[] cs) throws NullPointerException {
        if (cs == null) {
            throw new NullPointerException();
        }
        this.cs = cs;
    }

    @Override
    public boolean hasNext(int i) {
        return idx + i < cs.length;
    }

    @Override
    public char moveNextAndGet() {
        return cs[++idx];
    }

    @Override
    public char peekNext(int i) {
        return cs[idx + i];
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CharStream(");
        if (idx == -1) {
            sb.append("[]");
        }
        for (int i = 0; i < cs.length; ++i) {
            if (i == idx) {
                sb.append('[');
            }
            sb.append(cs[i]);
            if (i == idx) {
                sb.append(']');
            }
        }
        sb.append(")");
        return sb.toString();
    }
}
