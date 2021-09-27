package io.vproxy.vswitch.dispatcher;

import io.vproxy.vswitch.dispatcher.impl.KeySelectorUseQueueId;

import java.util.function.Supplier;

public enum BPFMapKeySelectors {
    useQueueId(KeySelectorUseQueueId::new),
    ;
    public final Supplier<BPFMapKeySelector> keySelector;

    BPFMapKeySelectors(Supplier<BPFMapKeySelector> keySelector) {
        this.keySelector = keySelector;
    }
}
