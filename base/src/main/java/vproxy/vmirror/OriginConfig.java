package vproxy.vmirror;

public class OriginConfig {
    public final MirrorConfig mirror;
    public String origin;

    public OriginConfig(MirrorConfig mirror) {
        this.mirror = mirror;
    }
}
