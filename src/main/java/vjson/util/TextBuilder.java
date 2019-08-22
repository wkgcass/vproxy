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

public class TextBuilder {
    private char[] buf; // use buf if not oob
    private int len = 0;

    public TextBuilder(int bufLen) {
        buf = new char[bufLen];
    }

    public int getBufLen() {
        return len;
    }

    public int getBufCap() {
        return buf.length;
    }

    public void clear() {
        len = 0;
    }

    public TextBuilder append(char c) {
        // choose to write to buffer or string builder
        buf[len++] = c;
        if (len == buf.length) {
            char[] newbuf = new char[len * 4];
            System.arraycopy(buf, 0, newbuf, 0, len);
            buf = newbuf;
        }
        return this;
    }

    public TextBuilder removeLast() {
        if (len != 0) {
            --len;
        }
        return this;
    }

    @Override
    public String toString() {
        if (len == 0) {
            return "";
        } else {
            return new String(buf, 0, len);
        }
    }
}
