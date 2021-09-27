package io.vproxy.base.util.ringbuffer.ssl;

public class SSL {
    public final SSLContextHolder sslContextHolder;
    public final SSLEngineBuilder sslEngineBuilder;

    public SSL(SSLContextHolder sslContextHolder, SSLEngineBuilder sslEngineBuilder) {
        this.sslContextHolder = sslContextHolder;
        this.sslEngineBuilder = sslEngineBuilder;
    }
}
