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

package vjson.simple;

import vjson.JSON;
import vjson.Stringifier;

import java.util.*;

public class SimpleObject extends AbstractSimpleInstance<LinkedHashMap<String, Object>> implements JSON.Object {
    private final List<SimpleObjectEntry<JSON.Instance>> map;
    private LinkedHashSet<String> keySet;
    private List<String> keyList;
    private Map<String, JSON.Instance> fastSingleMap;
    private Map<String, List<JSON.Instance>> fastMultiMap;

    public SimpleObject(Map<String, JSON.Instance> initMap) throws NullPointerException, IllegalArgumentException {
        if (initMap == null) {
            throw new NullPointerException();
        }
        for (Map.Entry<String, JSON.Instance> entry : initMap.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("key should not be null");
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("value should not be null");
            }
        }
        this.map = new ArrayList<>(initMap.size());
        for (Map.Entry<String, JSON.Instance> entry : initMap.entrySet()) {
            this.map.add(new SimpleObjectEntry<>(entry.getKey(), entry.getValue()));
        }
    }

    public SimpleObject(List<SimpleObjectEntry<JSON.Instance>> initMap) throws NullPointerException, IllegalArgumentException {
        if (initMap == null) {
            throw new NullPointerException();
        }
        for (SimpleObjectEntry<JSON.Instance> entry : initMap) {
            if (entry == null) {
                throw new IllegalArgumentException("entry should not be null");
            }
            if (entry.key == null) {
                throw new IllegalArgumentException("key should not be null");
            }
            if (entry.value == null) {
                throw new IllegalArgumentException("value should not be null");
            }
        }
        this.map = new ArrayList<>(initMap);
    }

    protected SimpleObject(List<SimpleObjectEntry<JSON.Instance>> initMap, vjson.parser.TrustedFlag flag) {
        if (flag == null) {
            throw new UnsupportedOperationException();
        }
        this.map = initMap;
    }

    protected SimpleObject(List<SimpleObjectEntry<JSON.Instance>> initMap, vjson.util.TrustedFlag flag) {
        if (flag == null) {
            throw new UnsupportedOperationException();
        }
        this.map = initMap;
    }

    @Override
    public LinkedHashMap<String, Object> toJavaObject() {
        return new LinkedHashMap<>(super.toJavaObject());
    }

    @Override
    protected LinkedHashMap<String, Object> _toJavaObject() {
        LinkedHashMap<String, Object> javaObject = new LinkedHashMap<>();
        for (SimpleObjectEntry<JSON.Instance> entry : map) {
            javaObject.put(entry.key, entry.value.toJavaObject());
        }
        return javaObject;
    }

    @Override
    public void stringify(StringBuilder sb, Stringifier sfr) {
        sfr.beforeObjectBegin(sb, this);
        sb.append("{");
        sfr.afterObjectBegin(sb, this);
        boolean isFirst = true;
        for (SimpleObjectEntry<JSON.Instance> entry : map) {
            if (isFirst) isFirst = false;
            else {
                sfr.beforeObjectComma(sb, this);
                sb.append(",");
                sfr.afterObjectComma(sb, this);
            }
            sfr.beforeObjectKey(sb, this, entry.key);
            sb.append(JSON.String.stringify(entry.key));
            sfr.afterObjectKey(sb, this, entry.key);
            sfr.beforeObjectColon(sb, this);
            sb.append(":");
            sfr.afterObjectColon(sb, this);
            sfr.beforeObjectValue(sb, this, entry.key, entry.value);
            entry.value.stringify(sb, sfr);
            sfr.afterObjectValue(sb, this, entry.key, entry.value);
        }
        sfr.beforeObjectEnd(sb, this);
        sb.append("}");
        sfr.afterObjectEnd(sb, this);
    }

    @Override
    protected String _toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Object{");
        boolean isFirst = true;
        for (SimpleObjectEntry<JSON.Instance> entry : map) {
            if (isFirst) isFirst = false;
            else sb.append(", ");
            sb.append(entry.key).append(":").append(entry.value);
        }
        sb.append("}");
        return sb.toString();
    }

    private LinkedHashSet<String> _keySet() {
        if (this.keySet == null) {
            LinkedHashSet<String> set = new LinkedHashSet<>();
            for (SimpleObjectEntry entry : map) {
                set.add(entry.key);
            }
            this.keySet = set;
        }
        return this.keySet;
    }

    @Override
    public LinkedHashSet<String> keySet() {
        return new LinkedHashSet<>(_keySet());
    }

    @Override
    public List<String> keyList() {
        if (keyList == null) {
            List<String> list = new ArrayList<>(map.size());
            for (SimpleObjectEntry entry : map) {
                list.add(entry.key);
            }
            keyList = list;
        }
        return new ArrayList<>(keyList);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean containsKey(String key) {
        return _keySet().contains(key);
    }

    private Map<String, JSON.Instance> getFastSingleMap() {
        if (fastSingleMap == null) {
            fastSingleMap = new HashMap<>(map.size());
        }
        return fastSingleMap;
    }

    @Override
    public JSON.Instance get(String key) throws NullPointerException, NoSuchElementException {
        if (key == null)
            throw new NullPointerException();

        Map<String, JSON.Instance> fastMap = getFastSingleMap();
        if (fastMap.containsKey(key)) {
            return fastMap.get(key);
        }
        JSON.Instance inst = null;
        for (SimpleObjectEntry<JSON.Instance> entry : map) {
            if (entry.key.equals(key)) {
                inst = entry.value;
                break;
            }
        }
        if (inst == null)
            throw new NoSuchElementException();
        fastMap.put(key, inst);
        return inst;
    }

    private Map<String, List<JSON.Instance>> getFastMultiMap() {
        if (fastMultiMap == null) {
            fastMultiMap = new HashMap<>();
        }
        return fastMultiMap;
    }

    @Override
    public List<JSON.Instance> getAll(String key) {
        if (key == null)
            throw new NullPointerException();
        if (!_keySet().contains(key)) {
            throw new NoSuchElementException();
        }

        Map<String, List<JSON.Instance>> fastMap = getFastMultiMap();
        if (fastMap.containsKey(key)) {
            return fastMap.get(key);
        }
        List<JSON.Instance> ret = new LinkedList<>();
        for (SimpleObjectEntry<JSON.Instance> entry : map) {
            if (entry.key.equals(key)) {
                ret.add(entry.value);
            }
        }
        ret = Collections.unmodifiableList(ret);
        fastMap.put(key, ret);
        return ret;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JSON.Object)) return false;
        JSON.Object that = (JSON.Object) o;
        if (!that.keySet().equals(_keySet())) return false;
        for (String key : keySet()) {
            if (!that.get(key).equals(get(key))) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(map);
    }
}
