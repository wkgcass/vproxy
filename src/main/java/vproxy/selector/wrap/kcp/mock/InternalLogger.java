package vproxy.selector.wrap.kcp.mock;

import vproxy.util.Logger;

import java.util.Arrays;

public class InternalLogger {
    public boolean isDebugEnabled() {
        return true;
    }

    public void debug(String fmt, Object... args) {
        assert Logger.lowLevelDebug(fmt + " " + Arrays.toString(args));
    }
}
