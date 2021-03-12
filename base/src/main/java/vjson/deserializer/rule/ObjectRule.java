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

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class ObjectRule<O> extends Rule<O> {
    public final Supplier<O> construct;
    private final Map<String, ObjectField<O, ?>> rules = new LinkedHashMap<>();

    public ObjectRule(Supplier<O> construct) {
        this.construct = construct;
    }

    public ObjectRule(Supplier<O> construct, ObjectRule<? super O> superRule) {
        this(construct);
        for (Map.Entry entry : superRule.rules.entrySet()) {
            //noinspection unchecked
            rules.put((String) entry.getKey(), (ObjectField<O, ?>) entry.getValue());
        }
    }

    public <V> ObjectRule<O> put(String key, BiConsumer<O, V> set, Rule<V> type) {
        if (rules.containsKey(key))
            throw new IllegalArgumentException("duplicated key: " + key);
        rules.put(key, new ObjectField<>(set, type));
        return this;
    }

    public ObjectField getRule(String key) {
        return rules.get(key);
    }

    @Override
    void toString(StringBuilder sb, Set<Rule> processedListsOrObjects) {
        if (!processedListsOrObjects.add(this)) {
            sb.append("Object{...recursive...}");
            return;
        }
        sb.append("Object{");
        boolean isFirst = true;
        for (Map.Entry<String, ObjectField<O, ?>> entry : rules.entrySet()) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append(",");
            }
            sb.append(entry.getKey()).append("=>");
            entry.getValue().rule.toString(sb, processedListsOrObjects);
        }
        sb.append("}");
    }
}
