package vproxy.vswitch.stack.fd;

import vproxy.vfd.IPPort;
import vproxy.vfd.ServerSocketFD;
import vproxy.vfd.SocketFD;
import vproxy.vfd.type.FDCloseReq;
import vproxy.vfd.type.FDCloseReturn;
import vproxy.vpacket.conntrack.tcp.ListenEntry;
import vproxy.vpacket.conntrack.tcp.TcpEntry;
import vproxy.vpacket.conntrack.tcp.TcpUtils;
import vproxy.vswitch.stack.OutputPacketL3Context;

import java.io.IOException;

public class VSwitchServerSocketFD extends VSwitchFD implements ServerSocketFD {
    private IPPort local;
    private ListenEntry entry;

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
        var lsnCtx = ctx.conntrack.lookupListen(l4addr);
        if (lsnCtx != null) {
            throw new IOException("already listening on " + l4addr);
        }
        this.entry = ctx.conntrack.listen(l4addr, new ListenHandler());
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
        FDCloseReq.inst().wrapClose(this::close);
    }

    @Override
    public FDCloseReturn close(FDCloseReq req) {
        if (closed) {
            return FDCloseReturn.nothing(req);
        }
        closed = true;

        cancelReadable();
        if (entry == null) {
            return FDCloseReturn.nothing(req);
        }
        for (var e : entry.synBacklog) {
            ctx.L4.output(new OutputPacketL3Context(
                getUUID(), ctx.table,
                TcpUtils.buildIpResponse(e, TcpUtils.buildRstResponse(e))
            ));
        }
        for (var e : entry.backlog) {
            ctx.L4.output(new OutputPacketL3Context(
                getUUID(), ctx.table,
                TcpUtils.buildIpResponse(e, TcpUtils.buildRstResponse(e))
            ));
        }
        ctx.conntrack.removeListen(local);
        entry.destroy();
        return FDCloseReturn.nothing(req);
    }

    private class ListenHandler implements vproxy.vpacket.conntrack.tcp.ListenHandler {
        @Override
        public void readable(ListenEntry entry) {
            setReadable();
        }
    }
}
