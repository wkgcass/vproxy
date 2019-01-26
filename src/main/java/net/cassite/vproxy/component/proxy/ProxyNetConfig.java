package net.cassite.vproxy.component.proxy;

import net.cassite.vproxy.connection.BindServer;
import net.cassite.vproxy.connection.NetEventLoop;

public class ProxyNetConfig {
    NetEventLoop acceptLoop;
    BindServer server;
    NetEventLoopProvider handleLoopProvider;
    ConnectorGen connGen;


    int inBufferSize = 128;
    int outBufferSize = 128;

    public ProxyNetConfig setAcceptLoop(NetEventLoop acceptLoop) {
        this.acceptLoop = acceptLoop;
        return this;
    }

    public ProxyNetConfig setServer(BindServer server) {
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
}
