package io.vproxy.vfd;

import io.vproxy.base.util.Utils;

public class VFDConfig {
    private VFDConfig() {
    }

    // -Dvfd=provided
    // see FDProvider
    public static final String vfdImpl;

    // this field is generated
    public static String vfdlibname;

    // -Dvfdtrace=1
    public static final boolean vfdtrace;

    // -Daesetsize=131072
    // the setsize for each libae aeEventLoop
    public static final int aesetsize;

    static {
        vfdImpl = Utils.getSystemProperty("vfd", "provided");
        if (!vfdImpl.equals("provided")) {
            if (vfdImpl.equals("posix")) {
                vfdlibname = "vfdposix";
            } else if (vfdImpl.equals("windows")) {
                vfdlibname = "vfdwindows";
            }
        }

        String vfdtraceConf = Utils.getSystemProperty("vfd_trace", "0");
        vfdtrace = !vfdtraceConf.equals("0");

        String aesetsizeStr = Utils.getSystemProperty("ae_setsize", "" + (128 * 1024));
        aesetsize = Integer.parseInt(aesetsizeStr);
    }
}
