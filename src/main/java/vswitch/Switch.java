package vswitch;

import vfd.DatagramFD;
import vfd.EventSet;
import vfd.FDProvider;
import vfd.FDs;
import vfd.posix.PosixFDs;
import vfd.posix.TunTapDatagramFD;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.elgroup.EventLoopGroupAttach;
import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.ClosedException;
import vproxy.component.exception.NotFoundException;
import vproxy.component.exception.XException;
import vproxy.component.secure.SecurityGroup;
import vproxy.connection.NetEventLoop;
import vproxy.connection.Protocol;
import vproxy.selector.Handler;
import vproxy.selector.HandlerContext;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.*;
import vproxy.util.Timer;
import vproxy.util.crypto.Aes256Key;
import vswitch.packet.*;
import vswitch.util.Consts;
import vswitch.util.Iface;
import vswitch.util.MacAddress;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Switch {
    public final String alias;
    public final InetSocketAddress vxlanBindingAddress;
    public final EventLoopGroup eventLoopGroup;
    private NetEventLoop currentEventLoop;
    private int macTableTimeout;
    private int arpTableTimeout;
    public SecurityGroup bareVXLanAccess;

    private boolean started = false;
    private boolean wantStart = false;

    private final ConcurrentHashMap<String, UserInfo> users = new ConcurrentHashMap<>();
    private final DatagramFD sock;
    private final Map<Integer, Table> tables = new ConcurrentHashMap<>();
    private final Map<Iface, IfaceTimer> ifaces = new HashMap<>();

    public Switch(String alias, InetSocketAddress vxlanBindingAddress, EventLoopGroup eventLoopGroup,
                  int macTableTimeout, int arpTableTimeout, SecurityGroup bareVXLanAccess) throws IOException, ClosedException {
        this.alias = alias;
        this.vxlanBindingAddress = vxlanBindingAddress;
        this.eventLoopGroup = eventLoopGroup;
        this.macTableTimeout = macTableTimeout;
        this.arpTableTimeout = arpTableTimeout;
        this.bareVXLanAccess = bareVXLanAccess;

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
        currentEventLoop = netLoop;
        tables.values().forEach(t -> t.setLoop(loop));
        started = true;
    }

    private void checkAndRestart() { // this method is only called when selected event loop closes, so no need to remove handler
        started = false;
        currentEventLoop = null;
        cancelAllIface();
        if (!wantStart) {
            return;
        }
        try {
            start();
        } catch (IOException e) {
            Logger.error(LogType.SYS_ERROR, "starting Switch:" + alias + " failed", e);
        }
    }

    private void cancelAllIface() {
        var set = Set.copyOf(ifaces.values());
        set.forEach(IfaceTimer::cancel);
    }

    public synchronized void stop() {
        wantStart = false;
        if (!started) {
            return;
        }
        currentEventLoop = null;
        cancelAllIface();
        for (var tbl : tables.values()) {
            tbl.clearCache();
        }
        started = false;
    }

    public synchronized void destroy() {
        wantStart = false;
        releaseSock();
        stop();
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

    public Table getTable(int vni) throws NotFoundException {
        Table t = tables.get(vni);
        if (t == null) {
            throw new NotFoundException("vni", "" + vni);
        }
        return t;
    }

    public void addTable(int vni, Network v4network, Network v6network) throws AlreadyExistException, XException {
        if (tables.containsKey(vni)) {
            throw new AlreadyExistException("vni " + vni + " already exists in switch " + alias);
        }
        if (currentEventLoop == null) {
            throw new XException("the switch " + alias + " is not bond to any event loop, cannot add vni");
        }
        tables.computeIfAbsent(vni, n -> new Table(n, currentEventLoop.getSelectorEventLoop(), v4network, v6network, macTableTimeout, arpTableTimeout));
    }

    public void delTable(int vni) throws NotFoundException {
        Table t = tables.remove(vni);
        if (t == null) {
            throw new NotFoundException("vni", "" + vni);
        }
        t.clearCache();
    }

    public List<Iface> getIfaces() {
        return new ArrayList<>(ifaces.keySet());
    }

    public void addUser(String user, String password, int vni) throws AlreadyExistException, XException {
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
        UserInfo old = users.putIfAbsent(user, new UserInfo(user, key, password, vni));
        if (old != null) {
            throw new AlreadyExistException("the user " + user + " already exists in switch " + alias);
        }
    }

    public void delUser(String user) throws NotFoundException {
        if (user.length() < 8) {
            user += Consts.USER_PADDING.repeat(8 - user.length());
        }
        UserInfo x = users.remove(user);
        if (x == null) {
            throw new NotFoundException("user in switch " + alias, user);
        }
    }

    // return created dev name
    public String addTap(String devPattern, int vni) throws XException, IOException {
        NetEventLoop netEventLoop = currentEventLoop;
        if (netEventLoop == null) {
            throw new XException("the switch " + alias + " is not bond to any event loop, cannot add tap device");
        }
        SelectorEventLoop loop = netEventLoop.getSelectorEventLoop();

        FDs fds = FDProvider.get().getProvided();
        if (!(fds instanceof PosixFDs)) {
            throw new IOException("tap is not supported by " + fds + ", use -Dvfd=posix");
        }
        PosixFDs posixFDs = (PosixFDs) fds;
        TunTapDatagramFD fd = posixFDs.openTunTap(devPattern, TunTapDatagramFD.IFF_TAP | TunTapDatagramFD.IFF_NO_PI);
        Iface iface = new Iface(fd);
        iface.serverSideVni = vni; // assign the vni
        try {
            fd.configureBlocking(false);
            loop.add(fd, EventSet.read(), null, new TapHandler(iface));
        } catch (IOException e) {
            try {
                fd.close();
            } catch (IOException t) {
                Logger.shouldNotHappen("failed to close the tap device when rolling back the creation", t);
            }
            throw e;
        }
        loop.runOnLoop(() -> ifaces.put(iface, new IfaceTimer(loop, -1, iface)));
        Logger.alert("tap device added: " + fd.tuntap.dev);
        return fd.tuntap.dev;
    }

    public void delTap(String devName) throws NotFoundException {
        Iface iface = null;
        for (Iface i : ifaces.keySet()) {
            if (i.tap != null && i.tap.tuntap.dev.equals(devName)) {
                iface = i;
                break;
            }
        }
        if (iface == null) {
            throw new NotFoundException("tap", devName);
        }
        utilRemoveIface(iface);
        NetEventLoop netEventLoop = currentEventLoop;
        if (netEventLoop != null) {
            netEventLoop.getSelectorEventLoop().remove(iface.tap);
        }
        try {
            iface.tap.close();
        } catch (IOException e) {
            Logger.shouldNotHappen("closing tap device failed", e);
        }
    }

    public Map<String, UserInfo> getUsers() {
        var ret = new LinkedHashMap<String, UserInfo>();
        for (var entry : users.entrySet()) {
            ret.put(entry.getKey().replace(Consts.USER_PADDING, ""), entry.getValue());
        }
        return ret;
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
        var x = users.get(name);
        if (x == null) return null;
        return x.key;
    }

    public static class UserInfo {
        public final String user;
        public final Aes256Key key;
        public final String pass;
        public final int vni;

        public UserInfo(String user, Aes256Key key, String pass, int vni) {
            this.user = user;
            this.key = key;
            this.pass = pass;
            this.vni = vni;
        }
    }

    private abstract class NetworkStack {
        private final ByteBuffer sndBuf = ByteBuffer.allocate(2048);

        protected NetworkStack() {
        }

        protected void sendIntoNetworkStack(String logId, VXLanPacket vxlan, Iface iface) {
            int vni = vxlan.getVni();
            Table table = tables.get(vni);
            if (table == null) {
                assert Logger.lowLevelDebug(logId + "vni not defined: " + vni);
                return;
            }

            recordAndHandleVxlan(logId, vxlan, table, iface);
        }

        private void recordAndHandleVxlan(String logId, VXLanPacket vxlan, Table table, Iface inputIface) {
            assert Logger.lowLevelDebug(logId + "into recordAndHandleVxlan(" + vxlan + "," + table + "," + inputIface + ")");

            MacAddress src = vxlan.getPacket().getSrc();

            // handle layer 2
            table.macTable.record(src, inputIface);

            // handle layer 3
            AbstractPacket packet = vxlan.getPacket().getPacket();
            if (packet instanceof ArpPacket) {
                assert Logger.lowLevelDebug(logId + "is arp packet");
                ArpPacket arp = (ArpPacket) packet;
                if (arp.getProtocolType() == Consts.ARP_PROTOCOL_TYPE_IP) {
                    assert Logger.lowLevelDebug(logId + "arp protocol is ip");
                    if (arp.getOpcode() == Consts.ARP_PROTOCOL_OPCODE_REQ) {
                        assert Logger.lowLevelDebug(logId + "arp is req");
                        ByteArray senderIp = arp.getSenderIp();
                        if (senderIp.length() == 4) {
                            assert Logger.lowLevelDebug(logId + "arp sender is ipv4");
                            // only handle ipv4 in arp, v6 should be handled with ndp
                            InetAddress ip = Utils.l3addr(senderIp.toJavaArray());
                            if (!table.v4network.contains(ip)) {
                                assert Logger.lowLevelDebug(logId + "got arp packet not allowed in the network: " + ip + " not in " + table.v4network);
                                return;
                            }
                            table.arpTable.record(src, ip);
                        }
                    } else if (arp.getOpcode() == Consts.ARP_PROTOCOL_OPCODE_RESP) {
                        assert Logger.lowLevelDebug(logId + "arp is resp");
                        ByteArray senderIp = arp.getSenderIp();
                        if (senderIp.length() == 4) {
                            // only handle ipv4 for now
                            InetAddress ip = Utils.l3addr(senderIp.toJavaArray());
                            if (!table.v4network.contains(ip)) {
                                assert Logger.lowLevelDebug(logId + "got arp packet not allowed in the network: " + ip + " not in " + table.v4network);
                                return;
                            }
                            table.arpTable.record(src, ip);
                        }
                    }
                }
            } else if (packet instanceof AbstractIpPacket) {
                assert Logger.lowLevelDebug(logId + "is ip packet");
                var ipPkt = (AbstractIpPacket) packet;
                if (ipPkt.getPacket() instanceof IcmpPacket) {
                    assert Logger.lowLevelDebug(logId + "is icmp packet");
                    var icmp = (IcmpPacket) ipPkt.getPacket();
                    if (icmp.getType() == Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation
                        ||
                        icmp.getType() == Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Advertisement) {
                        assert Logger.lowLevelDebug(logId + "is ndp");
                        var other = icmp.getOther();
                        if (other.length() >= 28) { // 4 reserved and 16 target address and 8 option
                            assert Logger.lowLevelDebug(logId + "ndp length is ok");
                            var targetIp = other.sub(4, 16);
                            var optType = other.uint8(20);
                            var optLen = other.uint8(21);
                            if (optLen == 1) {
                                assert Logger.lowLevelDebug(logId + "ndp optLen == 1");
                                var mac = new MacAddress(other.sub(22, 6));
                                if (optType == Consts.ICMPv6_OPTION_TYPE_Source_Link_Layer_Address) {
                                    assert Logger.lowLevelDebug(logId + "ndp has opt source link layer address");
                                    // mac is the sender's mac, record with src ip in ip packet
                                    InetAddress ip = ipPkt.getSrc();
                                    if (table.v6network == null || !table.v6network.contains(ip)) {
                                        assert Logger.lowLevelDebug(logId + "got ndp packet not allowed in the network: " + ip + " not in " + table.v6network);
                                        return;
                                    }
                                    table.arpTable.record(mac, ip);
                                } else {
                                    assert Logger.lowLevelDebug(logId + "ndp has opt target link layer address");
                                    // mac is the target's mac, record with target ip in icmp packet
                                    InetAddress ip = Utils.l3addr(targetIp.toJavaArray());
                                    if (table.v6network == null || !table.v6network.contains(ip)) {
                                        assert Logger.lowLevelDebug(logId + "got ndp packet not allowed in the network: " + ip + " not in " + table.v6network);
                                        return;
                                    }
                                    table.arpTable.record(mac, ip);
                                }
                            }
                        }
                    }
                }
            }

            handleVxlan(logId, vxlan, table, inputIface);
        }

        private void handleVxlan(String logId, VXLanPacket vxlan, Table table, Iface inputIface) {
            assert Logger.lowLevelDebug(logId + "into handleVxlan(" + vxlan + "," + table + "," + inputIface + ")");

            MacAddress dst = vxlan.getPacket().getDst();

            if (dst.isBroadcast() || dst.isMulticast() /*handle multicast in the same way as broadcast*/) {
                assert Logger.lowLevelDebug(logId + "broadcast or multicast");
                handleVirtual(logId, table, table.ips.allIps(), vxlan, inputIface, true);
                broadcastVXLan(logId, table, vxlan);
            } else {
                assert Logger.lowLevelDebug(logId + "unicast");
                Iface iface = table.macTable.lookup(dst);
                if (iface == null) {
                    assert Logger.lowLevelDebug(logId + "connected iface for this packet is not found");
                    // not found, try synthetic or otherwise drop
                    var ips = table.ips.lookupByMac(dst);
                    if (ips != null) {
                        assert Logger.lowLevelDebug(logId + "synthetic ip found for this packet");
                        handleVirtual(logId, table, ips, vxlan, inputIface, false);
                    }
                    return;
                }
                sendVXLanTo(logId, iface, vxlan);
            }
        }

        private void handleVirtual(String logId, Table table, Collection<InetAddress> ips, VXLanPacket vxlan, Iface inputIface, boolean allReceives) {
            assert Logger.lowLevelDebug(logId + "into handleVirtual(" + table + "," + ips + "," + vxlan + "," + inputIface + ")");

            // analyse the packet
            AbstractPacket l3Packet = vxlan.getPacket().getPacket();
            ArpPacket arp = null;
            InetAddress arpReq = null;
            AbstractIpPacket ipPkt = null;
            IcmpPacket icmp = null;
            Inet6Address ndpNeighborSolicitation = null;
            if (l3Packet instanceof ArpPacket) {
                assert Logger.lowLevelDebug(logId + "is arp packet");
                arp = (ArpPacket) l3Packet;
                if (arp.getOpcode() == Consts.ARP_PROTOCOL_OPCODE_REQ) {
                    assert Logger.lowLevelDebug(logId + "is arp req");
                    byte[] targetIpBytes = arp.getTargetIp().toJavaArray();
                    if (targetIpBytes.length == 4 || targetIpBytes.length == 16) {
                        assert Logger.lowLevelDebug(logId + "target protocol address might be an ip address");
                        arpReq = Utils.l3addr(targetIpBytes);
                    }
                }
            } else if (l3Packet instanceof AbstractIpPacket) {
                assert Logger.lowLevelDebug(logId + "is ip packet");
                ipPkt = (AbstractIpPacket) l3Packet;
                var pkt = ipPkt.getPacket();
                if (pkt instanceof IcmpPacket) {
                    assert Logger.lowLevelDebug(logId + "is icmp");
                    icmp = (IcmpPacket) pkt;
                    if (icmp.getType() == Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation) {
                        assert Logger.lowLevelDebug(logId + "is icmpv6 neighbor solicitation");
                        ByteArray other = icmp.getOther();
                        if (other.length() < 20) { // 4 reserved and 16 target address
                            assert Logger.lowLevelDebug(logId + "invalid packet for neighbor solicitation: too short");
                        } else {
                            assert Logger.lowLevelDebug(logId + "is a valid neighbor solicitation");
                            byte[] targetAddr = other.sub(4, 16).toJavaArray();
                            ndpNeighborSolicitation = (Inet6Address) Utils.l3addr(targetAddr);
                        }
                    }
                }
            }

            // handle
            boolean handled = false;
            for (InetAddress ip : ips) {
                assert Logger.lowLevelDebug(logId + "handle synthetic ip " + ip);

                MacAddress mac = table.ips.lookup(ip);
                // check l2
                if (!allReceives && !mac.equals(vxlan.getPacket().getDst())) {
                    assert Logger.lowLevelDebug(logId + "is unicast and mac address not match");
                    continue;
                }
                // check l3
                if (arpReq != null) {
                    assert Logger.lowLevelDebug(logId + "check arp");
                    if (ip.equals(arpReq)) {
                        assert Logger.lowLevelDebug(logId + "arp target address matches");
                        // should respond arp
                        handleArp(logId, vxlan, arp, ip, mac, inputIface);
                        handled = true;
                        if (!allReceives) break;
                    }
                } else if (ipPkt != null) {
                    assert Logger.lowLevelDebug(logId + "check ip");
                    var dstIp = ipPkt.getDst();
                    if (allReceives || dstIp.equals(ip)) {
                        assert Logger.lowLevelDebug(logId + "is broadcast or multicast or unicast and ip matches");
                        if (icmp != null) {
                            assert Logger.lowLevelDebug(logId + "check icmp");
                            if (ndpNeighborSolicitation != null && ndpNeighborSolicitation.equals(ip)) {
                                assert Logger.lowLevelDebug(logId + "ndp neighbor solicitation ip matches");
                                handleIcmpNDP(logId, vxlan, ipPkt, icmp, ip, mac, inputIface);
                                handled = true;
                                if (!allReceives) break;
                            } else {
                                assert Logger.lowLevelDebug(logId + "not ndp neighbor solicitation");
                                boolean shouldHandlePing;
                                if (icmp.isIpv6()) {
                                    assert Logger.lowLevelDebug(logId + "is icmpv6");
                                    shouldHandlePing = icmp.getType() == Consts.ICMPv6_PROTOCOL_TYPE_ECHO_REQ;
                                } else {
                                    assert Logger.lowLevelDebug(logId + "is icmp");
                                    shouldHandlePing = icmp.getType() == Consts.ICMP_PROTOCOL_TYPE_ECHO_REQ;
                                }
                                if (shouldHandlePing) {
                                    assert Logger.lowLevelDebug(logId + "handle ping");
                                    handleIcmpPing(logId, vxlan, ipPkt, icmp, ip, mac, inputIface);
                                    handled = true;
                                    if (!allReceives) break;
                                }
                            }
                        }
                    }
                }
                // otherwise ignore
            }
            // handle routes when the packet not handled by others
            if (!handled) {
                assert Logger.lowLevelDebug(logId + "not yet handled");
                if (!allReceives) { // only handle unicast
                    assert Logger.lowLevelDebug(logId + "is unicast");
                    if (ipPkt != null) { // only check ip packets here
                        assert Logger.lowLevelDebug(logId + "is ip packet");
                        int hop = ipPkt.getHopLimit();
                        if (hop != 0) { // do not route packets when hop reaches 0
                            assert Logger.lowLevelDebug(logId + "ip hop is ok");
                            ipPkt.setHopLimit(hop - 1);
                            // clear upper level cached packet
                            vxlan.getPacket().clearRawPacket();
                            vxlan.clearRawPacket();

                            var dstIp = ipPkt.getDst();
                            RouteTable.RouteRule r = table.routeTable.lookup(dstIp);
                            if (r != null) {
                                assert Logger.lowLevelDebug(logId + "route rule found");
                                int vni = r.toVni;
                                if (vni != 0) { // check the vni number, for further extension
                                    assert Logger.lowLevelDebug(logId + "vni in rule is ok");
                                    Table t = tables.get(vni);
                                    if (t != null) { // cannot handle if the table does no exist
                                        assert Logger.lowLevelDebug(logId + "target table is found");
                                        routeTo(logId, t, dstIp, vxlan, inputIface);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        private void routeTo(String logId, Table table, InetAddress dstIp, VXLanPacket vxlan, Iface inputIface) {
            assert Logger.lowLevelDebug(logId + "into routeTo(" + table + "," + dstIp + "," + vxlan + "," + inputIface + ")");

            // find the mac of the target address
            MacAddress dstMac = table.arpTable.lookup(dstIp);
            if (dstMac == null) {
                assert Logger.lowLevelDebug(logId + "dst mac is not found by the ip");
                if (dstIp instanceof Inet4Address) {
                    broadcastArp(logId, table, dstIp);
                } else {
                    broadcastNdp(logId, table, dstIp);
                }
                return;
            }
            // find an ip in that table to be used for the mac address
            var ipsInTable = table.ips.entries();
            boolean useIpv6 = dstIp instanceof Inet6Address;
            MacAddress srcMac = null;
            for (var x : ipsInTable) {
                if (useIpv6 && x.getKey() instanceof Inet6Address) {
                    srcMac = x.getValue();
                    break;
                }
                if (!useIpv6 && x.getKey() instanceof Inet4Address) {
                    srcMac = x.getValue();
                    break;
                }
            }
            // handle if the address exists
            if (srcMac == null) {
                assert Logger.lowLevelDebug(logId + "the source mac to send the packet is not found");
                return;
            }
            vxlan.getPacket().setSrc(srcMac);
            vxlan.getPacket().setDst(dstMac);

            // clear upper level cached packet
            vxlan.clearRawPacket();

            handleVxlan(logId, vxlan, table, inputIface);
        }

        private void broadcastArp(String logId, Table table, InetAddress ip) {
            assert Logger.lowLevelDebug(logId + "broadcastArp(" + logId + "," + table + "," + ip + ")");

            var optIp = table.ips.entries().stream().filter(x -> x.getKey() instanceof Inet4Address).findAny();
            if (optIp.isEmpty()) {
                assert Logger.lowLevelDebug(logId + "cannot find synthetic ipv4 in the table");
                return;
            }
            InetAddress reqIp = optIp.get().getKey();
            MacAddress reqMac = optIp.get().getValue();

            ArpPacket req = new ArpPacket();
            req.setHardwareType(Consts.ARP_HARDWARE_TYPE_ETHER);
            req.setProtocolType(Consts.ARP_PROTOCOL_TYPE_IP);
            req.setHardwareSize(Consts.ARP_HARDWARE_TYPE_ETHER);
            req.setProtocolSize(Consts.ARP_PROTOCOL_TYPE_IP);
            req.setOpcode(Consts.ARP_PROTOCOL_OPCODE_REQ);
            req.setSenderMac(reqMac.bytes);
            req.setSenderIp(ByteArray.from(reqIp.getAddress()));
            req.setTargetMac(ByteArray.allocate(6));
            req.setTargetIp(ByteArray.from(ip.getAddress()));

            EthernetPacket ether = new EthernetPacket();
            ether.setDst(new MacAddress("ff:ff:ff:ff:ff:ff"));
            ether.setSrc(reqMac);
            ether.setType(Consts.ETHER_TYPE_ARP);
            ether.setPacket(req);

            VXLanPacket vxlan = new VXLanPacket();
            vxlan.setFlags(0b00001000);
            vxlan.setVni(table.vni);
            vxlan.setPacket(ether);

            broadcastVXLan(logId, table, vxlan);
        }

        private void broadcastNdp(String logId, Table table, InetAddress ip) {
            assert Logger.lowLevelDebug(logId + "broadcastNdp(" + logId + "," + table + "," + ip + ")");

            var optIp = table.ips.entries().stream().filter(x -> x.getKey() instanceof Inet6Address).findAny();
            if (optIp.isEmpty()) {
                assert Logger.lowLevelDebug(logId + "cannot find synthetic ipv4 in the table");
                return;
            }
            InetAddress reqIp = optIp.get().getKey();
            MacAddress reqMac = optIp.get().getValue();

            IcmpPacket icmp = new IcmpPacket(true);
            icmp.setType(Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation);
            icmp.setCode(0);
            icmp.setOther(
                (ByteArray.allocate(4).set(0, (byte) 0)).concat(ByteArray.from(ip.getAddress()))
                    .concat(( // the source link-layer address
                        ByteArray.allocate(1 + 1).set(0, (byte) Consts.ICMPv6_OPTION_TYPE_Source_Link_Layer_Address)
                            .set(1, (byte) 1) // mac address len = 6, (1 + 1 + 6)/8 = 1
                            .concat(reqMac.bytes)
                    ))
            );

            Ipv6Packet ipv6 = new Ipv6Packet();
            ipv6.setVersion(6);
            ipv6.setNextHeader(Consts.IP_PROTOCOL_ICMPv6);
            ipv6.setHopLimit(255);
            ipv6.setSrc((Inet6Address) reqIp);
            byte[] foo = Consts.IPv6_Solicitation_Node_Multicast_Address.toNewJavaArray();
            byte[] bar = ip.getAddress();
            foo[13] = bar[13];
            foo[14] = bar[14];
            foo[15] = bar[15];
            ipv6.setDst((Inet6Address) Utils.l3addr(foo));
            ipv6.setExtHeaders(Collections.emptyList());
            ipv6.setPacket(icmp);
            ipv6.setPayloadLength(icmp.getRawICMPv6Packet(ipv6).length());

            EthernetPacket ether = new EthernetPacket();
            ether.setDst(new MacAddress("ff:ff:ff:ff:ff:ff"));
            ether.setSrc(reqMac);
            ether.setType(Consts.ETHER_TYPE_IPv6);
            ether.setPacket(ipv6);

            VXLanPacket vxlan = new VXLanPacket();
            vxlan.setFlags(0b00001000);
            vxlan.setVni(table.vni);
            vxlan.setPacket(ether);

            broadcastVXLan(logId, table, vxlan);
        }

        private void handleArp(String logId, VXLanPacket inVxlan, ArpPacket inArp, InetAddress ip, MacAddress mac, Iface inputIface) {
            assert Logger.lowLevelDebug(logId + "handleArp(" + inVxlan + "," + inArp + "," + ip + "," + mac + "," + inputIface + ")");

            ArpPacket resp = new ArpPacket();
            resp.setHardwareType(inArp.getHardwareType());
            resp.setProtocolType(inArp.getProtocolType());
            resp.setHardwareSize(inArp.getHardwareSize());
            resp.setProtocolSize(inArp.getProtocolSize());
            resp.setOpcode(Consts.ARP_PROTOCOL_OPCODE_RESP);
            resp.setSenderMac(mac.bytes);
            resp.setSenderIp(ByteArray.from(ip.getAddress()));
            resp.setTargetMac(inArp.getSenderMac());
            resp.setTargetIp(inArp.getTargetIp());

            EthernetPacket ether = new EthernetPacket();
            ether.setDst(inVxlan.getPacket().getSrc());
            ether.setSrc(mac);
            ether.setType(Consts.ETHER_TYPE_ARP);
            ether.setPacket(resp);

            VXLanPacket vxlan = new VXLanPacket();
            vxlan.setFlags(inVxlan.getFlags());
            vxlan.setVni(inVxlan.getVni());
            vxlan.setPacket(ether);

            sendVXLanTo(logId, inputIface, vxlan);
        }

        private void handleIcmpPing(String logId, VXLanPacket inVxlan, AbstractIpPacket inIpPkt, IcmpPacket inIcmp, InetAddress ip, MacAddress mac, Iface inputIface) {
            assert Logger.lowLevelDebug(logId + "handleIcmpPing(" + inVxlan + "," + inIpPkt + "," + inIcmp + "," + ip + "," + mac + "," + inputIface + ")");

            IcmpPacket icmp = new IcmpPacket(ip instanceof Inet6Address);
            icmp.setType(inIcmp.isIpv6() ? Consts.ICMPv6_PROTOCOL_TYPE_ECHO_RESP : Consts.ICMP_PROTOCOL_TYPE_ECHO_RESP);
            icmp.setCode(0);
            icmp.setOther(inIcmp.getOther());

            AbstractIpPacket ipPkt;
            if (ip instanceof Inet4Address) {
                var ipv4 = new Ipv4Packet();
                ipv4.setVersion(4);
                ipv4.setIhl(5);
                ipv4.setTotalLength(20 + icmp.getRawPacket().length());
                ipv4.setTtl(64);
                ipv4.setProtocol(Consts.IP_PROTOCOL_ICMP);
                ipv4.setSrc((Inet4Address) ip);
                ipv4.setDst((Inet4Address) inIpPkt.getSrc());
                ipv4.setOptions(ByteArray.allocate(0));
                ipv4.setPacket(icmp);
                ipPkt = ipv4;
            } else {
                assert ip instanceof Inet6Address;
                var ipv6 = new Ipv6Packet();
                ipv6.setVersion(6);
                ipv6.setNextHeader(icmp.isIpv6() ? Consts.IP_PROTOCOL_ICMPv6 : Consts.IP_PROTOCOL_ICMP);
                ipv6.setHopLimit(64);
                ipv6.setSrc((Inet6Address) ip);
                ipv6.setDst((Inet6Address) inIpPkt.getSrc());
                ipv6.setExtHeaders(Collections.emptyList());
                ipv6.setPacket(icmp);
                ipv6.setPayloadLength(
                    (
                        icmp.isIpv6() ? icmp.getRawICMPv6Packet(ipv6) : icmp.getRawPacket()
                    ).length()
                );
                ipPkt = ipv6;
            }

            EthernetPacket ether = new EthernetPacket();
            ether.setDst(inVxlan.getPacket().getSrc());
            ether.setSrc(mac);
            ether.setType(ip instanceof Inet4Address ? Consts.ETHER_TYPE_IPv4 : Consts.ETHER_TYPE_IPv6);
            ether.setPacket(ipPkt);

            VXLanPacket vxlan = new VXLanPacket();
            vxlan.setFlags(inVxlan.getFlags());
            vxlan.setVni(inVxlan.getVni());
            vxlan.setPacket(ether);

            sendVXLanTo(logId, inputIface, vxlan);
        }

        private void handleIcmpNDP(String logId, VXLanPacket inVxlan, AbstractIpPacket inIpPkt, IcmpPacket inIcmp, InetAddress ip, MacAddress mac, Iface inputIface) {
            assert Logger.lowLevelDebug(logId + "handleIcmpNDP(" + inVxlan + "," + inIpPkt + "," + inIcmp + "," + ip + "," + mac + "," + inputIface + ")");

            assert inIcmp.getType() == Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation;
            // we only handle neighbor solicitation for now

            IcmpPacket icmp = new IcmpPacket(true);
            icmp.setType(Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Advertisement);
            icmp.setCode(0);
            icmp.setOther(
                (ByteArray.allocate(4).set(0, (byte) 0b01100000 /*-R,+S,+O*/)).concat(ByteArray.from(ip.getAddress()))
                    .concat(( // the target link-layer address
                        ByteArray.allocate(1 + 1).set(0, (byte) Consts.ICMPv6_OPTION_TYPE_Target_Link_Layer_Address)
                            .set(1, (byte) 1) // mac address len = 6, (1 + 1 + 6)/8 = 1
                            .concat(mac.bytes)
                    ))
            );

            Ipv6Packet ipv6 = new Ipv6Packet();
            ipv6.setVersion(6);
            ipv6.setNextHeader(Consts.IP_PROTOCOL_ICMPv6);
            ipv6.setHopLimit(255);
            ipv6.setSrc((Inet6Address) ip);
            ipv6.setDst((Inet6Address) inIpPkt.getSrc());
            ipv6.setExtHeaders(Collections.emptyList());
            ipv6.setPacket(icmp);
            ipv6.setPayloadLength(icmp.getRawICMPv6Packet(ipv6).length());

            EthernetPacket ether = new EthernetPacket();
            ether.setDst(inVxlan.getPacket().getSrc());
            ether.setSrc(mac);
            ether.setType(Consts.ETHER_TYPE_IPv6);
            ether.setPacket(ipv6);

            VXLanPacket vxlan = new VXLanPacket();
            vxlan.setFlags(inVxlan.getFlags());
            vxlan.setVni(inVxlan.getVni());
            vxlan.setPacket(ether);

            sendVXLanTo(logId, inputIface, vxlan);
        }

        private void broadcastVXLan(String logId, Table table, VXLanPacket vxlan) {
            assert Logger.lowLevelDebug(logId + "broadcastVXLan(" + table + "," + vxlan + ")");

            Set<Iface> toSend = new HashSet<>();
            for (var entry : table.macTable.listEntries()) {
                toSend.add(entry.iface);
                sendVXLanTo(logId, entry.iface, vxlan);
            }
            for (Iface f : ifaces.keySet()) {
                if (f.serverSideVni == table.vni) {
                    if (toSend.add(f)) {
                        sendVXLanTo(logId, f, vxlan);
                    }
                }
            }
        }

        private void sendVXLanTo(String logId, Iface iface, VXLanPacket vxlan) {
            assert Logger.lowLevelDebug(logId + "sendVXLanTo(" + iface + "," + vxlan + ")");
            // fix vni
            if (iface.clientSideVni != 0) {
                vxlan.setVni(iface.clientSideVni);
            }

            VProxySwitchPacket p = new VProxySwitchPacket(Switch.this::getKey);
            p.setMagic(Consts.VPROXY_SWITCH_MAGIC);
            p.setType(Consts.VPROXY_SWITCH_TYPE_VXLAN);
            p.setVxlan(vxlan);
            sendVProxyPacketTo(iface, p);
        }

        protected final void sendVProxyPacketTo(Iface iface, VProxySwitchPacket p) {
            AbstractPacket packetToSend;

            if (iface.user == null) {
                if (p.getVxlan() == null) {
                    Logger.error(LogType.IMPROPER_USE, "want to send packet to " + iface + " with " + p + ", but both user and vxlan packet are null");
                    return;
                }
                // user == null means it's bare vxlan or tap
                // directly send
                if (iface.udpSockAddress != null) {
                    packetToSend = p.getVxlan();
                } else {
                    assert iface.tap != null;
                    packetToSend = p.getVxlan().getPacket();
                }
            } else {
                p.setUser(iface.user);
                packetToSend = p;
            }

            byte[] bytes;
            try {
                bytes = packetToSend.getRawPacket().toJavaArray();
            } catch (IllegalArgumentException e) {
                assert Logger.lowLevelDebug("encode packet failed, maybe because of a deleted user: " + e);
                return;
            }
            sndBuf.limit(sndBuf.capacity()).position(0);
            sndBuf.put(bytes);
            sndBuf.flip();
            if (iface.udpSockAddress != null) {
                try {
                    sock.send(sndBuf, iface.udpSockAddress);
                } catch (IOException e) {
                    Logger.error(LogType.CONN_ERROR, "sending udp packet to " + iface.udpSockAddress + " using " + sock + " failed", e);
                }
            } else {
                assert iface.tap != null;
                try {
                    iface.tap.write(sndBuf);
                } catch (IOException e) {
                    Logger.error(LogType.CONN_ERROR, "sending packet to " + iface.tap + " failed", e);
                }
            }
        }
    }

    private class PacketHandler extends NetworkStack implements Handler<DatagramFD> {
        private static final int IFACE_TIMEOUT = 60 * 1000;
        private final ByteBuffer rcvBuf = ByteBuffer.allocate(2048);

        @Override
        public void accept(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void connected(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        private Tuple<VXLanPacket, Iface> handleNetworkAndGetVXLanPacket(String logId, SelectorEventLoop loop, InetSocketAddress remote, ByteArray data) {
            VProxySwitchPacket packet = new VProxySwitchPacket(Switch.this::getKey);
            VXLanPacket vxLanPacket;
            Iface iface;

            String err = packet.from(data);
            assert Logger.lowLevelDebug(logId + "packet.from(data) = " + err);
            if (err == null) {
                String user = packet.getUser();
                iface = new Iface(remote, user);
                UserInfo info = users.get(user);
                if (info == null) {
                    Logger.warn(LogType.SYS_ERROR, "concurrency detected: user info is null while parsing the packet succeeded: " + user);
                    return null;
                }
                iface.serverSideVni = info.vni;

                assert Logger.lowLevelDebug(logId + "got packet " + packet + " from " + iface);

                vxLanPacket = packet.getVxlan();
                if (vxLanPacket != null) {
                    int packetVni = vxLanPacket.getVni();
                    iface.clientSideVni = packetVni; // set vni to the iface
                    assert Logger.lowLevelDebug(logId + "setting vni for " + user + " to " + info.vni);
                    if (packetVni != info.vni) {
                        vxLanPacket.setVni(info.vni);
                    }
                }

                if (packet.getType() == Consts.VPROXY_SWITCH_TYPE_PING) {
                    assert Logger.lowLevelDebug(logId + "is vproxy ping message, do reply");
                    sendPingTo(logId, iface);
                }
                // fall through
            } else {
                if (bareVXLanAccess.allow(Protocol.UDP, remote.getAddress(), vxlanBindingAddress.getPort())) {
                    assert Logger.lowLevelDebug(logId + "is bare vxlan");
                    // try to parse into vxlan directly
                    vxLanPacket = new VXLanPacket();
                    err = vxLanPacket.from(data);
                    if (err != null) {
                        assert Logger.lowLevelDebug(logId + "invalid packet for vxlan: " + err + ", drop it");
                        return null;
                    }
                    iface = new Iface(remote);
                    iface.clientSideVni = vxLanPacket.getVni();
                    iface.serverSideVni = iface.clientSideVni;
                    assert Logger.lowLevelDebug(logId + "got vxlan packet " + vxLanPacket + " from " + iface);
                    // fall through
                } else {
                    assert Logger.lowLevelDebug(logId + "invalid packet: " + err + ", drop it");
                    return null;
                }
            }

            var timer = ifaces.get(iface);
            if (timer == null) {
                timer = new IfaceTimer(loop, IFACE_TIMEOUT, iface);
                timer.record(iface);
            }
            timer.resetTimer();

            return new Tuple<>(vxLanPacket, iface);
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

                String logId = UUID.randomUUID().toString() + " ::: ";

                var tuple = handleNetworkAndGetVXLanPacket(logId, ctx.getEventLoop(), remote, data);
                if (tuple == null) {
                    continue;
                }
                var vxlan = tuple.left;
                var iface = tuple.right;
                if (vxlan == null) {
                    assert Logger.lowLevelDebug(logId + "no vxlan packet found, ignore");
                    continue;
                }

                sendIntoNetworkStack(logId, vxlan, iface);
            }
        }

        private void sendPingTo(String logId, Iface iface) {
            assert Logger.lowLevelDebug(logId + "sendPingTo(" + iface + ")");
            VProxySwitchPacket p = new VProxySwitchPacket(Switch.this::getKey);
            p.setMagic(Consts.VPROXY_SWITCH_MAGIC);
            p.setType(Consts.VPROXY_SWITCH_TYPE_PING);
            sendVProxyPacketTo(iface, p);
        }

        @Override
        public void writable(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void removed(HandlerContext<DatagramFD> ctx) {
            assert Logger.lowLevelDebug("udp sock " + ctx.getChannel() + " removed from loop");
            checkAndRestart();
        }
    }

    private void utilRemoveIface(Iface iface) {
        ifaces.remove(iface);

        for (var table : tables.values()) {
            table.macTable.disconnect(iface);
        }
        Logger.warn(LogType.ALERT, iface + " disconnected from Switch:" + alias);
    }

    private class IfaceTimer extends Timer {
        final Iface iface;

        public IfaceTimer(SelectorEventLoop loop, int timeout, Iface iface) {
            super(loop, timeout);
            this.iface = iface;
        }

        @Override
        public void resetTimer() {
            if (getTimeout() == -1) {
                return; // no timeout
            }
            super.resetTimer();
        }

        void record(Iface newIface) {
            if (newIface.clientSideVni != 0) {
                iface.clientSideVni = newIface.clientSideVni;
            }
            if (newIface.serverSideVni != 0) {
                iface.serverSideVni = newIface.serverSideVni;
            }
            ifaces.put(iface, this);
            Logger.alert(iface + " connected to Switch:" + alias);
            resetTimer();
        }

        @Override
        public void cancel() {
            super.cancel();
            utilRemoveIface(iface);
        }
    }

    private class TapHandler extends NetworkStack implements Handler<TunTapDatagramFD> {
        private final Iface iface;

        private final ByteBuffer rcvBuf = ByteBuffer.allocate(2048);

        private TapHandler(Iface iface) {
            this.iface = iface;
        }

        @Override
        public void accept(HandlerContext<TunTapDatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void connected(HandlerContext<TunTapDatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void readable(HandlerContext<TunTapDatagramFD> ctx) {
            while (true) {
                rcvBuf.limit(rcvBuf.capacity()).position(0);
                try {
                    ctx.getChannel().read(rcvBuf);
                } catch (IOException e) {
                    Logger.error(LogType.CONN_ERROR, "tap device " + ctx.getChannel() + " got error when reading", e);
                    return;
                }
                if (rcvBuf.position() == 0) {
                    break; // nothing read, quit loop
                }
                byte[] bytes = rcvBuf.array();
                ByteArray data = ByteArray.from(bytes).sub(0, rcvBuf.position());

                String logId = UUID.randomUUID().toString() + " ::: ";

                EthernetPacket ether = new EthernetPacket();
                String err = ether.from(data);
                if (err != null) {
                    assert Logger.lowLevelDebug(logId + "got invalid packet: " + err);
                    continue;
                }
                VXLanPacket vxlan = new VXLanPacket();
                vxlan.setFlags(0b00001000);
                vxlan.setVni(iface.serverSideVni);
                vxlan.setPacket(ether);

                sendIntoNetworkStack(logId, vxlan, iface);
            }
        }

        @Override
        public void writable(HandlerContext<TunTapDatagramFD> ctx) {
            // ignore, and will not fire
        }

        @Override
        public void removed(HandlerContext<TunTapDatagramFD> ctx) {
            Logger.warn(LogType.CONN_ERROR, "tap device " + ctx.getChannel() + " removed from loop, it's not handled anymore, need to be closed");
            try {
                delTap(ctx.getChannel().tuntap.dev);
            } catch (NotFoundException ignore) {
            }
        }
    }
}
