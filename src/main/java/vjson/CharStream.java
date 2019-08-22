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

package vjson;

import vjson.cs.CharArrayCharStream;
import vjson.parser.ParserUtils;

import java.util.Iterator;

public interface CharStream extends Iterator<Character>, Iterable<Character> {
    static CharStream from(char[] array) {
        return new CharArrayCharStream(array);
    }

    static CharStream from(String string) {
        return from(string.toCharArray());
    }

    boolean hasNext(int i);

    char moveNextAndGet();

    char peekNext(int i);

    default char peekNext() {
        return peekNext(1);
    }

    @Override
    default boolean hasNext() {
        return hasNext(1);
    }

    @Deprecated
    @Override
    default Character next() {
        return moveNextAndGet();
    }

    @Deprecated
    @Override
    default void remove() {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    @Override
    default Iterator<Character> iterator() {
        return this;
    }

    default void skipBlank() {
        while (hasNext()) {
            char c = peekNext();
            if (ParserUtils.isWhiteSpace(c)) {
                moveNextAndGet();
            } else {
                break;
            }
        }
    }
}
