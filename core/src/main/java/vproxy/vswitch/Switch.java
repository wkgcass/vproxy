package vproxy.vswitch;

import vproxy.base.component.elgroup.EventLoopGroup;
import vproxy.base.component.elgroup.EventLoopGroupAttach;
import vproxy.base.connection.NetEventLoop;
import vproxy.base.connection.Protocol;
import vproxy.base.selector.Handler;
import vproxy.base.selector.HandlerContext;
import vproxy.base.selector.PeriodicEvent;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.selector.wrap.blocking.BlockingDatagramFD;
import vproxy.base.util.Timer;
import vproxy.base.util.*;
import vproxy.base.util.crypto.Aes256Key;
import vproxy.base.util.exception.AlreadyExistException;
import vproxy.base.util.exception.ClosedException;
import vproxy.base.util.exception.NotFoundException;
import vproxy.base.util.exception.XException;
import vproxy.component.secure.SecurityGroup;
import vproxy.vfd.*;
import vproxy.vmirror.Mirror;
import vproxy.vpacket.EthernetPacket;
import vproxy.vpacket.VProxyEncryptedPacket;
import vproxy.vpacket.VXLanPacket;
import vproxy.vswitch.iface.*;
import vproxy.vswitch.stack.InputPacketL2Context;
import vproxy.vswitch.stack.L2;
import vproxy.vswitch.stack.SwitchContext;
import vproxy.vswitch.util.SwitchUtils;
import vproxy.vswitch.util.UserInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class Switch {
    public final String alias;
    public final IPPort vxlanBindingAddress;
    public final EventLoopGroup eventLoopGroup;
    private NetEventLoop eventLoop;
    private PeriodicEvent refreshCacheEvent;
    private int macTableTimeout;
    private int arpTableTimeout;
    public SecurityGroup bareVXLanAccess;

    private boolean started = false;
    private boolean wantStart = false;

    private final Map<String, UserInfo> users = new HashMap<>();
    private DatagramFD sock;
    private final Map<Integer, Table> tables = new ConcurrentHashMap<>();
    private final Map<Iface, IfaceTimer> ifaces = new HashMap<>();

    public final NetworkStack netStack = new NetworkStack();

    public Switch(String alias, IPPort vxlanBindingAddress, EventLoopGroup eventLoopGroup,
                  int macTableTimeout, int arpTableTimeout, SecurityGroup bareVXLanAccess) throws AlreadyExistException, ClosedException {
        this.alias = alias;
        this.vxlanBindingAddress = vxlanBindingAddress;
        this.eventLoopGroup = eventLoopGroup;
        this.macTableTimeout = macTableTimeout;
        this.arpTableTimeout = arpTableTimeout;
        this.bareVXLanAccess = bareVXLanAccess;

        try {
            eventLoopGroup.attachResource(new SwitchEventLoopGroupAttach());
        } catch (AlreadyExistException e) {
            Logger.shouldNotHappen("attaching resource to event loop group failed", e);
            throw e;
        } catch (ClosedException e) {
            Logger.error(LogType.IMPROPER_USE, "the event loop group is already closed", e);
            throw e;
        }
    }

    private void releaseSock() {
        if (sock != null) {
            try {
                sock.close();
                sock = null;
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

        if (sock == null) {
            sock = FDProvider.get().openDatagramFD();
            try {
                sock.configureBlocking(false);
                sock.bind(vxlanBindingAddress);
            } catch (IOException e) {
                releaseSock();
                throw e;
            }
        }

        var loop = netLoop.getSelectorEventLoop();
        loop.add(sock, EventSet.read(), null, new PacketHandler());
        eventLoop = netLoop;
        refreshCacheEvent = eventLoop.getSelectorEventLoop().period(40_000, this::refreshCache);
        tables.values().forEach(t -> t.setLoop(loop));
        started = true;
    }

    private void cancelAllIface() {
        var set = Set.copyOf(ifaces.values());
        set.forEach(IfaceTimer::cancel);
    }

    private void cancelEventLoop() {
        eventLoop = null;
        if (refreshCacheEvent != null) {
            refreshCacheEvent.cancel();
            refreshCacheEvent = null;
        }
    }

    private void stopStack() {
        stopTcp();
    }

    private void stopTcp() {
        for (var tbl : tables.values()) {
            for (var entry : tbl.conntrack.listListenEntries()) {
                entry.destroy();
                tbl.conntrack.removeListen(entry.listening);
            }
            for (var entry : tbl.conntrack.listTcpEntries()) {
                entry.destroy();
                tbl.conntrack.remove(entry.source, entry.destination);
            }
        }
    }

    public synchronized void stop() {
        wantStart = false;
        if (!started) {
            return;
        }
        stopStack();
        cancelEventLoop();
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

    private void refreshCache() {
        for (Table t : tables.values()) {
            for (ArpTable.ArpEntry arp : t.arpTable.listEntries()) {
                if (arp.getTTL() < ArpTable.ARP_REFRESH_CACHE_BEFORE_TTL_TIME) {
                    refreshArpCache(t, arp.ip, arp.mac);
                }
            }
            for (MacTable.MacEntry macEntry : t.macTable.listEntries()) {
                if (macEntry.getTTL() < MacTable.MAC_TRY_TO_REFRESH_CACHE_BEFORE_TTL_TIME) {
                    var set = t.arpTable.lookupByMac(macEntry.mac);
                    if (set != null) {
                        set.stream().findAny().ifPresent(arp -> refreshArpCache(t, arp.ip, arp.mac));
                    }
                }
            }
        }
    }

    private void refreshArpCache(Table t, IP ip, MacAddress mac) {
        String handlingUUID = netStack.newHandlingUUID();
        assert Logger.lowLevelDebug(handlingUUID + " trigger arp cache refresh for " + ip.formatToIPString() + " " + mac);

        netStack.L2.L3.resolve(handlingUUID, t, ip, mac);
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

    public void addTable(int vni, Network v4network, Network v6network, Annotations annotations) throws AlreadyExistException, XException {
        if (tables.containsKey(vni)) {
            throw new AlreadyExistException("vni " + vni + " already exists in switch " + alias);
        }
        if (eventLoop == null) {
            throw new XException("the switch " + alias + " is not bond to any event loop, cannot add vni");
        }
        tables.computeIfAbsent(vni, n -> new Table(this, n, eventLoop, v4network, v6network, macTableTimeout, arpTableTimeout, annotations));
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
        user = formatUserName(user);

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
    public String addTap(String devPattern, int vni, String postScript, Annotations annotations) throws XException, IOException {
        NetEventLoop netEventLoop = eventLoop;
        if (netEventLoop == null) {
            throw new XException("the switch " + alias + " is not bond to any event loop, cannot add tap device");
        }
        SelectorEventLoop loop = netEventLoop.getSelectorEventLoop();

        FDs fds = FDProvider.get().getProvided();
        if (!(fds instanceof FDsWithTap)) {
            throw new IOException("tap is not supported by " + fds + ", use -Dvfd=posix or -Dvfd=windows");
        }
        FDsWithTap tapFDs = (FDsWithTap) fds;
        TapDatagramFD fd = tapFDs.openTap(devPattern);
        AbstractDatagramFD<?> fdToPutIntoLoop = null;
        TapIface iface;
        try {
            if (tapFDs.tapNonBlockingSupported()) {
                fdToPutIntoLoop = fd;
                fd.configureBlocking(false);
            } else {
                fdToPutIntoLoop = new BlockingDatagramFD<>(fd, loop, 2048, 65536, 32);
            }
            iface = new TapIface(fd, fdToPutIntoLoop, vni, postScript, annotations, loop);
            loop.add(fdToPutIntoLoop, EventSet.read(), null, new TapHandler(iface, fd));
        } catch (IOException e) {
            if (fdToPutIntoLoop != null) {
                try {
                    fdToPutIntoLoop.close();
                } catch (IOException t) {
                    Logger.shouldNotHappen("failed to close the tap device wrapper when rolling back the creation", t);
                }
            }
            try {
                fd.close();
            } catch (IOException t) {
                Logger.shouldNotHappen("failed to close the tap device when rolling back the creation", t);
            }
            throw e;
        }
        try {
            executePostScript(fd.getTap().dev, vni, postScript);
        } catch (Exception e) {
            // executing script failed
            // close the fds
            try {
                loop.remove(fdToPutIntoLoop);
            } catch (Throwable ignore) {
            }
            try {
                fdToPutIntoLoop.close();
            } catch (IOException t) {
                Logger.shouldNotHappen("failed to close the tap device wrapper when rolling back the creation", t);
            }
            try {
                fd.close();
            } catch (IOException t) {
                Logger.shouldNotHappen("closing the tap fd failed, " + fd, t);
            }
            throw new XException(Utils.formatErr(e));
        }
        loop.runOnLoop(() -> ifaces.put(iface, new IfaceTimer(loop, -1, iface)));
        Logger.alert("tap device added: " + fd.getTap().dev);
        return fd.getTap().dev;
    }

    private void executePostScript(String dev, int vni, String postScript) throws Exception {
        if (postScript == null || postScript.isBlank()) {
            return;
        }
        ProcessBuilder pb = new ProcessBuilder().command(postScript);
        var env = pb.environment();
        env.put("DEV", dev);
        env.put("VNI", "" + vni);
        env.put("SWITCH", alias);
        Process p = pb.start();
        Utils.pipeOutputOfSubProcess(p);
        p.waitFor(10, TimeUnit.SECONDS);
        if (p.isAlive()) {
            p.destroyForcibly();
            throw new Exception("the process took too long to execute");
        }
        int exit = p.exitValue();
        if (exit == 0) {
            return;
        }
        throw new Exception("exit code is " + exit);
    }

    public void delTap(String devName) throws NotFoundException {
        Iface iface = null;
        for (Iface i : ifaces.keySet()) {
            if (!(i instanceof TapIface)) {
                continue;
            }
            TapIface tapIface = (TapIface) i;
            if (tapIface.tap.getTap().dev.equals(devName)) {
                iface = i;
                break;
            }
        }
        if (iface == null) {
            throw new NotFoundException("tap", devName);
        }
        utilRemoveIface(iface);
    }

    private void initUserClient(SelectorEventLoop loop, UserClientIface iface) throws IOException {
        DatagramFD cliSock = iface.sock;
        iface.attachedToLoopAlert(loop);
        try {
            loop.add(cliSock, EventSet.read(), null, new UserClientHandler(loop, iface));
        } catch (IOException e) {
            iface.detachedFromLoopAlert();
            throw e;
        }
    }

    public void addUserClient(String user, String password, int vni, IPPort remoteAddr) throws AlreadyExistException, IOException, XException {
        user = formatUserName(user);

        for (Iface i : ifaces.keySet()) {
            if (!(i instanceof UserClientIface)) {
                continue;
            }
            UserClientIface ucliIface = (UserClientIface) i;
            if (ucliIface.user.user.equals(user) && ucliIface.remoteAddress.equals(remoteAddr)) {
                throw new AlreadyExistException("user-client", user);
            }
        }

        SelectorEventLoop loop = eventLoop.getSelectorEventLoop();

        Aes256Key key = new Aes256Key(password);
        UserInfo info = new UserInfo(user, key, password, vni);

        DatagramFD cliSock = FDProvider.get().openDatagramFD();
        UserClientIface iface = new UserClientIface(info, cliSock, remoteAddr);

        try {
            cliSock.connect(remoteAddr);
            cliSock.configureBlocking(false);
        } catch (IOException e) {
            try {
                cliSock.close();
            } catch (IOException t) {
                Logger.shouldNotHappen("close datagram sock when rolling back failed", t);
            }
            throw e;
        }

        try {
            initUserClient(loop, iface);
        } catch (IOException e) {
            try {
                cliSock.close();
            } catch (IOException t) {
                Logger.shouldNotHappen("close datagram sock when rolling back failed", t);
            }
            throw e;
        }
        ifaces.put(iface, new IfaceTimer(loop, -1, iface));
    }

    private String formatUserName(String user) throws XException {
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
        return user;
    }

    public void delUserClient(String user, IPPort remoteAddr) throws NotFoundException {
        UserClientIface iface = null;
        for (Iface i : ifaces.keySet()) {
            if (!(i instanceof UserClientIface)) {
                continue;
            }
            UserClientIface ucliIface = (UserClientIface) i;
            if (ucliIface.user.user.equals(user) && ucliIface.remoteAddress.equals(remoteAddr)) {
                iface = ucliIface;
                break;
            }
        }

        if (iface == null) {
            throw new NotFoundException("user-client", user);
        }
        utilRemoveIface(iface);
    }

    public void addRemoteSwitch(String alias, IPPort vxlanSockAddr, boolean addSwitchFlag) throws XException, AlreadyExistException {
        NetEventLoop netEventLoop = eventLoop;
        if (netEventLoop == null) {
            throw new XException("the switch " + alias + " is not bond to any event loop, cannot add remote switch");
        }
        SelectorEventLoop loop = netEventLoop.getSelectorEventLoop();

        for (Iface i : ifaces.keySet()) {
            if (!(i instanceof RemoteSwitchIface)) {
                continue;
            }
            RemoteSwitchIface rsi = (RemoteSwitchIface) i;
            if (alias.equals(rsi.alias)) {
                throw new AlreadyExistException("switch", alias);
            }
            if (vxlanSockAddr.equals(rsi.udpSockAddress)) {
                throw new AlreadyExistException("switch", vxlanSockAddr.formatToIPPortString());
            }
        }
        Iface iface = new RemoteSwitchIface(alias, vxlanSockAddr, addSwitchFlag);
        loop.runOnLoop(() -> ifaces.put(iface, new IfaceTimer(loop, -1, iface)));
    }

    public void delRemoteSwitch(String alias) throws NotFoundException {
        Iface iface = null;
        for (Iface i : ifaces.keySet()) {
            if (!(i instanceof RemoteSwitchIface)) {
                continue;
            }
            RemoteSwitchIface rsi = (RemoteSwitchIface) i;
            if (alias.equals(rsi.alias)) {
                iface = i;
                break;
            }
        }
        if (iface == null) {
            throw new NotFoundException("switch", alias);
        }
        utilRemoveIface(iface);
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

    public class NetworkStack {
        public final L2 L2 = new L2(new SwitchContext(
            this::sendPacket,
            Switch.this::getIfaces,
            tables::get,
            () -> eventLoop.getSelectorEventLoop()
        ));
        private final ByteBuffer sndBuf = Utils.allocateByteBuffer(2048);

        protected NetworkStack() {
        }

        protected String newHandlingUUID() {
            return UUID.randomUUID().toString();
        }

        protected void inputVXLan(String handlingUUID, VXLanPacket vxlan, Iface iface) {
            int vni = vxlan.getVni();
            Table table = tables.get(vni);
            if (table == null) {
                assert Logger.lowLevelDebug(handlingUUID + " vni not defined: " + vni);
                return;
            }

            if (Mirror.isEnabled("switch")) {
                Mirror.switchPacket(vxlan.getPacket());
            }

            // loop detect mechanism
            int r1 = vxlan.getReserved1();
            int r2 = vxlan.getReserved2();
            {
                if (r2 > 250) {
                    Logger.error(LogType.INVALID_EXTERNAL_DATA, "possible loop detected from " + iface + " with packet " + vxlan);

                    final int I_DETECTED_A_POSSIBLE_LOOP = Consts.I_DETECTED_A_POSSIBLE_LOOP;
                    final int I_WILL_DISCONNECT_FROM_YOU_IF_I_RECEIVE_AGAIN = Consts.I_WILL_DISCONNECT_FROM_YOU_IF_I_RECEIVE_AGAIN;

                    boolean possibleLoop = (r1 & I_DETECTED_A_POSSIBLE_LOOP) == I_DETECTED_A_POSSIBLE_LOOP;
                    boolean willDisconnect = (r1 & I_WILL_DISCONNECT_FROM_YOU_IF_I_RECEIVE_AGAIN) == I_WILL_DISCONNECT_FROM_YOU_IF_I_RECEIVE_AGAIN;

                    if (possibleLoop && willDisconnect) {
                        Logger.error(LogType.INVALID_EXTERNAL_DATA, "disconnect from " + iface + " due to possible loop");
                        table.macTable.disconnect(iface);
                        return; // drop
                    }
                    if (!possibleLoop && !willDisconnect) {
                        vxlan.setReserved1(r1 | I_DETECTED_A_POSSIBLE_LOOP);
                    } else {
                        vxlan.setReserved1(r1 | I_DETECTED_A_POSSIBLE_LOOP | I_WILL_DISCONNECT_FROM_YOU_IF_I_RECEIVE_AGAIN);
                    }
                }
                vxlan.setReserved2(r2 + 1);
            }

            L2.input(new InputPacketL2Context(handlingUUID, iface, table, vxlan));
        }

        private void sendPacket(VXLanPacket vxlan, Iface iface) {
            assert Logger.lowLevelDebug("unicast(" + iface + "," + vxlan + ")");

            if (Mirror.isEnabled("switch")) {
                Mirror.switchPacket(vxlan.getPacket());
            }

            sndBuf.limit(sndBuf.capacity()).position(0);
            try {
                iface.sendPacket(sock, vxlan, sndBuf);
            } catch (IOException e) {
                Logger.error(LogType.CONN_ERROR, "sending packet to " + iface + " failed", e);
            }
        }

        protected final void sendVProxyPacketTo(String handlingUUID, IfaceCanSendVProxyPacket iface, VProxyEncryptedPacket p) {
            assert Logger.lowLevelDebug("sendVProxyPacketTo(" + handlingUUID + "," + iface + "," + p + ")");

            sndBuf.limit(sndBuf.capacity()).position(0);
            try {
                iface.sendVProxyPacket(sock, p, sndBuf);
            } catch (IOException e) {
                Logger.error(LogType.CONN_ERROR, "sending packet to " + iface + " failed", e);
            }
        }
    }

    private class PacketHandler implements Handler<DatagramFD> {
        private static final int IFACE_TIMEOUT = 60 * 1000;
        private final ByteBuffer rcvBuf = Utils.allocateByteBuffer(2048);

        @Override
        public void accept(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void connected(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        private Tuple<VXLanPacket, Iface> handleNetworkAndGetVXLanPacket(String handlingUUID, SelectorEventLoop loop, IPPort remote, ByteArray data) {
            VProxyEncryptedPacket packet = new VProxyEncryptedPacket(Switch.this::getKey);
            VXLanPacket vxLanPacket;
            Iface iface;

            String err = packet.from(data);
            assert Logger.lowLevelDebug(handlingUUID + " packet.from(data) = " + err);
            if (err == null) {
                String user = packet.getUser();
                UserIface uiface = new UserIface(remote, user, users);
                iface = uiface;
                UserInfo info = users.get(user);
                if (info == null) {
                    Logger.warn(LogType.SYS_ERROR, "concurrency detected: user info is null while parsing the packet succeeded: " + user);
                    return null;
                }
                uiface.setLocalSideVni(info.vni);

                assert Logger.lowLevelDebug(handlingUUID + " got packet " + packet + " from " + iface);

                vxLanPacket = packet.getVxlan();
                if (vxLanPacket != null) {
                    int packetVni = vxLanPacket.getVni();
                    uiface.setRemoteSideVni(packetVni); // set vni to the iface
                    assert Logger.lowLevelDebug(handlingUUID + " setting vni for " + user + " to " + info.vni);
                    if (packetVni != info.vni) {
                        vxLanPacket.setVni(info.vni);
                    }
                }

                if (packet.getType() == Consts.VPROXY_SWITCH_TYPE_PING) {
                    assert Logger.lowLevelDebug(handlingUUID + " is vproxy ping message, do reply");
                    sendPingTo(handlingUUID, uiface);
                }
                // fall through
            } else {
                if (bareVXLanAccess.allow(Protocol.UDP, remote.getAddress(), vxlanBindingAddress.getPort())) {
                    assert Logger.lowLevelDebug(handlingUUID + " is bare vxlan");
                    // try to parse into vxlan directly
                    vxLanPacket = new VXLanPacket();
                    err = vxLanPacket.from(data);
                    if (err != null) {
                        assert Logger.lowLevelDebug(handlingUUID + " invalid packet for vxlan: " + err + ", drop it");
                        return null;
                    }
                    // check whether it's coming from remote switch
                    Iface remoteSwitch = null;
                    for (Iface i : ifaces.keySet()) {
                        if (!(i instanceof RemoteSwitchIface)) {
                            continue;
                        }
                        RemoteSwitchIface rsi = (RemoteSwitchIface) i;
                        if (remote.equals(rsi.udpSockAddress)) {
                            remoteSwitch = i;
                            break;
                        }
                    }
                    if (remoteSwitch == null) { // is from a vxlan endpoint
                        BareVXLanIface biface = new BareVXLanIface(remote);
                        iface = biface;
                        biface.setLocalSideVni(vxLanPacket.getVni());

                        // distinguish bare vxlan sock and switch vxlan link
                        {
                            int r1 = vxLanPacket.getReserved1();
                            final int I_AM_FROM_SWITCH = Consts.I_AM_FROM_SWITCH;
                            if ((r1 & I_AM_FROM_SWITCH) == I_AM_FROM_SWITCH) {
                                Logger.warn(LogType.INVALID_EXTERNAL_DATA,
                                    "received a packet which should come from a remote switch, but actually coming from bare vxlan sock: " + iface + " with packet " + vxLanPacket);
                                return null; // drop
                            }
                        }
                    } else { // is from a remote switch
                        iface = remoteSwitch;
                    }
                    assert Logger.lowLevelDebug(handlingUUID + " got vxlan packet " + vxLanPacket + " from " + iface);
                    // fall through
                } else {
                    assert Logger.lowLevelDebug(handlingUUID + " not in allowed security-group or invalid packet: " + err + ", drop it");
                    return null;
                }
            }

            // check whether the packet's src and dst mac are the same
            if (vxLanPacket != null) {
                if (vxLanPacket.getPacket().getSrc().equals(vxLanPacket.getPacket().getDst())) {
                    assert Logger.lowLevelDebug(handlingUUID + " got packet with same src and dst: " + vxLanPacket);
                    return null;
                }
            }

            var timer = ifaces.get(iface);
            if (timer == null) {
                timer = new IfaceTimer(loop, IFACE_TIMEOUT, iface);
            }
            timer.record(iface);

            return new Tuple<>(vxLanPacket, iface);
        }

        @Override
        public void readable(HandlerContext<DatagramFD> ctx) {
            DatagramFD sock = ctx.getChannel();
            while (true) {
                rcvBuf.limit(rcvBuf.capacity()).position(0);
                IPPort remote;
                try {
                    remote = sock.receive(rcvBuf);
                } catch (IOException e) {
                    Logger.error(LogType.CONN_ERROR, "udp sock " + ctx.getChannel() + " got error when reading", e);
                    return;
                }
                if (rcvBuf.position() == 0) {
                    break; // nothing read, quit loop
                }
                byte[] bytes = rcvBuf.array();
                ByteArray data = ByteArray.from(bytes).sub(0, rcvBuf.position());

                String handlingUUID = netStack.newHandlingUUID();

                var tuple = handleNetworkAndGetVXLanPacket(handlingUUID, ctx.getEventLoop(), remote, data);
                if (tuple == null) {
                    continue;
                }
                var vxlan = tuple.left;
                var iface = tuple.right;
                if (vxlan == null) {
                    assert Logger.lowLevelDebug(handlingUUID + "no vxlan packet found, ignore");
                    continue;
                }

                netStack.inputVXLan(handlingUUID, vxlan, iface);
            }
        }

        private void sendPingTo(String handlingUUID, UserIface iface) {
            assert Logger.lowLevelDebug("sendPingTo(" + handlingUUID + "," + iface + ")");
            VProxyEncryptedPacket p = new VProxyEncryptedPacket(Switch.this::getKey);
            p.setMagic(Consts.VPROXY_SWITCH_MAGIC);
            p.setType(Consts.VPROXY_SWITCH_TYPE_PING);
            netStack.sendVProxyPacketTo(handlingUUID, iface, p);
        }

        @Override
        public void writable(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void removed(HandlerContext<DatagramFD> ctx) {
            Logger.error(LogType.IMPROPER_USE, "the udp sock " + ctx.getChannel() + " is removed from loop," +
                "the loop is considered to be closed, it's required to terminate all ifaces");
            boolean backupWantStart = wantStart;
            stop();
            wantStart = backupWantStart;
        }
    }

    private void utilRemoveIface(Iface iface) {
        ifaces.remove(iface);

        for (var table : tables.values()) {
            table.macTable.disconnect(iface);
        }
        Logger.warn(LogType.ALERT, iface + " disconnected from Switch:" + alias);

        iface.destroy();
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
            SwitchUtils.updateBothSideVni(iface, newIface);
            if (ifaces.putIfAbsent(iface, this) == null) {
                Logger.alert(iface + " connected to Switch:" + alias);
            }
            resetTimer();
        }

        @Override
        public void cancel() {
            super.cancel();
            utilRemoveIface(iface);
        }
    }

    private class TapHandler extends NetworkStack implements Handler<AbstractDatagramFD<?>> {
        private final TapIface iface;
        private final TapDatagramFD tapDatagramFD;

        private final ByteBuffer rcvBuf = Utils.allocateByteBuffer(2048);

        private TapHandler(TapIface iface, TapDatagramFD tapDatagramFD) {
            this.iface = iface;
            this.tapDatagramFD = tapDatagramFD;
        }

        @Override
        public void accept(HandlerContext<AbstractDatagramFD<?>> ctx) {
            // will not fire
        }

        @Override
        public void connected(HandlerContext<AbstractDatagramFD<?>> ctx) {
            // will not fire
        }

        @Override
        public void readable(HandlerContext<AbstractDatagramFD<?>> ctx) {
            while (true) {
                rcvBuf.limit(rcvBuf.capacity()).position(0);
                try {
                    ctx.getChannel().read(rcvBuf);
                } catch (IOException e) {
                    Logger.error(LogType.CONN_ERROR, "tap device " + tapDatagramFD + " got error when reading", e);
                    return;
                }
                if (rcvBuf.position() == 0) {
                    break; // nothing read, quit loop
                }
                byte[] bytes = rcvBuf.array();
                ByteArray data = ByteArray.from(bytes).sub(0, rcvBuf.position());

                String handlingUUID = newHandlingUUID();

                EthernetPacket ether = new EthernetPacket();
                String err = ether.from(data);
                if (err != null) {
                    assert Logger.lowLevelDebug(handlingUUID + " got invalid packet: " + err);
                    continue;
                }
                VXLanPacket vxlan = new VXLanPacket();
                vxlan.setFlags(0b00001000);
                vxlan.setVni(iface.localSideVni);
                vxlan.setPacket(ether);

                var table = tables.get(iface.localSideVni);
                if (table == null) {
                    Logger.shouldNotHappen("cannot find table from tap device " + iface);
                    return;
                }

                L2.input(new InputPacketL2Context(handlingUUID, iface, table, ether));
            }
        }

        @Override
        public void writable(HandlerContext<AbstractDatagramFD<?>> ctx) {
            // ignore, and will not fire
        }

        @Override
        public void removed(HandlerContext<AbstractDatagramFD<?>> ctx) {
            Logger.warn(LogType.CONN_ERROR, "tap device " + tapDatagramFD + " removed from loop, it's not handled anymore, need to be closed");
            try {
                delTap(tapDatagramFD.getTap().dev);
            } catch (NotFoundException ignore) {
            }
        }
    }

    private class UserClientHandler extends NetworkStack implements Handler<DatagramFD> {
        private final ByteBuffer rcvBuf = Utils.allocateByteBuffer(2048);
        private final UserClientIface iface;

        private ConnectedToSwitchTimer connectedToSwitchTimer = null;
        private static final int toSwitchTimeoutSeconds = 60;

        private PeriodicEvent pingPeriodicEvent;
        private static final int pingPeriod = 20 * 1000;

        private class ConnectedToSwitchTimer extends Timer {

            public ConnectedToSwitchTimer(SelectorEventLoop loop) {
                super(loop, toSwitchTimeoutSeconds * 1000);
                iface.setConnected(true);
            }

            @Override
            public void cancel() {
                super.cancel();
                connectedToSwitchTimer = null;
                iface.setConnected(false);
            }
        }

        public UserClientHandler(SelectorEventLoop loop, UserClientIface iface) {
            this.iface = iface;
            pingPeriodicEvent = loop.period(pingPeriod, this::sendPingPacket);
            sendPingPacket();
        }

        private void sendPingPacket() {
            VProxyEncryptedPacket p = new VProxyEncryptedPacket(x -> iface.user.key);
            p.setMagic(Consts.VPROXY_SWITCH_MAGIC);
            p.setType(Consts.VPROXY_SWITCH_TYPE_PING);
            sendVProxyPacketTo(newHandlingUUID(), iface, p);
        }

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
                try {
                    sock.read(rcvBuf);
                } catch (IOException e) {
                    Logger.error(LogType.CONN_ERROR, "udp sock " + ctx.getChannel() + " got error when reading", e);
                    return;
                }
                if (rcvBuf.position() == 0) {
                    break; // nothing read, quit loop
                }

                String handlingUUID = newHandlingUUID();

                VProxyEncryptedPacket p = new VProxyEncryptedPacket(x -> iface.user.key);
                ByteArray arr = ByteArray.from(rcvBuf.array()).sub(0, rcvBuf.position());
                String err = p.from(arr);
                if (err != null) {
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA, handlingUUID + " received invalid packet from " + iface + ": " + arr);
                    continue;
                }
                if (!p.getUser().equals(iface.user.user)) {
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA, handlingUUID + " user in received packet from " + iface + " mismatches, got " + p.getUser());
                    continue;
                }
                if (connectedToSwitchTimer == null) {
                    connectedToSwitchTimer = new ConnectedToSwitchTimer(ctx.getEventLoop());
                }
                connectedToSwitchTimer.resetTimer();
                if (p.getVxlan() == null) {
                    // not vxlan packet, ignore
                    continue;
                }
                if (p.getVxlan().getVni() != iface.user.vni) {
                    p.getVxlan().setVni(iface.user.vni);
                }
                inputVXLan(handlingUUID, p.getVxlan(), iface);
            }
        }

        @Override
        public void writable(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void removed(HandlerContext<DatagramFD> ctx) {
            iface.detachedFromLoopAlert();
            if (connectedToSwitchTimer != null) {
                connectedToSwitchTimer.cancel();
                connectedToSwitchTimer = null;
            }
            if (pingPeriodicEvent != null) {
                pingPeriodicEvent.cancel();
                pingPeriodicEvent = null;
            }
        }
    }
}
