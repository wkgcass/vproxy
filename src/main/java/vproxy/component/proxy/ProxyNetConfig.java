package vproxy.component.proxy;

import vproxy.app.Config;
import vproxy.connection.NetEventLoop;
import vproxy.connection.ServerSock;

import javax.net.ssl.SSLContext;

public class ProxyNetConfig {
    NetEventLoop acceptLoop;
    ServerSock server;
    NetEventLoopProvider handleLoopProvider;
    ConnectorGen connGen;
    int timeout = Config.tcpTimeout;

    int inBufferSize = 128;
    int outBufferSize = 128;

    SSLContext sslContext = null;

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

    public ProxyNetConfig setConnGen(ConnectorGen connGen) {
        this.connGen = connGen;
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

    public ProxyNetConfig setSslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
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

    public SSLContext getSslContext() {
        return sslContext;
    }
}
