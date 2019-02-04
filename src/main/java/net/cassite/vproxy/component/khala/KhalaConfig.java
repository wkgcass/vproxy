package net.cassite.vproxy.component.khala;

public class KhalaConfig {
    public final int syncPeriod;

    public KhalaConfig(int syncPeriod) {
        this.syncPeriod = syncPeriod;
    }

    public KhalaConfig getDefault() {
        return new KhalaConfig(120_000);
    }
}
