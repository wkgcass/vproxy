package io.vproxy.xdp;

import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.OS;
import io.vproxy.base.util.Utils;

public class NativeXDP {
    public static final boolean supportUMemReuse = OS.major() > 5 || (OS.major() == 5 && OS.minor() >= 10);
    public static final boolean supportTxMetadata = OS.major() > 6 || (OS.major() == 6 && OS.minor() >= 8);
    public static final boolean needUmemTxMetadataLenFlag;
    public static final int XDP_UMEM_TX_METADATA_LEN = (1 << 2);

    private static boolean isLoaded = false;

    static {
        var _needUmemTxMetadataLenFlag = false;
        if (OS.dist().equalsIgnoreCase("ubuntu")) {
            var suffix = OS.osVersionSuffix();
            var isGeneric = suffix.contains("generic");
            if (suffix.contains("-")) {
                suffix = suffix.substring(0, suffix.indexOf("-"));
            }
            double n = 0;
            try {
                n = Double.parseDouble(suffix);
            } catch (NumberFormatException ignore) {
            }
            if (isGeneric && n >= 50) { // https://git.launchpad.net/~ubuntu-kernel/ubuntu/+source/linux/+git/noble/commit/?h=Ubuntu-6.8.0-50.50
                _needUmemTxMetadataLenFlag = true;
            }
        }
        var strNeedUmemTxMetadataLenFlag = Utils.getSystemProperty("need_umem_tx_metadata_len_flag");
        if ("true".equals(strNeedUmemTxMetadataLenFlag)) {
            _needUmemTxMetadataLenFlag = true;
        } else if ("false".equals(strNeedUmemTxMetadataLenFlag)) {
            _needUmemTxMetadataLenFlag = false;
        }
        needUmemTxMetadataLenFlag = _needUmemTxMetadataLenFlag;
    }

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
