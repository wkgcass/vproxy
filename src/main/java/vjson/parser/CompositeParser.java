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
import vjson.Parser;
import vjson.ex.JsonParseException;

@SuppressWarnings("WeakerAccess")
public class CompositeParser {
    private final ParserOptions opts;

    private ArrayParser arrayParser;
    private BoolParser boolParser;
    private NullParser nullParser;
    private NumberParser numberParser;
    private ObjectParser objectParser;
    private StringParser stringParser;

    private StringParser keyParser;

    protected CompositeParser(ParserOptions opts) {
        this.opts = opts;
    }

    protected ArrayParser getArrayParser() {
        if (arrayParser == null) {
            arrayParser = new ArrayParser(ParserUtils.subParserOptions(opts));
        } else {
            arrayParser.reset();
        }
        return arrayParser;
    }

    protected BoolParser getBoolParser() {
        if (boolParser == null) {
            boolParser = new BoolParser(ParserUtils.subParserOptions(opts));
        } else {
            boolParser.reset();
        }
        return boolParser;
    }

    protected NullParser getNullParser() {
        if (nullParser == null) {
            nullParser = new NullParser(ParserUtils.subParserOptions(opts));
        } else {
            nullParser.reset();
        }
        return nullParser;
    }

    protected NumberParser getNumberParser() {
        if (numberParser == null) {
            numberParser = new NumberParser(ParserUtils.subParserOptions(opts));
        } else {
            numberParser.reset();
        }
        return numberParser;
    }

    protected ObjectParser getObjectParser() {
        if (objectParser == null) {
            objectParser = new ObjectParser(ParserUtils.subParserOptions(opts));
        } else {
            objectParser.reset();
        }
        return objectParser;
    }

    protected StringParser getStringParser() {
        if (stringParser == null) {
            stringParser = new StringParser(ParserUtils.subParserOptions(opts));
        } else {
            stringParser.reset();
        }
        return stringParser;
    }

    @SuppressWarnings("DuplicateBranchesInSwitch")
    protected Parser getSubParser(CharStream cs) {
        // the caller is responsible for cs.skipBlank() and checking cs.hasNext()
        char first = cs.peekNext();
        switch (first) {
            case '{':
                return getObjectParser();
            case '[':
                return getArrayParser();
            case '\'':
                if (!opts.isStringSingleQuotes()) {
                    throw new JsonParseException("not valid json string");
                }
            case '"':
                return getStringParser();
            case 'n':
                return getNullParser();
            case 't':
                return getBoolParser();
            case 'f':
                return getBoolParser();
            case '-':
                return getNumberParser();
            default:
                if (first >= '0' && first <= '9') {
                    return getNumberParser();
                }
                // invalid json
                throw new JsonParseException("not valid json string");
        }
    }

    public StringParser getKeyParser() {
        if (keyParser == null) {
            ParserOptions opts;
            if (ParserOptions.isDefaultOptions(this.opts)) {
                opts = ParserOptions.DEFAULT_JAVA_OBJECT_NO_END;
            } else {
                opts = new ParserOptions(this.opts).setEnd(false).setMode(ParserMode.JAVA_OBJECT);
            }
            keyParser = new StringParser(opts);
        } else {
            keyParser.reset();
        }
        return keyParser;
    }
}
