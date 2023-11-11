package io.vproxy.vproxyx.websocks;

import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.selector.wrap.h2streamed.H2StreamedClientFDs;
import io.vproxy.vfd.FDs;

import java.util.Collections;
import java.util.Map;

public class SharedData {
    public final ServerList.Server svr;
    public final Map<SelectorEventLoop, H2StreamedClientFDs> fds;
    public final FDs quicFDs;

    public SharedData(ServerList.Server svr, Map<SelectorEventLoop, H2StreamedClientFDs> fds, FDs quicFDs) {
        this.svr = svr;
        this.fds = Collections.unmodifiableMap(fds);
        this.quicFDs = quicFDs;
    }
}
