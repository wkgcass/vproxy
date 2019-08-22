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
import vjson.ex.JsonParseException;
import vjson.ex.ParserFinishedException;

public interface Parser<T extends JSON.Instance> {
    T build(CharStream cs, boolean isComplete) throws NullPointerException, JsonParseException, ParserFinishedException;

    Object buildJavaObject(CharStream cs, boolean isComplete) throws NullPointerException, JsonParseException, ParserFinishedException;

    boolean completed();

    void reset();

    default T feed(CharStream cs) throws NullPointerException, JsonParseException, ParserFinishedException {
        return build(cs, false);
    }

    default T feed(String cs) throws NullPointerException, JsonParseException, ParserFinishedException {
        return feed(CharStream.from(cs));
    }

    default T last(CharStream cs) throws NullPointerException, JsonParseException, ParserFinishedException {
        return build(cs, true);
    }

    default T last(String cs) throws NullPointerException, JsonParseException, ParserFinishedException {
        return last(CharStream.from(cs));
    }

    default T end() throws JsonParseException, ParserFinishedException {
        return last(CharArrayCharStream.EMPTY);
    }
}
