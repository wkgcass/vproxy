package io.vproxy.vmirror;

import io.vproxy.vfd.TapDatagramFD;

public class MirrorConfig {
    public String tapName;
    public TapDatagramFD tap;
    public int mtu;

    public MirrorConfig() {
    }
}
