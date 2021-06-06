package vproxy.app.app;

import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.base.util.Utils;
import vproxy.base.util.thread.VProxyThread;

public class OOMHandler {
    private static byte[] _512K;
    private static byte[] _512K_2;
    private static VProxyThread oomThread;
    private static volatile boolean stop = false;

    private OOMHandler() {
    }

    @SuppressWarnings("unused")
    public byte[] get_512k() {
        return _512K;
    }

    @SuppressWarnings("unused")
    public byte[] get_512k2() {
        return _512K_2;
    }

    public static void handleOOM() {
        if (oomThread != null) {
            return;
        }
        oomThread = VProxyThread.create(() -> {
            while (true) {
                if (stop) {
                    return;
                }
                try {
                    Thread.sleep(60_000);
                    byte[] b = _512K;
                    _512K = Utils.allocateByteArray(512 * 1024);
                    _512K_2 = b;
                } catch (OutOfMemoryError e) {
                    Logger.fatal(LogType.ALERT, "OOM occurred");
                    break;
                } catch (Throwable t) {
                    Logger.shouldNotHappen("oom-handler got exception", t);
                }
            }
            Utils.exit(137);
        }, "oom-handler");
        oomThread.start();
    }

    public static void stop() {
        stop = true;
        if (oomThread != null) {
            oomThread.interrupt();
            oomThread = null;
        }
    }
}
