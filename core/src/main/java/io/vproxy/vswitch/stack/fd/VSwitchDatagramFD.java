package io.vproxy.vswitch.stack.fd;

import io.vproxy.base.selector.wrap.VirtualFD;
import io.vproxy.base.util.ByteArray;
import io.vproxy.vfd.DatagramFD;
import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.conntrack.udp.Datagram;
import io.vproxy.vpacket.conntrack.udp.UdpListenEntry;
import io.vproxy.vpacket.conntrack.udp.UdpListenHandler;

import java.io.IOException;
import java.nio.ByteBuffer;

public class VSwitchDatagramFD extends VSwitchFD implements DatagramFD, VirtualFD {
    private IPPort connectedAddress = null;
    private UdpListenEntry udpListenEntry;
    private IPPort bindIPPort;
    private boolean isReadable = false;

    protected VSwitchDatagramFD(VSwitchFDContext ctx) {
        super(ctx);
    }

    @Override
    public void onRegister() {
        if (isReadable) {
            setReadable();
        }
        // always writable
        ctx.selector.registerVirtualWritable(this);
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
    public void onRemove() {
        // ignore
    }

    private void checkEntry() throws IOException {
        if (udpListenEntry == null) {
            throw new IOException("bind() or connect() not called");
        }
    }

    private void checkConnected() throws IOException {
        if (connectedAddress == null) {
            throw new IOException("connect() not called");
        }
    }

    @Override
    public void connect(IPPort remote) throws IOException {
        checkNotClosed();

        if (connectedAddress != null) {
            throw new IOException("already connected");
        }
        if (udpListenEntry == null) {
            // need to bind to an address
            IPPort ipport = ctx.network.findFreeUdpIPPort(remote);
            if (ipport == null) {
                throw new IOException("unable to find a free port to bind");
            }
            bind(ipport);
        }
        connectedAddress = remote;
        udpListenEntry.setOnlyReceiveFrom(remote);
    }

    @Override
    public void bind(IPPort l4addr) throws IOException {
        checkNotClosed();

        if (udpListenEntry != null) {
            throw new IOException("already bond");
        }
        if (l4addr.getAddress().isAnyLocalAddress()) {
            throw new IOException("any local address is not allowed when using VSwitchDatagramFD");
        }
        udpListenEntry = ctx.network.conntrack.listenUdp(l4addr, new ListenHandler());
        bindIPPort = udpListenEntry.listening;
    }

    @Override
    public int send(ByteBuffer buf, IPPort remote) throws IOException {
        checkNotClosed();
        checkEntry();

        int len = buf.limit() - buf.position();
        if (len == 0) {
            udpListenEntry.sendingQueue.apiSend(new Datagram(remote, ByteArray.allocate(0)));
        } else {
            byte[] array = new byte[len];
            buf.get(array);
            udpListenEntry.sendingQueue.apiSend(new Datagram(remote, ByteArray.from(array)));
        }

        ctx.L4.sendUdp(ctx.network, udpListenEntry);

        return len;
    }

    @Override
    public IPPort receive(ByteBuffer buf) throws IOException {
        checkNotClosed();
        checkEntry();

        var dg = udpListenEntry.receivingQueue.apiRecv();
        if (dg == null) {
            return null;
        }
        if (buf.limit() - buf.position() > dg.data.length()) {
            buf.put(dg.data.toJavaArray());
        } // otherwise drop the packet

        if (udpListenEntry.receivingQueue.hasMorePacketsToRead()) {
            setReadable();
        } else {
            cancelReadable();
        }
        return new IPPort(dg.remoteIp, dg.remotePort);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (udpListenEntry != null) {
            udpListenEntry.destroy();
            ctx.conntrack.removeUdpListen(udpListenEntry.listening);
            udpListenEntry = null;
        }
    }

    @Override
    public IPPort getLocalAddress() throws IOException {
        checkEntry();
        return udpListenEntry.listening;
    }

    @Override
    public IPPort getRemoteAddress() {
        return connectedAddress;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        checkNotClosed();
        checkConnected();

        int pos = dst.position();
        receive(dst);
        return dst.position() - pos;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        checkNotClosed();
        checkConnected();

        int pos = src.position();
        send(src, connectedAddress);
        return src.position() - pos;
    }

    private class ListenHandler implements UdpListenHandler {
        @Override
        public void readable(UdpListenEntry entry) {
            setReadable();
        }
    }

    @Override
    public String toString() {
        return "VSwitchDatagramFD{" +
            "connect=" + connectedAddress +
            ", bind=" + bindIPPort +
            ", closed=" + closed +
            '}';
    }
}
