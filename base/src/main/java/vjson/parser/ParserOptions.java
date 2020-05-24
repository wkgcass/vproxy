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

package vjson.parser;

import vjson.ParserListener;
import vjson.listener.EmptyParserListener;

public class ParserOptions {
    static final ParserOptions DEFAULT = new ParserOptions();
    static final ParserOptions DEFAULT_NO_END = new ParserOptions().setEnd(false);
    static final ParserOptions DEFAULT_JAVA_OBJECT = new ParserOptions().setMode(ParserMode.JAVA_OBJECT);
    static final ParserOptions DEFAULT_JAVA_OBJECT_NO_END = new ParserOptions().setMode(ParserMode.JAVA_OBJECT).setEnd(false);

    static boolean isDefaultOptions(ParserOptions opts) {
        return opts == DEFAULT || opts == DEFAULT_NO_END || opts == DEFAULT_JAVA_OBJECT || opts == DEFAULT_JAVA_OBJECT_NO_END;
    }

    static ParserOptions ensureNotModifiedByOutside(ParserOptions opts) {
        if (isDefaultOptions(opts))
            return opts;
        return new ParserOptions(opts);
    }

    private int bufLen;
    private boolean end;
    private ParserMode mode;
    private ParserListener listener;

    // features
    private boolean stringSingleQuotes;
    private boolean keyNoQuotes;
    private boolean strict;

    public ParserOptions() {
        this.bufLen = 256;
        this.end = true;
        this.mode = ParserMode.DEFAULT;
        this.listener = EmptyParserListener.INSTANCE;

        // features
        stringSingleQuotes = false;
        keyNoQuotes = false;
    }

    public ParserOptions(ParserOptions opts) {
        this.bufLen = opts.bufLen;
        this.end = opts.end;
        this.mode = opts.mode;
        this.listener = opts.listener;

        // features
        this.stringSingleQuotes = opts.stringSingleQuotes;
        this.keyNoQuotes = opts.keyNoQuotes;
    }

    public int getBufLen() {
        return bufLen;
    }

    public ParserOptions setBufLen(int bufLen) {
        this.bufLen = bufLen;
        return this;
    }

    public boolean isEnd() {
        return end;
    }

    public ParserOptions setEnd(boolean end) {
        this.end = end;
        return this;
    }

    public ParserMode getMode() {
        return mode;
    }

    public ParserOptions setMode(ParserMode mode) {
        this.mode = mode;
        return this;
    }

    public ParserListener getListener() {
        return listener;
    }

    public ParserOptions setListener(ParserListener listener) {
        if (listener == null) {
            listener = EmptyParserListener.INSTANCE;
        }
        this.listener = listener;
        return this;
    }

    // ============
    // features
    // ============

    public boolean isStringSingleQuotes() {
        return stringSingleQuotes;
    }

    public ParserOptions setStringSingleQuotes(boolean stringSingleQuotes) {
        this.stringSingleQuotes = stringSingleQuotes;
        return this;
    }

    public boolean isKeyNoQuotes() {
        return keyNoQuotes;
    }

    public ParserOptions setKeyNoQuotes(boolean keyNoQuotes) {
        this.keyNoQuotes = keyNoQuotes;
        return this;
    }
}
