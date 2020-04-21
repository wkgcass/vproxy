package vswitch;

import vfd.DatagramFD;
import vfd.EventSet;
import vfd.FDProvider;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.elgroup.EventLoopGroupAttach;
import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.ClosedException;
import vproxy.component.exception.NotFoundException;
import vproxy.component.exception.XException;
import vproxy.selector.Handler;
import vproxy.selector.HandlerContext;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.*;
import vproxy.util.Timer;
import vproxy.util.crypto.Aes256Key;
import vswitch.packet.AbstractPacket;
import vswitch.packet.ArpPacket;
import vswitch.packet.VProxySwitchPacket;
import vswitch.packet.VXLanPacket;
import vswitch.util.Consts;
import vswitch.util.Iface;
import vswitch.util.MacAddress;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Switch {
    public final String alias;
    public final InetSocketAddress vxlanBindingAddress;
    public final EventLoopGroup eventLoopGroup;
    private int macTableTimeout;
    private int arpTableTimeout;

    private boolean started = false;
    private boolean wantStart = false;

    private final ConcurrentHashMap<String, Password> passwords = new ConcurrentHashMap<>();
    private final DatagramFD sock;
    private final Map<Integer, Table> tables = new HashMap<>();

    public Switch(String alias, InetSocketAddress vxlanBindingAddress, EventLoopGroup eventLoopGroup,
                  int macTableTimeout, int arpTableTimeout) throws IOException, ClosedException {
        this.alias = alias;
        this.vxlanBindingAddress = vxlanBindingAddress;
        this.eventLoopGroup = eventLoopGroup;
        this.macTableTimeout = macTableTimeout;
        this.arpTableTimeout = arpTableTimeout;

        sock = FDProvider.get().openDatagramFD();
        try {
            sock.configureBlocking(false);
            sock.bind(vxlanBindingAddress);
        } catch (IOException e) {
            releaseSock();
            throw e;
        }

        try {
            eventLoopGroup.attachResource(new SwitchEventLoopGroupAttach());
        } catch (AlreadyExistException e) {
            Logger.shouldNotHappen("attaching resource to event loop group failed");
            releaseSock();
            throw new RuntimeException(e);
        } catch (ClosedException e) {
            releaseSock();
            throw e;
        }
    }

    private void releaseSock() {
        if (sock != null) {
            try {
                sock.close();
            } catch (IOException e) {
                Logger.shouldNotHappen("closing sock " + sock + " failed", e);
            }
        }
    }

    public synchronized void start() throws IOException {
        wantStart = true;
        if (started) {
            return;
        }
        var netLoop = eventLoopGroup.next();
        if (netLoop == null) {
            return;
        }
        var loop = netLoop.getSelectorEventLoop();
        loop.add(sock, EventSet.read(), null, new PacketHandler());
        started = true;
    }

    private void restart() {
        started = false;
        try {
            start();
        } catch (IOException e) {
            Logger.error(LogType.SYS_ERROR, "starting Switch:" + alias + " failed", e);
        }
    }

    public synchronized void stop() {
        wantStart = false;
        if (!started) {
            return;
        }
        releaseSock();
        for (var tbl : tables.values()) {
            tbl.clear();
        }
        tables.clear();
        started = false;
    }

    public synchronized void destroy() {
        stop();
        releaseSock();
    }

    public int getMacTableTimeout() {
        return macTableTimeout;
    }

    public int getArpTableTimeout() {
        return arpTableTimeout;
    }

    public void setMacTableTimeout(int macTableTimeout) {
        this.macTableTimeout = macTableTimeout;
        for (var tbl : tables.values()) {
            tbl.setMacTableTimeout(macTableTimeout);
        }
    }

    public void setArpTableTimeout(int arpTableTimeout) {
        this.arpTableTimeout = arpTableTimeout;
        for (var tbl : tables.values()) {
            tbl.setArpTableTimeout(arpTableTimeout);
        }
    }

    public Map<Integer, Table> getTables() {
        return tables;
    }

    public void addUser(String user, String password) throws AlreadyExistException, XException {
        char[] chars = user.toCharArray();
        if (chars.length < 3 || chars.length > 8) {
            throw new XException("invalid user, should be at least 3 chars and at most 8 chars");
        }
        for (char c : chars) {
            if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z') && (c < '0' || c > '9')) {
                throw new XException("invalid user, should only contain a-zA-Z0-9");
            }
        }
        if (user.length() < 8) {
            user += Consts.USER_PADDING.repeat(8 - user.length());
        }

        Aes256Key key = new Aes256Key(password);
        Password old = passwords.putIfAbsent(user, new Password(key, password));
        if (old != null) {
            throw new AlreadyExistException("the user " + user + " already exists in switch " + alias);
        }
    }

    public void delUser(String user) throws NotFoundException {
        if (user.length() < 8) {
            user += Consts.USER_PADDING.repeat(8 - user.length());
        }
        Password x = passwords.remove(user);
        if (x == null) {
            throw new NotFoundException("user in switch " + alias, user);
        }
    }

    public Map<String, Password> getUsers() {
        return new LinkedHashMap<>(passwords);
    }

    private class SwitchEventLoopGroupAttach implements EventLoopGroupAttach {
        @Override
        public String id() {
            return "Switch:" + alias;
        }

        @Override
        public void onEventLoopAdd() {
            if (wantStart) {
                try {
                    start();
                } catch (Exception e) {
                    Logger.error(LogType.SYS_ERROR, "starting Switch:" + alias + " failed", e);
                }
            }
        }

        @Override
        public void onClose() {
            destroy();
        }
    }

    private Aes256Key getKey(String name) {
        var x = passwords.get(name);
        if (x == null) return null;
        return x.key;
    }

    public static class Password {
        public final Aes256Key key;
        public final String pass;

        public Password(Aes256Key key, String pass) {
            this.key = key;
            this.pass = pass;
        }
    }

    private class PacketHandler implements Handler<DatagramFD> {
        private static final int IFACE_TIMEOUT = 60 * 1000;
        private final ByteBuffer rcvBuf = ByteBuffer.allocate(2048);
        private final ByteBuffer sndBuf = ByteBuffer.allocate(2048);

        private Map<Iface, IfaceTimer> ifaces = new HashMap<>();

        @Override
        public void accept(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void connected(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void readable(HandlerContext<DatagramFD> ctx) {
            DatagramFD sock = ctx.getChannel();
            while (true) {
                rcvBuf.limit(rcvBuf.capacity()).position(0);
                InetSocketAddress remote;
                try {
                    remote = (InetSocketAddress) sock.receive(rcvBuf);
                } catch (IOException e) {
                    Logger.error(LogType.CONN_ERROR, "udp sock " + ctx.getChannel() + " got error when reading", e);
                    return;
                }
                if (rcvBuf.position() == 0) {
                    break; // nothing read, quit loop
                }
                byte[] bytes = rcvBuf.array();
                ByteArray data = ByteArray.from(bytes).sub(0, rcvBuf.position());

                var packet = new VProxySwitchPacket(Switch.this::getKey);
                String err = packet.from(data);
                if (err != null) {
                    assert Logger.lowLevelDebug("invalid packet: " + err + ", drop it");
                    continue;
                }
                Iface iface = new Iface(packet.user, remote);
                assert Logger.lowLevelDebug("got packet " + packet + " from " + iface);

                var timer = ifaces.get(iface);
                if (timer == null) {
                    timer = new IfaceTimer(ctx.getEventLoop(), IFACE_TIMEOUT, iface);
                    timer.record();
                }
                timer.resetTimer();

                if (packet.vxlan == null) {
                    if (packet.type == Consts.VPROXY_SWITCH_TYPE_PING) {
                        sendPingTo(iface);
                    }
                    continue;
                }
                int vni = packet.vxlan.vni;
                Table table = tables.get(vni);
                if (table == null) {
                    table = new Table(vni, ctx.getEventLoop(), macTableTimeout, arpTableTimeout);
                    tables.put(vni, table);
                }

                handleVxlan(packet.vxlan, table, iface);
            }
        }

        private void handleVxlan(VXLanPacket vxlan, Table table, Iface inputIface) {
            MacAddress src = vxlan.packet.getSrc();
            MacAddress dst = vxlan.packet.getDst();

            // handle layer 2
            table.macTable.record(src, inputIface);

            // handle layer 3
            AbstractPacket packet = vxlan.packet.getPacket();
            if (packet instanceof ArpPacket) {
                ArpPacket arp = (ArpPacket) packet;
                if (arp.protocolType == Consts.ARP_PROTOCOL_TYPE_IP) {
                    if (arp.opcode == Consts.ARP_PROTOCOL_OPCODE_REQ) {
                        ByteArray senderIp = arp.senderIp;
                        if (senderIp.length() == 4) {
                            // only handle ipv4 for now
                            InetAddress ip = Utils.l3addr(senderIp.toJavaArray());
                            table.arpTable.record(src, ip);
                        }
                    } else if (arp.opcode == Consts.ARP_PROTOCOL_OPCODE_RESP) {
                        ByteArray senderIp = arp.senderIp;
                        if (senderIp.length() == 4) {
                            // only handle ipv4 for now
                            InetAddress ip = Utils.l3addr(senderIp.toJavaArray());
                            table.arpTable.record(src, ip);
                        }
                    }
                }
            }

            if (dst.isBroadcast() || dst.isMulticast() /*handle multicast in the same way as broadcast*/) {
                Set<Iface> forwardBroadcastIfaces = new HashSet<>(ifaces.keySet());
                forwardBroadcastIfaces.remove(inputIface);
                for (var iface : forwardBroadcastIfaces) {
                    sendVXLanTo(iface, vxlan);
                }
            } else {
                Iface iface = table.macTable.lookup(dst);
                if (iface == null) {
                    // not found, drop
                    return;
                }
                sendVXLanTo(iface, vxlan);
            }
        }

        private void sendPingTo(Iface iface) {
            VProxySwitchPacket p = new VProxySwitchPacket(Switch.this::getKey);
            p.magic = Consts.VPROXY_SWITCH_MAGIC;
            p.type = Consts.VPROXY_SWITCH_TYPE_PING;
            sendVProxyPacketTo(iface, p);
        }

        private void sendVXLanTo(Iface iface, VXLanPacket vxlan) {
            VProxySwitchPacket p = new VProxySwitchPacket(Switch.this::getKey);
            p.magic = Consts.VPROXY_SWITCH_MAGIC;
            p.type = Consts.VPROXY_SWITCH_TYPE_VXLAN;
            p.vxlan = vxlan;
            sendVProxyPacketTo(iface, p);
        }

        private void sendVProxyPacketTo(Iface iface, VProxySwitchPacket p) {
            p.user = iface.user;
            byte[] bytes;
            try {
                bytes = p.getRawPacket().toJavaArray();
            } catch (IllegalArgumentException e) {
                assert Logger.lowLevelDebug("encode packet failed, maybe because of a deleted user: " + e);
                return;
            }
            sndBuf.limit(sndBuf.capacity()).position(0);
            sndBuf.put(bytes);
            sndBuf.flip();
            try {
                sock.send(sndBuf, iface.udpSockAddress);
            } catch (IOException e) {
                Logger.error(LogType.CONN_ERROR, "sending udp packet to " + iface.udpSockAddress + " using " + sock + " failed", e);
            }
        }

        @Override
        public void writable(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void removed(HandlerContext<DatagramFD> ctx) {
            assert Logger.lowLevelDebug("udp sock " + ctx.getChannel() + " removed from loop");
            restart();
        }

        private class IfaceTimer extends Timer {
            final Iface iface;

            public IfaceTimer(SelectorEventLoop loop, int timeout, Iface iface) {
                super(loop, timeout);
                this.iface = iface;
            }

            void record() {
                ifaces.put(iface, this);
                Logger.alert(iface + " connected to Switch:" + alias);
                resetTimer();
            }

            @Override
            public void cancel() {
                super.cancel();

                ifaces.remove(iface);

                for (var table : tables.values()) {
                    table.macTable.disconnect(iface);
                }
                Logger.warn(LogType.ALERT, iface + " disconnected from Switch:" + alias);
            }
        }
    }
}
