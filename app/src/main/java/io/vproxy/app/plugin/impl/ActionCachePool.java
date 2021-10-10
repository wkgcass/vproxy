package io.vproxy.app.plugin.impl;

import io.vproxy.base.util.coll.LRUMap;
import io.vproxy.vpacket.tuples.SevenTuple;

class ActionCachePool {
    private static final int CACHE_SIZE = 1024;
    final LRUMap<SevenTuple, ActionCache> cache = new LRUMap<>(CACHE_SIZE);

    public ActionCache lookup(SevenTuple tuple) {
        return cache.get(tuple);
    }

    public void remove(SevenTuple tuple) {
        cache.remove(tuple);
    }

    public void record(SevenTuple tuple, ActionCache res) {
        cache.put(tuple, res);
    }

    public boolean isFull() {
        return cache.size() >= CACHE_SIZE;
    }
}
