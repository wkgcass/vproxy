package io.vproxy.component.proxy;

import io.vproxy.base.Config;
import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.component.svrgroup.ServerGroup;
import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.connection.ServerSock;
import io.vproxy.base.util.ringbuffer.ssl.VSSLContext;
import io.vproxy.component.svrgroup.Upstream;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.util.function.BiConsumer;

public class ProxyNetConfig {
    NetEventLoop acceptLoop;
    ServerSock server;
    NetEventLoopProvider handleLoopProvider;
    ConnectorGen connGen;
    int timeout = Config.tcpTimeout;

    int inBufferSize = 128;
    int outBufferSize = 128;

    VSSLContext sslContext = null;
    BiConsumer<SSLEngine, SSLParameters> sslEngineManipulator = null;

    public ProxyNetConfig setAcceptLoop(NetEventLoop acceptLoop) {
        this.acceptLoop = acceptLoop;
        return this;
    }

    public ProxyNetConfig setServer(ServerSock server) {
        this.server = server;
        return this;
    }

    public ProxyNetConfig setHandleLoopProvider(NetEventLoopProvider handleLoopProvider) {
        this.handleLoopProvider = handleLoopProvider;
        return this;
    }

    public ProxyNetConfig setHandleLoopProvider(EventLoopGroup elg) {
        return setHandleLoopProvider(elg::next);
    }

    public ProxyNetConfig setConnGen(ConnectorGen connGen) {
        this.connGen = connGen;
        return this;
    }

    public ProxyNetConfig setConnGen(Upstream ups) {
        this.connGen = (accepted, hint) -> ups.next(accepted.getRemote(), hint);
        return this;
    }

    public ProxyNetConfig setConnGen(ServerGroup group) {
        this.connGen = (accepted, _) -> group.next(accepted.getRemote());
        return this;
    }

    public ProxyNetConfig setInBufferSize(int inBufferSize) {
        this.inBufferSize = inBufferSize;
        return this;
    }

    public ProxyNetConfig setOutBufferSize(int outBufferSize) {
        this.outBufferSize = outBufferSize;
        return this;
    }

    public ProxyNetConfig setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    public ProxyNetConfig setSslContext(VSSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    public ProxyNetConfig setSslEngineManipulator(BiConsumer<SSLEngine, SSLParameters> sslEngineManipulator) {
        this.sslEngineManipulator = sslEngineManipulator;
        return this;
    }

    public NetEventLoop getAcceptLoop() {
        return acceptLoop;
    }

    public ServerSock getServer() {
        return server;
    }

    public NetEventLoopProvider getHandleLoopProvider() {
        return handleLoopProvider;
    }

    public ConnectorGen getConnGen() {
        return connGen;
    }

    public int getInBufferSize() {
        return inBufferSize;
    }

    public int getOutBufferSize() {
        return outBufferSize;
    }

    public int getTimeout() {
        return timeout;
    }

    public VSSLContext getSslContext() {
        return sslContext;
    }

    public BiConsumer<SSLEngine, SSLParameters> getSslEngineManipulator() {
        return sslEngineManipulator;
    }
}
