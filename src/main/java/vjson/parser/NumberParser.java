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
import vjson.simple.SimpleDouble;
import vjson.simple.SimpleExp;
import vjson.simple.SimpleInteger;
import vjson.simple.SimpleLong;

public class NumberParser implements Parser<JSON.Number> {
    private final ParserOptions opts;
    private int state;
    // 0->negative_or_1-9_or_0,
    // 1->got-negative(1-9_or_0),
    // 2->0-9_or_dot_or_e,
    // 3->after-dot(0-9),
    // 4->fraction(0-9)_or_e,
    // 5->after-e(symbol_or_0-9),
    // 6->after-e-got-symbol(0-9),
    // 7->after-e(0-9),
    // 8->finish,
    // 9->already_returned

    // this is a sub state indicating that the parser want to peek a character,
    // but not provided yet
    private boolean wantPeek;

    private boolean negative;
    private long integer;

    private boolean hasFraction;
    private double fraction;
    private double fractionNextMulti;

    private boolean hasExponent;
    private boolean exponentNegative;
    private int exponent;

    public NumberParser() {
        this(ParserOptions.DEFAULT);
    }

    public NumberParser(ParserOptions opts) throws NullPointerException {
        if (opts == null) {
            throw new NullPointerException();
        }
        this.opts = ParserOptions.ensureNotModifiedByOutside(opts);
        reset();
    }

    @Override
    public void reset() {
        state = 0;
        wantPeek = false;
        negative = false;
        integer = 0;
        hasFraction = false;
        fraction = 0.0;
        fractionNextMulti = 10;
        hasExponent = false;
        exponentNegative = false;
        exponent = 0;
    }

    public boolean isNegative() {
        return negative;
    }

    public void setNegative(boolean negative) {
        this.negative = negative;
    }

    public long getInteger() {
        return integer;
    }

    public void setInteger(long integer) {
        if (integer < 0)
            throw new IllegalArgumentException();
        this.integer = integer;
    }

    public boolean hasFraction() {
        return hasFraction;
    }

    public void clearFraction() {
        this.hasFraction = false;
        this.fraction = 0.0;
        this.fractionNextMulti = 10;
    }

    public double getFraction() {
        return fraction;
    }

    public double getFractionNextMulti() {
        return fractionNextMulti;
    }

    public void setFraction(double fraction, int multi) {
        if (fraction > 1 || fraction < 0 || multi < 1)
            throw new IllegalArgumentException();
        this.hasFraction = true;
        this.fraction = fraction;
        this.fractionNextMulti = 10;
        for (int i = 0; i < multi; ++i) {
            this.fractionNextMulti *= 10;
        }
    }

    public boolean hasExponent() {
        return hasExponent;
    }

    public void clearExponent() {
        this.hasExponent = false;
        this.exponentNegative = false;
        this.exponent = 0;
    }

    public boolean isExponentNegative() {
        return exponentNegative;
    }

    public void setExponentNegative(boolean exponentNegative) {
        this.exponentNegative = exponentNegative;
    }

    public int getExponent() {
        return exponent;
    }

    public void setExponent(int exponent) {
        if (exponent < 0)
            throw new IllegalArgumentException();
        this.hasExponent = true;
        this.exponent = exponent;
    }

    private int parseDigit(char c) {
        if ('0' <= c && c <= '9') {
            return c - 48;
        } else {
            return -1;
        }
    }

    private void fractionBegin() {
        hasFraction = true;
        state = 3;
        opts.getListener().onNumberFractionBegin(this, integer);
    }

    private void exponentBegin() {
        hasExponent = true;
        state = 5;
        opts.getListener().onNumberExponentBegin(this, integer + (hasFraction ? fraction : 0));
    }

    private void gotoFractionExponentEnd(CharStream cs, boolean isComplete) {
        if (cs.hasNext()) {
            char peek = cs.peekNext();
            if (peek == '.') {
                cs.moveNextAndGet();
                fractionBegin();
            } else if (peek == 'e' || peek == 'E') {
                cs.moveNextAndGet();
                exponentBegin();
            } else {
                // not number character, consider it ends the number literal
                state = 8;
            }
        } else {
            if (isComplete) {
                // ends
                state = 8;
            } else {
                wantPeek = true;
            }
        }
    }

    @SuppressWarnings("Duplicates")
    private boolean tryParse(CharStream cs, boolean isComplete) {
        if (wantPeek) {
            wantPeek = false;
            gotoFractionExponentEnd(cs, isComplete);
        }

        char c;
        String err;
        if (state == 0) {
            cs.skipBlank();
            if (cs.hasNext()) {
                opts.getListener().onNumberBegin(this);
                c = cs.moveNextAndGet();
                if (c == '-') {
                    negative = true;
                    state = 1;
                } else {
                    int d = parseDigit(c);
                    if (d == -1) {
                        err = "invalid digit in number: " + c;
                        throw ParserUtils.err(opts, err);
                    }
                    if (d == 0) {
                        integer = 0;
                        gotoFractionExponentEnd(cs, isComplete);
                    } else {
                        integer = d;
                        state = 2;
                    }
                }
            }
        }
        if (state == 1) {
            if (cs.hasNext()) {
                c = cs.moveNextAndGet();
                int d = parseDigit(c);
                if (d == -1) {
                    err = "invalid digit in number: " + c;
                    throw ParserUtils.err(opts, err);
                }
                if (d == 0) {
                    integer = 0;
                    gotoFractionExponentEnd(cs, isComplete);
                } else {
                    integer = d;
                    state = 2;
                }
            }
        }
        while (cs.hasNext()) {
            if (state == 2) {
                c = cs.peekNext();
                if (c == '.') {
                    cs.moveNextAndGet();
                    fractionBegin();
                } else if (c == 'e' || c == 'E') {
                    cs.moveNextAndGet();
                    exponentBegin();
                } else {
                    int d = parseDigit(c);
                    if (d == -1) {
                        state = 8;
                    } else {
                        cs.moveNextAndGet();
                        integer *= 10;
                        integer += d;
                        // state not changed
                        state = 2;
                    }
                }
            }
            if (state == 3) {
                if (cs.hasNext()) {
                    c = cs.moveNextAndGet();
                    int d = parseDigit(c);
                    if (d == -1) {
                        err = "invalid digit in fraction: " + c;
                        throw ParserUtils.err(opts, err);
                    }
                    fraction += d / fractionNextMulti;
                    fractionNextMulti *= 10;
                    state = 4;
                }
            }
            if (state == 4) {
                if (cs.hasNext()) {
                    c = cs.peekNext();
                    if (c == 'e' || c == 'E') {
                        cs.moveNextAndGet();
                        exponentBegin();
                    } else {
                        int d = parseDigit(c);
                        if (d == -1) {
                            state = 8;
                        } else {
                            cs.moveNextAndGet();
                            fraction += d / fractionNextMulti;
                            fractionNextMulti *= 10;
                            state = 4;
                            continue;
                        }
                    }
                }
            }
            if (state == 5) {
                if (cs.hasNext()) {
                    c = cs.moveNextAndGet();
                    if (c == '+') {
                        exponentNegative = false;
                        state = 6;
                    } else if (c == '-') {
                        exponentNegative = true;
                        state = 6;
                    } else {
                        int d = parseDigit(c);
                        if (d == -1) {
                            err = "invalid digit in exponent: " + c;
                            throw ParserUtils.err(opts, err);
                        }
                        exponent *= 10;
                        exponent += d;
                        state = 7;
                    }
                }
            }
            if (state == 6) {
                if (cs.hasNext()) {
                    c = cs.moveNextAndGet();
                    int d = parseDigit(c);
                    if (d == -1) {
                        err = "invalid digit in exponent: " + c;
                        throw ParserUtils.err(opts, err);
                    }
                    exponent *= 10;
                    exponent += d;
                    state = 7;
                }
            }
            if (state == 7) {
                if (cs.hasNext()) {
                    char peek = cs.peekNext();
                    int d = parseDigit(peek);
                    if (d == -1) {
                        state = 8;
                    } else {
                        cs.moveNextAndGet();
                        exponent *= 10;
                        exponent += d;
                        state = 7;
                    }
                }
            }
            if (state == 8) {
                break;
            }
            if (state == 9) {
                break;
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
            // note: cs.hasNext() is false when reaches here
            if (state == 2 || state == 4 || state == 7) {
                state = 9;
                return true;
            } else {
                err = "expecting more characters to build number";
                throw ParserUtils.err(opts, err);
            }
        } else {
            return false;
        }
    }

    @Override
    public JSON.Number build(CharStream cs, boolean isComplete) throws NullPointerException, JsonParseException, ParserFinishedException {
        if (cs == null) {
            throw new NullPointerException();
        }
        if (tryParse(cs, isComplete)) {
            opts.getListener().onNumberEnd(this);
            JSON.Number ret;
            if (hasFraction) {
                double num = integer + fraction;
                num = negative ? -num : num;
                if (hasExponent) {
                    ret = new SimpleExp(num, exponentNegative ? -exponent : exponent);
                } else {
                    ret = new SimpleDouble(num);
                }
            } else {
                long num = negative ? -integer : integer;
                if (hasExponent) {
                    ret = new SimpleExp(num, exponentNegative ? -exponent : exponent);
                } else {
                    if (negative) {
                        if (num < Integer.MIN_VALUE) {
                            ret = new SimpleLong(num);
                        } else {
                            ret = new SimpleInteger((int) num);
                        }
                    } else {
                        if (num > Integer.MAX_VALUE) {
                            ret = new SimpleLong(num);
                        } else {
                            ret = new SimpleInteger((int) num);
                        }
                    }
                }
            }
            opts.getListener().onNumber(ret);

            ParserUtils.checkEnd(cs, opts, "number");
            return ret;
        } else {
            return null;
        }
    }

    @Override
    public Number buildJavaObject(CharStream cs, boolean isComplete) throws NullPointerException, JsonParseException, ParserFinishedException {
        if (cs == null) {
            throw new NullPointerException();
        }
        if (tryParse(cs, isComplete)) {
            opts.getListener().onNumberEnd(this);
            Number ret;
            if (hasFraction) {
                double num = integer + fraction;
                num = negative ? -num : num;
                if (hasExponent) {
                    ret = num * Math.pow(10, exponentNegative ? -exponent : exponent);
                } else {
                    ret = num;
                }
            } else {
                long num = negative ? -integer : integer;
                if (hasExponent) {
                    ret = num * Math.pow(10, exponentNegative ? -exponent : exponent);
                } else {
                    if (negative) {
                        if (num < Integer.MIN_VALUE) {
                            ret = num;
                        } else {
                            ret = (int) num;
                        }
                    } else {
                        if (num > Integer.MAX_VALUE) {
                            ret = num;
                        } else {
                            ret = (int) num;
                        }
                    }
                }
            }
            opts.getListener().onNumber(ret);

            ParserUtils.checkEnd(cs, opts, "number");
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
