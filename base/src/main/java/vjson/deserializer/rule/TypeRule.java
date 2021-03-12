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

package vjson.deserializer.rule;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class TypeRule<T> extends Rule<T> {
    private final String defaultTypeName;
    private final Rule<? extends T> defaultRule;
    private final Map<String, ObjectRule<? extends T>> rules = new LinkedHashMap<>();

    public TypeRule() {
        this.defaultTypeName = null;
        this.defaultRule = null;
    }

    public TypeRule(String defaultTypeName, ObjectRule<? extends T> defaultRule) {
        this.defaultTypeName = defaultTypeName;
        this.defaultRule = defaultRule;
        rules.put(defaultTypeName, defaultRule);
    }

    public TypeRule(Class<?> aClass, ObjectRule<? extends T> defaultRule) {
        this(aClass.getName(), defaultRule);
    }

    public TypeRule<T> type(String typeName, ObjectRule<? extends T> rule) {
        rules.put(typeName, rule);
        return this;
    }

    public TypeRule<T> type(Class<?> aClass, ObjectRule<? extends T> rule) {
        return type(aClass.getName(), rule);
    }

    public ObjectRule<T> getDefaultRule() {
        //noinspection unchecked
        return (ObjectRule<T>) defaultRule;
    }

    public ObjectRule<T> getRule(String typeName) {
        //noinspection unchecked
        return (ObjectRule<T>) rules.get(typeName);
    }

    @Override
    void toString(StringBuilder sb, Set<Rule> processedListsOrObjects) {
        if (!processedListsOrObjects.add(this)) {
            sb.append("TypeRule{...recursive...}");
            return;
        }
        sb.append("TypeRule{");
        boolean isFirst = true;
        for (Map.Entry<String, ObjectRule<? extends T>> entry : rules.entrySet()) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append(",");
            }
            sb.append("@type[").append(entry.getKey());
            if (entry.getKey().equals(defaultTypeName)) {
                sb.append("*");
            }
            sb.append("]=>");
            entry.getValue().toString(sb, processedListsOrObjects);
        }
        sb.append("}");
    }
}
