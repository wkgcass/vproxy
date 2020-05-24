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

package vjson.util;

import vjson.JSON;
import vjson.simple.*;

import java.util.*;
import java.util.function.Function;

@SuppressWarnings("WeakerAccess")
public class Transformer {
    private final Map<Class<?>, Function<Object, JSON.Instance>> rules = new LinkedHashMap<>();

    public Transformer() {
        this.
            addRule(Boolean.class, SimpleBool::new)
            .addRule(Integer.class, SimpleInteger::new)
            .addRule(Long.class, SimpleLong::new)
            .addRule(Double.class, SimpleDouble::new)
            .addRule(Float.class, (Function<Float, JSON.Instance>) SimpleDouble::new)
            .addRule(Number.class, x -> new SimpleInteger(x.intValue()))
            .addRule(String.class, SimpleString::new)
            .addRule(Character.class, c -> new SimpleString(Character.toString(c)));
    }

    public <T> Transformer addRule(Class<T> type, Function<T, JSON.Instance> func) {
        //noinspection unchecked
        rules.put(type, (Function<Object, JSON.Instance>) func);
        return this;
    }

    public Transformer removeRule(Class type) {
        rules.remove(type);
        return this;
    }

    public JSON.Instance transform(Object input) {
        if (input == null)
            return new SimpleNull();
        if (input instanceof JSON.Instance) {
            return (JSON.Instance) input;
        }
        if (input instanceof Collection) {
            Collection coll = (Collection) input;
            List<JSON.Instance> list = new ArrayList<>(coll.size());
            for (Object o : coll) {
                list.add(transform(o));
            }
            return new SimpleArray(list, TrustedFlag.FLAG) {
            };
        }
        if (input instanceof Map) {
            Map inputMap = (Map) input;
            List<SimpleObjectEntry<JSON.Instance>> map = new ArrayList<>(inputMap.size());
            for (Object key : inputMap.keySet()) {
                if (!(key instanceof String)) {
                    throw new IllegalArgumentException("keys of map should be String");
                }
                map.add(new SimpleObjectEntry<>((String) key, transform(inputMap.get(key))));
            }
            return new SimpleObject(map, TrustedFlag.FLAG) {
            };
        }
        Class<?> type = input.getClass();
        for (Map.Entry<Class<?>, Function<Object, JSON.Instance>> entry : rules.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                return entry.getValue().apply(input);
            }
        }
        throw new IllegalArgumentException("unknown input type: " + type.getName());
    }
}
