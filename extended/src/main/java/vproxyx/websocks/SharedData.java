package vproxyx.websocks;

import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.selector.wrap.h2streamed.H2StreamedClientFDs;

import java.util.Collections;
import java.util.Map;

public class SharedData {
    public final boolean useSSL;
    public final boolean useKCP;
    public final Map<SelectorEventLoop, H2StreamedClientFDs> fds;

    public SharedData(boolean useSSL, boolean useKCP, Map<SelectorEventLoop, H2StreamedClientFDs> fds) {
        this.useSSL = useSSL;
        this.useKCP = useKCP;
        this.fds = Collections.unmodifiableMap(fds);
    }
}
