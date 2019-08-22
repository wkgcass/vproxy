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
import vjson.simple.SimpleBool;

public class BoolParser implements Parser<JSON.Bool> {
    private final ParserOptions opts;
    private int state; // 0->[t|f],1->t[r]ue,2->tr[u]e,3->tru[e],4->f[a]lse,5->fa[l]se,6->fal[s]e,7->fals[e],8->finish,9->already_returned
    private boolean result;

    public BoolParser() {
        this(ParserOptions.DEFAULT);
    }

    public BoolParser(ParserOptions opts) throws NullPointerException {
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
                opts.getListener().onBoolBegin(this);
                c = cs.moveNextAndGet();
                if (c == 't') {
                    result = true;
                    state = 1;
                } else if (c == 'f') {
                    result = false;
                    state = 4;
                } else {
                    err = "invalid character for [t]rue|[f]alse: " + c;
                    throw ParserUtils.err(opts, err);
                }
            }
        }
        if (state == 1) {
            if (cs.hasNext()) {
                c = cs.moveNextAndGet();
                if (c != 'r') {
                    err = "invalid character for t[r]ue: " + c;
                    throw ParserUtils.err(opts, err);
                }
                ++state;
            }
        }
        if (state == 2) {
            if (cs.hasNext()) {
                c = cs.moveNextAndGet();
                if (c != 'u') {
                    err = "invalid character for tr[u]e: " + c;
                    throw ParserUtils.err(opts, err);
                }
                ++state;
            }
        }
        if (state == 3) {
            if (cs.hasNext()) {
                c = cs.moveNextAndGet();
                if (c != 'e') {
                    err = "invalid character for tru[e]: " + c;
                    throw ParserUtils.err(opts, err);
                }
                state = 8;
            }
        }
        if (state == 4) {
            if (cs.hasNext()) {
                c = cs.moveNextAndGet();
                if (c != 'a') {
                    err = "invalid character for f[a]lse: " + c;
                    throw ParserUtils.err(opts, err);
                }
                ++state;
            }
        }
        if (state == 5) {
            if (cs.hasNext()) {
                c = cs.moveNextAndGet();
                if (c != 'l') {
                    err = "invalid character for fa[l]se: " + c;
                    throw ParserUtils.err(opts, err);
                }
                ++state;
            }
        }
        if (state == 6) {
            if (cs.hasNext()) {
                c = cs.moveNextAndGet();
                if (c != 's') {
                    err = "invalid character for fal[s]e: " + c;
                    throw ParserUtils.err(opts, err);
                }
                ++state;
            }
        }
        if (state == 7) {
            if (cs.hasNext()) {
                c = cs.moveNextAndGet();
                if (c != 'e') {
                    err = "invalid character for fals[e]: " + c;
                    throw ParserUtils.err(opts, err);
                }
                ++state;
            }
        }
        if (state == 8) {
            ++state;
            return true;
        } else if (state == 9) {
            cs.skipBlank();
            if (cs.hasNext()) {
                throw new ParserFinishedException();
            }
            return false;
        } else if (isComplete) {
            err = "expecting more characters to build `true` or `false`";
            throw ParserUtils.err(opts, err);
        } else {
            return false;
        }
    }

    @Override
    public JSON.Bool build(CharStream cs, boolean isComplete) throws NullPointerException, JsonParseException, ParserFinishedException {
        if (cs == null) {
            throw new NullPointerException();
        }
        if (tryParse(cs, isComplete)) {
            opts.getListener().onBoolEnd(this);
            SimpleBool ret = new SimpleBool(result);
            opts.getListener().onBool(ret);

            ParserUtils.checkEnd(cs, opts, "`true|false`");
            return ret;
        } else {
            return null;
        }
    }

    @Override
    public Boolean buildJavaObject(CharStream cs, boolean isComplete) throws NullPointerException, JsonParseException, ParserFinishedException {
        if (cs == null) {
            throw new NullPointerException();
        }
        if (tryParse(cs, isComplete)) {
            opts.getListener().onBoolEnd(this);
            Boolean ret = result;
            opts.getListener().onBool(ret);

            ParserUtils.checkEnd(cs, opts, "`true|false`");
            return ret;
        } else {
            return null;
        }
    }

    @Override
    public boolean completed() {
        return state == 9;
    }
}
