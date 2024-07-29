package io.vproxy.xdp;

import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.OS;
import io.vproxy.base.util.Utils;

public class NativeXDP {
    public static final boolean supportUMemReuse = OS.major() > 5 || (OS.major() == 5 && OS.minor() >= 10);
    public static final boolean supportTxMetadata = OS.major() > 6 || (OS.major() == 6 && OS.minor() >= 8);

    public static final int VP_XSK_FLAG_RX_GEN_CSUM = 1;

    private static boolean isLoaded = false;

    // must be called at every entrypoint related to xdp
    // currently: 1)bpfObject 2)umem
    public static void load() {
        if (isLoaded) {
            return;
        }
        synchronized (NativeXDP.class) {
            if (isLoaded) {
                return;
            }
            doLoad();
            isLoaded = true;
        }
    }

    private static void doLoad() {
        try {
            Utils.loadDynamicLibrary("elf");
        } catch (UnsatisfiedLinkError e) {
            Logger.error(LogType.SYS_ERROR, "unable to load libelf, you may need to add startup argument -Djava.library.path=/usr/lib/`uname -m`-linux-gnu");
            throw e;
        }
        Utils.loadDynamicLibrary("xdp");
        Utils.loadDynamicLibrary("vpxdp");
    }
}
