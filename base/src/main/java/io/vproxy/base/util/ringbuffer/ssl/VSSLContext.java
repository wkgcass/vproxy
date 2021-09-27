package io.vproxy.base.util.ringbuffer.ssl;

import javax.net.ssl.SSLContext;

public class VSSLContext {
    public final SSLContextHolder sslContextHolder;

    public VSSLContext() {
        this(new SSLContextHolder());
    }

    public VSSLContext(SSLContextHolder sslContextHolder) {
        this.sslContextHolder = sslContextHolder;
    }

    public SSL createSSL() {
        return new SSL(sslContextHolder, new SSLEngineBuilder(SSLContext::createSSLEngine));
    }
}
