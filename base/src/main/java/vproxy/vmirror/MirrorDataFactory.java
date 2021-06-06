package vproxy.vmirror;

import java.util.function.Consumer;

public class MirrorDataFactory {
    private final MirrorContext ctx;
    private final String origin;
    private final Consumer<MirrorData> addressSetter;

    public MirrorDataFactory(String origin, Consumer<MirrorData> addressSetter) {
        this.origin = origin;
        this.ctx = new MirrorContext();
        this.addressSetter = addressSetter;
    }

    public boolean isEnabled() {
        return Mirror.isEnabled(origin);
    }

    public MirrorData build() {
        MirrorData d = new MirrorData(ctx, origin);
        addressSetter.accept(d);
        return d;
    }
}
