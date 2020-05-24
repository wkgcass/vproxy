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
import vjson.simple.SimpleArray;

import java.util.LinkedList;
import java.util.List;

public class ArrayParser extends CompositeParser implements Parser<JSON.Array> {
    private final ParserOptions opts;
    private int state; // 0->`[`,1->first-value_or_`]`,2->`,`_or_`]`,3->value,4->finish,5->already_returned
    private LinkedList<JSON.Instance> list;
    private LinkedList<Object> javaList;
    private Parser subParser;

    public ArrayParser() {
        this(ParserOptions.DEFAULT);
    }

    public ArrayParser(ParserOptions opts) throws NullPointerException {
        super(opts);
        if (opts == null) {
            throw new NullPointerException();
        }
        this.opts = ParserOptions.ensureNotModifiedByOutside(opts);
        reset();
    }

    @Override
    public void reset() {
        state = 0;
        if (opts.getMode() == ParserMode.JAVA_OBJECT) {
            javaList = new LinkedList<>();
        } else {
            list = new LinkedList<>();
        }
        subParser = null;
    }

    public LinkedList<JSON.Instance> getList() {
        return list;
    }

    public LinkedList<Object> getJavaList() {
        return javaList;
    }

    private void handleSubParser(boolean tryGetNewSubParser, CharStream cs, boolean isComplete) {
        try {
            if (subParser == null) {
                if (tryGetNewSubParser) {
                    subParser = getSubParser(cs);
                } else {
                    return;
                }
            }
            if (opts.getMode() == ParserMode.JAVA_OBJECT) {
                Object o = subParser.buildJavaObject(cs, isComplete);
                if (subParser.completed()) {
                    state = 2;
                    javaList.add(o);
                    subParser = null; // clear the parser
                    opts.getListener().onArrayValueJavaObject(this, o);
                }
                // otherwise exception would be thrown or cs.hasNext() would return false
            } else {
                JSON.Instance inst = subParser.build(cs, isComplete);
                if (inst != null) {
                    state = 2;
                    list.add(inst);
                    subParser = null; // clear the parser
                    opts.getListener().onArrayValue(this, inst);
                }
                // otherwise exception would be thrown or cs.hasNext() would return false
            }
        } catch (JsonParseException e) {
            throw new JsonParseException("invalid json array: failed when parsing element: (" + e.getMessage() + ")");
        }
    }

    private boolean tryParse(CharStream cs, boolean isComplete) {
        handleSubParser(false, cs, isComplete); // handle sub parser first if it exists

        char c;
        String err;
        if (state == 0) {
            cs.skipBlank();
            if (cs.hasNext()) {
                opts.getListener().onArrayBegin(this);
                c = cs.moveNextAndGet();
                if (c != '[') {
                    err = "invalid character for json array: not starts with `[`: " + c;
                    throw ParserUtils.err(opts, err);
                }
                ++state;
            }
        }
        if (state == 1) {
            cs.skipBlank();
            if (cs.hasNext()) {
                char peek = cs.peekNext();
                if (peek == ']') {
                    cs.moveNextAndGet();
                    state = 4;
                } else {
                    handleSubParser(true, cs, isComplete);
                }
            }
        }
        while (cs.hasNext()) {
            if (state == 2) {
                cs.skipBlank();
                if (cs.hasNext()) {
                    c = cs.moveNextAndGet();
                    if (c == ']') {
                        state = 4;
                    } else if (c == ',') {
                        state = 3;
                    } else {
                        err = "invalid character for json array, expecting `]` or `,`, but got " + c;
                        throw ParserUtils.err(opts, err);
                    }
                }
            }
            if (state == 3) {
                cs.skipBlank();
                if (cs.hasNext()) {
                    handleSubParser(true, cs, isComplete);
                }
            }
            if (state == 4) {
                break;
            }
            if (state == 5) {
                break;
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
            err = "expecting more characters to build array";
            throw ParserUtils.err(opts, err);
        } else {
            return false;
        }
    }

    @Override
    public JSON.Array build(CharStream cs, boolean isComplete) throws NullPointerException, JsonParseException, ParserFinishedException {
        if (cs == null) {
            throw new NullPointerException();
        }
        if (tryParse(cs, isComplete)) {
            opts.getListener().onArrayEnd(this);
            SimpleArray ret = new SimpleArray(list, TrustedFlag.FLAG) {
            };
            opts.getListener().onArray(ret);

            ParserUtils.checkEnd(cs, opts, "array");
            return ret;
        } else {
            return null;
        }
    }

    @Override
    public List<Object> buildJavaObject(CharStream cs, boolean isComplete) throws NullPointerException, JsonParseException, ParserFinishedException {
        if (cs == null) {
            throw new NullPointerException();
        }
        if (tryParse(cs, isComplete)) {
            opts.getListener().onArrayEnd(this);
            opts.getListener().onArray(javaList);

            ParserUtils.checkEnd(cs, opts, "array");
            return javaList;
        } else {
            return null;
        }
    }

    @Override
    public boolean completed() {
        return state == 5;
    }
}
