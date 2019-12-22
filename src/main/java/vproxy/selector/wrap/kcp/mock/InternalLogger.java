package vproxy.selector.wrap.kcp.mock;

import vproxy.util.Logger;
import vproxy.util.Utils;

import java.util.Objects;

public class InternalLogger {
    public boolean isDebugEnabled() {
        return true;
    }

    public void debug(String fmt0, Object... args) {
        //noinspection AssertWithSideEffects
        assert Utils.debug(() -> {
            String fmt = fmt0;
            int i = 0;
            for (; i < args.length; ++i) {
                Object arg = args[i];
                if (!fmt.contains("{}")) {
                    break;
                }
                fmt = fmt.replace("{}", Objects.toString(arg));
            }
            if (i < args.length) {
                StringBuilder fmtBuilder = new StringBuilder(fmt);
                for (; i < args.length; ++i) {
                    fmtBuilder.append(" ").append(args[i]);
                }
                fmt = fmtBuilder.toString();
            }
            assert Logger.lowLevelDebug(fmt);
        });
    }
}
