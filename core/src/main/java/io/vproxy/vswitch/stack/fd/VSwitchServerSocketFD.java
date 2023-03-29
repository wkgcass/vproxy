package io.vproxy.vswitch.stack.fd;

import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.ServerSocketFD;
import io.vproxy.vfd.SocketFD;
import io.vproxy.vpacket.conntrack.tcp.TcpEntry;
import io.vproxy.vpacket.conntrack.tcp.TcpListenEntry;
import io.vproxy.vpacket.conntrack.tcp.TcpListenHandler;
import io.vproxy.vpacket.conntrack.tcp.TcpUtils;
import io.vproxy.vswitch.PacketBuffer;

import java.io.IOException;

public class VSwitchServerSocketFD extends VSwitchFD implements ServerSocketFD {
    private IPPort local;
    private TcpListenEntry entry;

    private boolean isReadable = false;

    public VSwitchServerSocketFD(VSwitchFDContext ctx) {
        super(ctx);
    }

    private void checkEntry() throws IOException {
        if (entry == null) {
            throw new IOException("bind() not called");
        }
    }

    private void setReadable() {
        isReadable = true;
        ctx.selector.registerVirtualReadable(this);
    }

    private void cancelReadable() {
        isReadable = false;
        ctx.selector.removeVirtualReadable(this);
    }

    @Override
    public IPPort getLocalAddress() {
        return local;
    }

    @Override
    public SocketFD accept() throws IOException {
        checkEntry();
        checkNotClosed();
        if (entry.backlog.isEmpty()) {
            cancelReadable();
            return null;
        }
        TcpEntry tcp = entry.backlog.pollFirst();
        if (entry.backlog.isEmpty()) {
            cancelReadable();
        }

        return new VSwitchSocketFD(ctx, tcp);
    }

    @Override
    public void bind(IPPort l4addr) throws IOException {
        checkNotClosed();
        if (local != null) {
            throw new IOException("already bond " + local);
        }
        var lsnCtx = ctx.conntrack.lookupTcpListen(l4addr);
        if (lsnCtx != null) {
            throw new IOException("already listening on " + l4addr);
        }
        this.entry = ctx.conntrack.listenTcp(l4addr, new ListenHandler());
        this.local = l4addr;
    }

    @Override
    public void onRegister() {
        if (isReadable) {
            setReadable();
        }
    }

    @Override
    public void onRemove() {
        // ignore
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        cancelReadable();
        if (entry == null) {
            return;
        }
        for (var e : entry.synBacklog) {
            PacketBuffer pkb = PacketBuffer.fromPacket(ctx.network, TcpUtils.buildIpResponse(e, TcpUtils.buildRstResponse(e)));
            ctx.tcpStack.output(pkb);
        }
        for (var e : entry.backlog) {
            PacketBuffer pkb = PacketBuffer.fromPacket(ctx.network, TcpUtils.buildIpResponse(e, TcpUtils.buildRstResponse(e)));
            ctx.tcpStack.output(pkb);
        }
        ctx.conntrack.removeTcpListen(local);
        entry.destroy();
    }

    private class ListenHandler implements TcpListenHandler {
        @Override
        public void readable(TcpListenEntry entry) {
            setReadable();
        }
    }
}
