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

import java.util.List;
import java.util.Map;

public class ParserUtils {
    private ParserUtils() {
    }

    public static boolean isWhiteSpace(char c) {
        return c == '\n'
            || c == '\r'
            || c == ' '
            || c == '\t';
    }

    static void checkEnd(CharStream cs, ParserOptions opts, String type) {
        if (opts.isEnd()) {
            cs.skipBlank();
            if (cs.hasNext()) {
                String err = "input stream contain extra characters other than " + type;
                opts.getListener().onError(err);
                throw new JsonParseException(err);
            }
        }
    }

    static JsonParseException err(ParserOptions opts, String msg) {
        opts.getListener().onError(msg);
        return new JsonParseException(msg);
    }

    static ParserOptions subParserOptions(ParserOptions opts) {
        if (opts == ParserOptions.DEFAULT || opts == ParserOptions.DEFAULT_NO_END) {
            return ParserOptions.DEFAULT_NO_END;
        }
        if (opts == ParserOptions.DEFAULT_JAVA_OBJECT || opts == ParserOptions.DEFAULT_JAVA_OBJECT_NO_END) {
            return ParserOptions.DEFAULT_JAVA_OBJECT_NO_END;
        }
        if (!opts.isEnd()) {
            return opts;
        }
        return new ParserOptions(opts).setEnd(false);
    }

    private static final ThreadLocal<ArrayParser> threadLocalArrayParser = new ThreadLocal<>();
    private static final ThreadLocal<ObjectParser> threadLocalObjectParser = new ThreadLocal<>();
    private static final ThreadLocal<StringParser> threadLocalStringParser = new ThreadLocal<>();

    private static final ThreadLocal<ArrayParser> threadLocalArrayParserJavaObject = new ThreadLocal<>();
    private static final ThreadLocal<ObjectParser> threadLocalObjectParserJavaObject = new ThreadLocal<>();
    private static final ThreadLocal<StringParser> threadLocalStringParserJavaObject = new ThreadLocal<>();

    public static JSON.Instance buildFrom(CharStream cs) throws NullPointerException, JsonParseException {
        ParserOptions opts = ParserOptions.DEFAULT;
        cs.skipBlank();
        if (!cs.hasNext()) {
            throw new JsonParseException("empty input string");
        }
        char first = cs.peekNext();
        switch (first) {
            case '{': {
                ObjectParser p = threadLocalObjectParser.get();
                if (p == null) {
                    p = new ObjectParser(opts);
                    threadLocalObjectParser.set(p);
                }
                JSON.Object ret;
                try {
                    ret = p.last(cs);
                } finally {
                    p.reset();
                }
                return ret;
            }
            case '[': {
                ArrayParser p = threadLocalArrayParser.get();
                if (p == null) {
                    p = new ArrayParser(opts);
                    threadLocalArrayParser.set(p);
                }
                JSON.Array ret;
                try {
                    ret = p.last(cs);
                } finally {
                    p.reset();
                }
                return ret;
            }
            case '\'':
                throw new JsonParseException("not valid json string: stringSingleQuotes not enabled");
            case '"': {
                StringParser p = threadLocalStringParser.get();
                if (p == null) {
                    p = new StringParser(opts);
                    threadLocalStringParser.set(p);
                }
                JSON.String ret;
                try {
                    ret = p.last(cs);
                } finally {
                    p.reset();
                }
                return ret;
            }
            default:
                return build(cs, opts);
        }
    }

    public static JSON.Instance buildFrom(CharStream cs, ParserOptions opts) throws NullPointerException, JsonParseException {
        if (cs == null) {
            throw new NullPointerException();
        }
        if (opts == null) {
            throw new NullPointerException();
        }
        return build(cs, opts);
    }

    public static Object buildJavaObject(CharStream cs) throws NullPointerException, JsonParseException {
        ParserOptions opts = ParserOptions.DEFAULT_JAVA_OBJECT;
        cs.skipBlank();
        if (!cs.hasNext()) {
            throw new JsonParseException("empty input string");
        }
        char first = cs.peekNext();
        switch (first) {
            case '{': {
                ObjectParser p = threadLocalObjectParserJavaObject.get();
                if (p == null) {
                    p = new ObjectParser(opts);
                    threadLocalObjectParserJavaObject.set(p);
                }
                Map ret;
                try {
                    ret = p.buildJavaObject(cs, true);
                } finally {
                    p.reset();
                }
                return ret;
            }
            case '[': {
                ArrayParser p = threadLocalArrayParserJavaObject.get();
                if (p == null) {
                    p = new ArrayParser(opts);
                    threadLocalArrayParserJavaObject.set(p);
                }
                List ret;
                try {
                    ret = p.buildJavaObject(cs, true);
                } finally {
                    p.reset();
                }
                return ret;
            }
            case '\'':
                throw new JsonParseException("not valid json string: stringSingleQuotes not enabled");
            case '"': {
                StringParser p = threadLocalStringParserJavaObject.get();
                if (p == null) {
                    p = new StringParser(opts);
                    threadLocalStringParserJavaObject.set(p);
                }
                String ret;
                try {
                    ret = p.buildJavaObject(cs, true);
                } finally {
                    p.reset();
                }
                return ret;
            }
            default:
                return buildJ(cs, opts);
        }
    }

    public static Object buildJavaObject(CharStream cs, ParserOptions opts) throws NullPointerException, JsonParseException {
        if (cs == null) {
            throw new NullPointerException();
        }
        if (opts == null) {
            throw new NullPointerException();
        }
        return buildJ(cs, opts);
    }

    @SuppressWarnings("DuplicateBranchesInSwitch")
    private static Parser parser(CharStream cs, ParserOptions opts) throws JsonParseException {
        cs.skipBlank();
        if (!cs.hasNext()) {
            throw new JsonParseException("empty input string");
        }
        char first = cs.peekNext();
        switch (first) {
            case '{':
                return new ObjectParser(opts);
            case '[':
                return new ArrayParser(opts);
            case '\'':
                if (!opts.isStringSingleQuotes()) {
                    throw new JsonParseException("not valid json string: stringSingleQuotes not enabled");
                }
            case '"':
                return new StringParser(opts);
            case 'n':
                return new NullParser(opts);
            case 't':
                return new BoolParser(opts);
            case 'f':
                return new BoolParser(opts);
            case '-':
                return new NumberParser(opts);
            default:
                if (first >= '0' && first <= '9') {
                    return new NumberParser(opts);
                }
                // invalid json
                throw new JsonParseException("not valid json string");
        }
    }

    private static JSON.Instance build(CharStream cs, ParserOptions opts) throws IllegalArgumentException, JsonParseException {
        return parser(cs, opts).build(cs, true);
    }

    private static Object buildJ(CharStream cs, ParserOptions opts) throws IllegalArgumentException, JsonParseException {
        opts.setMode(ParserMode.JAVA_OBJECT);
        return parser(cs, opts).buildJavaObject(cs, true);
    }
}
