package io.vproxy.app.plugin.impl;

import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.Timer;
import io.vproxy.vpacket.tuples.PacketFullTuple;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.PacketFilterHelper;
import io.vproxy.vswitch.plugin.FilterResult;

import java.util.function.BiFunction;

class ActionCacheEntry {
    public final PacketFullTuple tuple;
    public final BiFunction<PacketFilterHelper, PacketBuffer, FilterResult> action;
    public final MicroFlow pool;
    private final Timer timer;

    ActionCacheEntry(PacketFullTuple tuple, BiFunction<PacketFilterHelper, PacketBuffer, FilterResult> action, MicroFlow pool) {
        this.tuple = tuple;
        this.action = action;
        this.pool = pool;
        timer = new Timer(SelectorEventLoop.current(), 60_000) {
            @Override
            public void cancel() {
                super.cancel();
                pool.cache.remove(tuple, ActionCacheEntry.this);
            }
        };
        timer.start();
    }

    public void resetTimer() {
        timer.resetTimer();
    }

    public void destroy() {
        timer.cancel();
    }
}
