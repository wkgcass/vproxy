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

public interface Stringifier {
    void beforeObjectBegin(StringBuilder sb, JSON.Object object);

    void afterObjectBegin(StringBuilder sb, JSON.Object object);

    void beforeObjectKey(StringBuilder sb, JSON.Object object, String key);

    void afterObjectKey(StringBuilder sb, JSON.Object object, String key);

    void beforeObjectColon(StringBuilder sb, JSON.Object object);

    void afterObjectColon(StringBuilder sb, JSON.Object object);

    void beforeObjectValue(StringBuilder sb, JSON.Object object, String key, JSON.Instance value);

    void afterObjectValue(StringBuilder sb, JSON.Object object, String key, JSON.Instance value);

    void beforeObjectComma(StringBuilder sb, JSON.Object object);

    void afterObjectComma(StringBuilder sb, JSON.Object object);

    void beforeObjectEnd(StringBuilder sb, JSON.Object object);

    void afterObjectEnd(StringBuilder sb, JSON.Object object);

    void beforeArrayBegin(StringBuilder sb, JSON.Array array);

    void afterArrayBegin(StringBuilder sb, JSON.Array array);

    void beforeArrayValue(StringBuilder sb, JSON.Array array, JSON.Instance value);

    void afterArrayValue(StringBuilder sb, JSON.Array array, JSON.Instance value);

    void beforeArrayComma(StringBuilder sb, JSON.Array array);

    void afterArrayComma(StringBuilder sb, JSON.Array array);

    void beforeArrayEnd(StringBuilder sb, JSON.Array array);

    void afterArrayEnd(StringBuilder sb, JSON.Array array);
}
