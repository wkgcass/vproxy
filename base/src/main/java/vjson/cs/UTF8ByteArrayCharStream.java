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

/*
 *    Char. number range  |        UTF-8 octet sequence
 *       (hexadecimal)    |              (binary)
 *    --------------------+---------------------------------------------
 *    0000 0000-0000 007F | 0xxxxxxx
 *    0000 0080-0000 07FF | 110xxxxx 10xxxxxx
 *    0000 0800-0000 FFFF | 1110xxxx 10xxxxxx 10xxxxxx
 *    0001 0000-0010 FFFF | 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
 */
public class UTF8ByteArrayCharStream implements CharStream {
    private final byte[] array;
    private int idx = -1;
    private boolean idxIsFirstJavaCharFor4BytesUTF8Char = false;

    public UTF8ByteArrayCharStream(byte[] array) {
        this.array = array;
    }

    private int nextLen(int off) {
        int idx = this.idx + off;
        if (array.length == idx) {
            return -1;
        }
        byte first = array[idx];
        if ((byte) (first & 0b1_000_0000) == (byte) (0b0000_0000)) {
            return 1;
        }
        int total;
        if ((byte) (first & 0b111_0_0000) == (byte) 0b110_0_0000) {
            total = 2;
        } else if ((byte) (first & 0b1111_0000) == (byte) 0b1110_0000) {
            total = 3;
        } else if ((byte) (first & 0b1111_1_000) == (byte) 0b1111_0_000) {
            total = 4;
        } else {
            throw new IllegalArgumentException("encoding not UTF-8, first byte not 0xxxxxxx, 110xxxxx, 1110xxxx, 11110xxx: " + first);
        }
        return total;
    }

    @Override
    public boolean hasNext(int i) {
        int total = 0;
        if (idxIsFirstJavaCharFor4BytesUTF8Char) {
            if (i == 1) {
                return true;
            } else {
                i -= 1;
                total += 4;
            }
        }
        boolean met4bytes = false;
        for (int n = 0; n < i; ++n) {
            int t = nextLen(1 + total);
            if (t < 0) {
                return false;
            }
            if (t == 4) {
                if (!met4bytes) {
                    met4bytes = true;
                    continue;
                } else {
                    met4bytes = false;
                    // fall through
                }
            }
            total += t;
        }
        return true;
    }

    @Override
    public char moveNextAndGet() {
        int total = nextLen(1);
        char c = peekNext(1);
        if (total == 4) {
            if (idxIsFirstJavaCharFor4BytesUTF8Char) {
                idx += 4;
            }
            idxIsFirstJavaCharFor4BytesUTF8Char = !idxIsFirstJavaCharFor4BytesUTF8Char;
        } else {
            idx += total;
        }
        return c;
    }

    @Override
    public char peekNext(int i) {
        int offset = 1;
        if (idxIsFirstJavaCharFor4BytesUTF8Char) {
            if (i == 1) {
                return fourBytesChar(1)[1];
            } else {
                i -= 1;
                offset += 4;
            }
        }
        boolean met4bytes = false;
        for (int n = 0; n < i - 1; ++n) {
            int len = nextLen(offset);
            if (len == 4) {
                if (met4bytes) {
                    met4bytes = false;
                    // fall through
                } else {
                    met4bytes = true;
                    continue; // do not increase offset
                }
            }
            offset += len;
        }

        int len = nextLen(offset);
        if (len == 4) {
            if (met4bytes) {
                return fourBytesChar(offset)[1];
            } else {
                return fourBytesChar(offset)[0];
            }
        }

        byte first = array[idx + offset];
        if (len == 1) {
            return (char) array[idx + offset];
        } else {
            byte second = array[idx + offset + 1];
            if (len == 2) {
                return (char) (((first & 0b0001_1111) << 6) | (second & 0b0011_1111));
            } else {
                byte third = array[idx + offset + 2];
                // assert len == 3;
                return (char) (((first & 0b0000_1111) << 12) | ((second & 0b0011_1111) << 6) | (third & 0b0011_1111));
            }
        }
    }

    private char[] fourBytesChar(int offset) {
        byte first = array[idx + offset];
        byte second = array[idx + offset + 1];
        byte third = array[idx + offset + 2];
        byte fourth = array[idx + offset + 3];
        int n = (((first & 0b0000_0111) << 18) | ((second & 0b0011_1111) << 12) | ((third & 0b0011_1111) << 6) | (fourth & 0b0011_1111));
        n -= 0x10000;
        char a = (char) ((n >> 10) | 0b1101_10_00_0000_0000);
        char b = (char) ((n & 0b0000_00_11_1111_1111) | 0b1101_11_00_0000_0000);
        return new char[]{a, b};
    }
}
