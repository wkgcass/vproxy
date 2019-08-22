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
import vjson.simple.SimpleString;
import vjson.util.TextBuilder;

public class StringParser implements Parser<JSON.String> {
    private final ParserOptions opts;
    private int state;
    // 0->start`"`,
    // 1->normal or escape_begin or end`"`,
    // 2->escape_val or escape_u,
    // 3->escape_u1,
    // 4->escape_u2,
    // 5->escape_u3,
    // 6->escape_u4,
    // 7->finish
    // 8->already_returned
    private final TextBuilder builder;
    private char beginning;
    private int u1 = -1;
    private int u2 = -1;
    private int u3 = -1;
    // u4 can be local variable

    public StringParser() {
        this(ParserOptions.DEFAULT);
    }

    public StringParser(ParserOptions opts) throws NullPointerException {
        if (opts == null) {
            throw new NullPointerException();
        }
        this.opts = ParserOptions.ensureNotModifiedByOutside(opts);
        this.builder = new TextBuilder(opts.getBufLen());
    }

    @Override
    public void reset() {
        state = 0;
        builder.clear();
        // start/u1/2/3 can keep their values
    }

    public TextBuilder getBuilder() {
        return builder;
    }

    private static int parseHex(char c) {
        if ('0' <= c && c <= '9') {
            return c - 48;
        } else if ('A' <= c && c <= 'F') {
            return c - (65 - 10);
        } else if ('a' <= c && c <= 'f') {
            return c - (97 - 10);
        } else {
            return -1;
        }
    }

    private void append(char c) {
        builder.append(c);
        opts.getListener().onStringChar(this, c);
    }

    private boolean tryParse(CharStream cs, boolean isComplete) {
        char c;
        String err;
        if (state == 0) {
            cs.skipBlank();
            if (cs.hasNext()) {
                opts.getListener().onStringBegin(this);
                c = cs.moveNextAndGet();
                if (c == '\"') {
                    beginning = '\"';
                } else if (c == '\'' && opts.isStringSingleQuotes()) {
                    beginning = '\'';
                } else {
                    err = "invalid character for string: not starts with \": " + c;
                    throw ParserUtils.err(opts, err);
                }
                ++state;
            }
        }
        while (cs.hasNext()) {
            if (state == 1) {
                c = cs.moveNextAndGet();
                if (c == '\\') {
                    // escape
                    state = 2;
                } else if (c == beginning) {
                    // end
                    state = 7;
                    break;
                } else if (c > 31) {
                    // normal
                    append(c);
                    continue;
                } else {
                    err = "invalid character in string: code is: " + ((int) c);
                    throw ParserUtils.err(opts, err);
                }
            }
            if (state == 2) {
                if (cs.hasNext()) {
                    c = cs.moveNextAndGet();
                    switch (c) {
                        case '\"':
                            append('\"');
                            state = 1;
                            break;
                        case '\'':
                            // not in json standard
                            // so check if user enables stringSingleQuotes
                            if (!opts.isStringSingleQuotes()) {
                                err = "invalid escape character: " + c;
                                throw ParserUtils.err(opts, err);
                            }
                            append('\'');
                            state = 1;
                            break;
                        case '\\':
                            append('\\');
                            state = 1;
                            break;
                        case '/':
                            append('/');
                            state = 1;
                            break;
                        case 'b':
                            append('\b');
                            state = 1;
                            break;
                        case 'f':
                            append('\f');
                            state = 1;
                            break;
                        case 'n':
                            append('\n');
                            state = 1;
                            break;
                        case 'r':
                            append('\r');
                            state = 1;
                            break;
                        case 't':
                            append('\t');
                            state = 1;
                            break;
                        case 'u':
                            state = 3;
                            break;
                        default:
                            err = "invalid escape character: " + c;
                            throw ParserUtils.err(opts, err);
                    }
                    if (state == 1) {
                        continue;
                    }
                }
            }
            if (state == 3) {
                if (cs.hasNext()) {
                    c = cs.moveNextAndGet();
                    u1 = parseHex(c);
                    if (u1 == -1) {
                        err = "invalid hex character in \\u[H]HHH: " + c;
                        throw ParserUtils.err(opts, err);
                    }
                    ++state;
                }
            }
            if (state == 4) {
                if (cs.hasNext()) {
                    c = cs.moveNextAndGet();
                    u2 = parseHex(c);
                    if (u2 == -1) {
                        err = "invalid hex character in \\u" + u1 + "[H]HH: " + c;
                        throw ParserUtils.err(opts, err);
                    }
                    ++state;
                }
            }
            if (state == 5) {
                if (cs.hasNext()) {
                    c = cs.moveNextAndGet();
                    u3 = parseHex(c);
                    if (u3 == -1) {
                        err = "invalid hex character in \\u" + u1 + "" + u2 + "[H]H: " + c;
                        throw ParserUtils.err(opts, err);
                    }
                    ++state;
                }
            }
            if (state == 6) {
                if (cs.hasNext()) {
                    c = cs.moveNextAndGet();
                    int u4 = parseHex(c);
                    if (u4 == -1) {
                        err = "invalid hex character in \\u" + u1 + "" + u2 + "" + u3 + "[H]: " + c;
                        throw ParserUtils.err(opts, err);
                    }
                    append((char) (u1 << 12 | u2 << 8 | u3 << 4 | u4));
                    state = 1;
                }
            }
            if (state == 8) {
                break;
            }
        }
        if (state == 7) {
            ++state;
            return true;
        } else if (state == 8) {
            cs.skipBlank();
            if (cs.hasNext()) {
                throw new ParserFinishedException();
            }
            return false;
        } else if (isComplete) {
            err = "expecting more characters to build string";
            throw ParserUtils.err(opts, err);
        } else {
            return false;
        }
    }

    @Override
    public JSON.String build(CharStream cs, boolean isComplete) throws NullPointerException, JsonParseException, ParserFinishedException {
        if (cs == null) {
            throw new NullPointerException();
        }
        if (tryParse(cs, isComplete)) {
            opts.getListener().onStringEnd(this);
            SimpleString ret = new SimpleString(builder.toString());
            opts.getListener().onString(ret);

            ParserUtils.checkEnd(cs, opts, "string");
            return ret;
        } else {
            return null;
        }
    }

    @Override
    public String buildJavaObject(CharStream cs, boolean isComplete) throws NullPointerException, JsonParseException, ParserFinishedException {
        if (cs == null) {
            throw new NullPointerException();
        }
        if (tryParse(cs, isComplete)) {
            opts.getListener().onStringEnd(this);
            String s = builder.toString();
            opts.getListener().onString(s);

            ParserUtils.checkEnd(cs, opts, "string");
            return s;
        } else {
            return null;
        }
    }

    @Override
    public boolean completed() {
        return state == 8;
    }
}
