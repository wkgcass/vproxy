package vproxy.base.util.coll;

import java.util.LinkedHashMap;
import java.util.Map;

public class AppendableMap<K, V> extends LinkedHashMap<K, V> {
    public <KK extends K, VV extends V> AppendableMap<KK, VV> append(K k, V v) {
        put(k, v);
        //noinspection unchecked
        return (AppendableMap<KK, VV>) this;
    }

    public <KK extends K, VV extends V> AppendableMap<KK, VV> appendAll(Map<KK, VV> m) {
        putAll(m);
        //noinspection unchecked
        return (AppendableMap<KK, VV>) this;
    }
}
