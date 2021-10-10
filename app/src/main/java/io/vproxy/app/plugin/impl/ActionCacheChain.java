package io.vproxy.app.plugin.impl;

import io.vproxy.vpacket.tuples.SevenTuple;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.PacketFilterHelper;
import io.vproxy.vswitch.plugin.FilterResult;

import java.util.ArrayList;
import java.util.function.BiFunction;

class ActionCacheChain {
    private static final int CHAIN_SIZE = 16;
    private static final int LEVEL_UP_COUNT = 128;

    private final ArrayList<ActionCachePool> pools = new ArrayList<>(CHAIN_SIZE);

    ActionCacheChain() {
        for (int i = 0; i < CHAIN_SIZE; ++i) {
            pools.add(new ActionCachePool());
        }
    }

    public BiFunction<PacketFilterHelper, PacketBuffer, FilterResult> lookup(SevenTuple tuple) {
        for (int i = 0; i < CHAIN_SIZE; ++i) {
            var pool = pools.get(i);
            var res = pool.lookup(tuple);
            if (res == null) {
                continue;
            }
            if (i > 0) {
                res.hit += 1;
                if (res.hit > LEVEL_UP_COUNT) {
                    res.hit = 0;
                    pool.remove(tuple);
                    pools.get(i - 1).record(tuple, res);
                }
            }
            return res.action;
        }
        return null;
    }

    public void record(SevenTuple tuple, BiFunction<PacketFilterHelper, PacketBuffer, FilterResult> exec) {
        for (int i = 0; i < CHAIN_SIZE; ++i) {
            var pool = pools.get(i);
            if (pool.isFull()) {
                if (i < CHAIN_SIZE - 1) {
                    continue;
                }
            }
            pool.record(tuple, new ActionCache(exec));
            break;
        }
    }
}
