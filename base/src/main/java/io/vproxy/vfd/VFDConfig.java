package io.vproxy.vfd;

import io.vproxy.base.util.OS;
import io.vproxy.base.util.Utils;

public class VFDConfig {
    private VFDConfig() {
    }

    // -Dvfd=provided
    // see FDProvider
    public static final String vfdImpl;

    // -Dvfdtrace=1
    public static final boolean vfdtrace;

    // -DVPROXY_AE_SETSIZE=131072
    // the setsize for each libae aeEventLoop; every slot pre-allocates an Att
    // object, so iOS (tight memory cap) defaults to 2048
    public static final int aesetsize;

    static {
        vfdImpl = Utils.getSystemProperty("vfd", "provided");

        String vfdtraceConf = Utils.getSystemProperty("vfd_trace", "0");
        vfdtrace = !vfdtraceConf.equals("0");

        int defaultSetsize = OS.isIOS() ? 2048 : 128 * 1024;
        String aesetsizeStr = Utils.getSystemProperty("ae_setsize", "" + defaultSetsize);
        aesetsize = Integer.parseInt(aesetsizeStr);
    }
}
