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

import vjson.parser.*;

import java.util.List;
import java.util.Map;

public interface ParserListener {
    void onObjectBegin(ObjectParser object);

    void onObjectKey(ObjectParser object, String key);

    void onObjectValue(ObjectParser object, String key, JSON.Instance value);

    void onObjectValueJavaObject(ObjectParser object, String key, Object value);

    void onObjectEnd(ObjectParser object);

    void onObject(JSON.Object object);

    void onObject(Map<String, Object> object);

    void onArrayBegin(ArrayParser array);

    void onArrayValue(ArrayParser array, JSON.Instance value);

    void onArrayValueJavaObject(ArrayParser array, Object value);

    void onArrayEnd(ArrayParser array);

    void onArray(JSON.Array array);

    void onArray(List<Object> array);

    void onBoolBegin(BoolParser bool);

    void onBoolEnd(BoolParser bool);

    void onBool(JSON.Bool bool);

    void onBool(Boolean bool);

    void onNullBegin(NullParser n);

    void onNullEnd(NullParser n);

    void onNull(JSON.Null n);

    void onNull(Void n);

    void onNumberBegin(NumberParser number);

    void onNumberFractionBegin(NumberParser number, long integer);

    void onNumberExponentBegin(NumberParser number, double base);

    void onNumberEnd(NumberParser number);

    void onNumber(JSON.Number number);

    void onNumber(Number number);

    void onStringBegin(StringParser string);

    void onStringChar(StringParser string, char c);

    void onStringEnd(StringParser string);

    void onString(JSON.String string);

    void onString(String string);

    void onError(String err);
}
