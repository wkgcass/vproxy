package vproxy.util.ringbuffer.ssl;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

public interface SSLEngineManipulator {
    void manipulate(SSLEngine engine, SSLParameters params, /*@Nullable*/String sni);
}
