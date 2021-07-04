package vproxy.vswitch.dispatcher;

import vproxy.vswitch.dispatcher.impl.NormalKeySelector;

import java.util.function.Supplier;

public enum BPFMapKeySelectors {
    normal(NormalKeySelector::new),
    ;
    public final Supplier<BPFMapKeySelector> keySelector;

    BPFMapKeySelectors(Supplier<BPFMapKeySelector> keySelector) {
        this.keySelector = keySelector;
    }
}
