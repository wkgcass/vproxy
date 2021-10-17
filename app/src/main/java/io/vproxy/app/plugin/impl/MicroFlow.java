package io.vproxy.app.plugin.impl;

import io.vproxy.base.util.coll.LRUMap;
import io.vproxy.vpacket.tuples.PacketFullTuple;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.PacketFilterHelper;
import io.vproxy.vswitch.plugin.FilterResult;

import java.util.function.BiFunction;

class MicroFlow {
    final LRUMap<PacketFullTuple, ActionCacheEntry> cache;
    private long hitCount = 0;
    private long missCount = 0;

    MicroFlow(int cacheSize) {
        cache = new LRUMap<>(cacheSize);
    }

    public BiFunction<PacketFilterHelper, PacketBuffer, FilterResult> lookup(PacketFullTuple tuple) {
        var entry = cache.get(tuple);
        if (entry == null) {
            ++missCount;
            return null;
        }
        ++hitCount;
        entry.resetTimer();
        return entry.action;
    }

    public void record(PacketFullTuple tuple, BiFunction<PacketFilterHelper, PacketBuffer, FilterResult> action) {
        var old = cache.put(tuple, new ActionCacheEntry(tuple, action, this));
        if (old != null) {
            old.destroy();
        }
    }

    public int getCurrentCacheCount() {
        return cache.size();
    }

    public long getHitCount() {
        return hitCount;
    }

    public long getMissCount() {
        return missCount;
    }
}
