package io.vproxy.fstack;

import java.util.List;

public interface IFStack {
    void ff_init(List<String> args);

    void ff_run(FStackRunnable r);
}
