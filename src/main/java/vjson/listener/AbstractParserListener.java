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

package vjson.listener;

import vjson.JSON;
import vjson.ParserListener;
import vjson.parser.*;

import java.util.List;
import java.util.Map;

public abstract class AbstractParserListener implements ParserListener {
    @Override
    public void onObjectBegin(ObjectParser object) {

    }

    @Override
    public void onObjectKey(ObjectParser object, String key) {

    }

    @Override
    public void onObjectValue(ObjectParser object, String key, JSON.Instance value) {

    }

    @Override
    public void onObjectValueJavaObject(ObjectParser object, String key, Object value) {

    }

    @Override
    public void onObjectEnd(ObjectParser object) {

    }

    @Override
    public void onObject(JSON.Object object) {

    }

    @Override
    public void onObject(Map<String, Object> object) {

    }

    @Override
    public void onArrayBegin(ArrayParser array) {

    }

    @Override
    public void onArrayValue(ArrayParser array, JSON.Instance value) {

    }

    @Override
    public void onArrayValueJavaObject(ArrayParser array, Object value) {

    }

    @Override
    public void onArrayEnd(ArrayParser array) {

    }

    @Override
    public void onArray(JSON.Array array) {

    }

    @Override
    public void onArray(List<Object> array) {

    }

    @Override
    public void onBoolBegin(BoolParser bool) {

    }

    @Override
    public void onBoolEnd(BoolParser bool) {

    }

    @Override
    public void onBool(JSON.Bool bool) {

    }

    @Override
    public void onBool(Boolean bool) {

    }

    @Override
    public void onNullBegin(NullParser n) {

    }

    @Override
    public void onNullEnd(NullParser n) {

    }

    @Override
    public void onNull(JSON.Null n) {

    }

    @Override
    public void onNull(Void n) {

    }

    @Override
    public void onNumberBegin(NumberParser number) {

    }

    @Override
    public void onNumberFractionBegin(NumberParser number, long integer) {

    }

    @Override
    public void onNumberExponentBegin(NumberParser number, double base) {

    }

    @Override
    public void onNumberEnd(NumberParser number) {

    }

    @Override
    public void onNumber(JSON.Number number) {

    }

    @Override
    public void onNumber(Number number) {

    }

    @Override
    public void onStringBegin(StringParser string) {

    }

    @Override
    public void onStringChar(StringParser string, char c) {

    }

    @Override
    public void onStringEnd(StringParser string) {

    }

    @Override
    public void onString(JSON.String string) {

    }

    @Override
    public void onString(String string) {

    }

    @Override
    public void onError(String err) {

    }
}
