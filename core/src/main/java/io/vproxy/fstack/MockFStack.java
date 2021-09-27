package io.vproxy.fstack;

import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;

import java.util.List;

public class MockFStack implements IFStack {
    @Override
    public void ff_init(List<String> args) {
        // do nothing
    }

    @Override
    public void ff_run(FStackRunnable r) {
        //noinspection InfiniteLoopStatement
        while (true) {
            try {
                r.run();
            } catch (Throwable t) {
                Logger.error(LogType.IMPROPER_USE, "exception thrown in ff_loop", t);
            }
        }
    }
}
