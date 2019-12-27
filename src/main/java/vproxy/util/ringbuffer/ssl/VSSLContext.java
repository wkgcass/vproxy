package vproxy.util.ringbuffer.ssl;

import javax.net.ssl.SSLContext;

public class VSSLContext {
    public final SSLContextHolder sslContextHolder;
    public final SSLEngineBuilder sslEngineBuilder;

    public VSSLContext() {
        this(new SSLContextHolder(), new SSLEngineBuilder(SSLContext::createSSLEngine));
    }

    public VSSLContext(SSLContextHolder sslContextHolder, SSLEngineBuilder sslEngineBuilder) {
        this.sslContextHolder = sslContextHolder;
        this.sslEngineBuilder = sslEngineBuilder;
    }
}
