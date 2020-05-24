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

import vjson.CharStream;
import vjson.JSON;
import vjson.Parser;
import vjson.ex.JsonParseException;
import vjson.ex.ParserFinishedException;
import vjson.simple.SimpleNull;

public class NullParser implements Parser<JSON.Null> {
    private final ParserOptions opts;
    private int state; // 0->[n]ull,1->n[u]ll,2->nu[l]l,3->nul[l],4->finish,5->already_returned

    public NullParser() {
        this(ParserOptions.DEFAULT);
    }

    public NullParser(ParserOptions opts) throws NullPointerException {
        if (opts == null) {
            throw new NullPointerException();
        }
        this.opts = ParserOptions.ensureNotModifiedByOutside(opts);
    }

    @Override
    public void reset() {
        state = 0;
    }

    private boolean tryParse(CharStream cs, boolean isComplete) {
        char c;
        String err;
        if (state == 0) {
            cs.skipBlank();
            if (cs.hasNext()) {
                opts.getListener().onNullBegin(this);
                c = cs.moveNextAndGet();
                if (c != 'n') {
                    err = "invalid character for `[n]ull`: " + c;
                    throw ParserUtils.err(opts, err);
                }
                ++state;
            }
        }
        if (state == 1) {
            if (cs.hasNext()) {
                c = cs.moveNextAndGet();
                if (c != 'u') {
                    err = "invalid character for `n[u]ll`: " + c;
                    throw ParserUtils.err(opts, err);
                }
                ++state;
            }
        }
        if (state == 2) {
            if (cs.hasNext()) {
                c = cs.moveNextAndGet();
                if (c != 'l') {
                    err = "invalid character for `nu[l]l`: " + c;
                    throw ParserUtils.err(opts, err);
                }
                ++state;
            }
        }
        if (state == 3) {
            if (cs.hasNext()) {
                c = cs.moveNextAndGet();
                if (c != 'l') {
                    err = "invalid character for `nul[l]`: " + c;
                    throw ParserUtils.err(opts, err);
                }
                ++state;
            }
        }
        if (state == 4) {
            ++state;
            return true;
        } else if (state == 5) {
            cs.skipBlank();
            if (cs.hasNext()) {
                throw new ParserFinishedException();
            }
            return false;
        } else if (isComplete) {
            err = "expecting more characters to build `null`";
            throw ParserUtils.err(opts, err);
        } else {
            return false; // expecting more data
        }
    }

    @Override
    public JSON.Null build(CharStream cs, boolean isComplete) throws NullPointerException, JsonParseException, ParserFinishedException {
        if (cs == null) {
            throw new NullPointerException();
        }
        if (tryParse(cs, isComplete)) {
            opts.getListener().onNullEnd(this);
            SimpleNull n = new SimpleNull();
            opts.getListener().onNull(n);

            ParserUtils.checkEnd(cs, opts, "`null`");
            return n;
        } else {
            return null;
        }
    }

    @Override
    public Object buildJavaObject(CharStream cs, boolean isComplete) throws NullPointerException, JsonParseException, ParserFinishedException {
        if (cs == null) {
            throw new NullPointerException();
        }
        if (tryParse(cs, isComplete)) {
            opts.getListener().onNullEnd(this);
            opts.getListener().onNull((Void) null);

            ParserUtils.checkEnd(cs, opts, "`null`");
            return null;
        } else {
            return null;
        }
    }

    @Override
    public boolean completed() {
        return state == 5;
    }
}
