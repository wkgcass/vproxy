package io.vproxy.app.plugin.impl;

import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.PacketFilterHelper;
import io.vproxy.vswitch.plugin.FilterResult;

import java.util.function.BiFunction;

class ActionCache {
    public final BiFunction<PacketFilterHelper, PacketBuffer, FilterResult> action;
    public int hit = 0;

    ActionCache(BiFunction<PacketFilterHelper, PacketBuffer, FilterResult> action) {
        this.action = action;
    }
}
