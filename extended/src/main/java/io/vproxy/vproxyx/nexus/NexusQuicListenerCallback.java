package io.vproxy.vproxyx.nexus;

import io.vproxy.msquic.MsQuicUtils;
import io.vproxy.msquic.QuicConnection;
import io.vproxy.msquic.QuicListenerEventNewConnection;
import io.vproxy.msquic.callback.ListenerCallback;
import io.vproxy.msquic.wrap.Listener;
import io.vproxy.pni.Allocator;

public class NexusQuicListenerCallback implements ListenerCallback {
    private final NexusContext nctx;

    public NexusQuicListenerCallback(NexusContext nctx) {
        this.nctx = nctx;
    }

    @Override
    public int newConnection(Listener listener, QuicListenerEventNewConnection data) {
        var connHQUIC = data.getConnection();
        var allocator = Allocator.ofUnsafe();
        var connQ = new QuicConnection(allocator);
        connQ.setApi(listener.opts.apiTableQ.getApi());
        connQ.setHandle(connHQUIC);

        var remote = MsQuicUtils.convertQuicAddrToIPPort(data.getInfo().getRemoteAddress());

        return NexusPeer.createAccepted(nctx, remote, connQ, listener, data, allocator);
    }
}
