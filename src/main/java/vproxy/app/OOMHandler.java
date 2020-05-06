package vproxy.app;

import vproxy.util.LogType;
import vproxy.util.Logger;
import vproxy.util.Utils;

public class OOMHandler {
    private static byte[] _512K;
    private static byte[] _512K_2;

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
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(60_000);
                    byte[] b = _512K;
                    _512K = new byte[512 * 1024];
                    _512K_2 = b;
                } catch (OutOfMemoryError e) {
                    Logger.fatal(LogType.ALERT, "OOM occurred");
                    break;
                } catch (Throwable t) {
                    Logger.shouldNotHappen("oom-handler got exception", t);
                }
            }
            Utils.exit(137);
        }, "oom-handler").start();
    }
}
