package vproxy.vswitch;

import vproxy.base.component.elgroup.EventLoopGroup;
import vproxy.base.component.elgroup.EventLoopGroupAttach;
import vproxy.base.connection.NetEventLoop;
import vproxy.base.selector.PeriodicEvent;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.util.Timer;
import vproxy.base.util.*;
import vproxy.base.util.crypto.Aes256Key;
import vproxy.base.util.exception.AlreadyExistException;
import vproxy.base.util.exception.ClosedException;
import vproxy.base.util.exception.NotFoundException;
import vproxy.base.util.exception.XException;
import vproxy.base.util.objectpool.CursorList;
import vproxy.base.util.thread.VProxyThread;
import vproxy.component.secure.SecurityGroup;
import vproxy.vfd.*;
import vproxy.vmirror.Mirror;
import vproxy.vswitch.iface.*;
import vproxy.vswitch.stack.NetworkStack;
import vproxy.vswitch.util.SwitchUtils;
import vproxy.vswitch.util.UserInfo;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Switch {
    public final String alias;
    public final IPPort vxlanBindingAddress;
    public final EventLoopGroup eventLoopGroup;
    private NetEventLoop eventLoop;
    private PeriodicEvent refreshCacheEvent;
    private int macTableTimeout;
    private int arpTableTimeout;
    public SecurityGroup bareVXLanAccess;
    public int defaultMtu;
    public boolean defaultFloodAllowed;

    private boolean started = false;
    private boolean wantStart = false;

    private final Map<String, UserInfo> users = new HashMap<>();
    private DatagramFD sock;
    private final Map<Integer, Table> tables = new ConcurrentHashMap<>();
    private final Map<Iface, IfaceTimer> ifaces = new HashMap<>();

    private final SwitchContext swCtx = new SwitchContext(
        this,
        () -> this.netStack,
        () -> {
            var foo = wantStart;
            stop();
            wantStart = foo;
        },
        this::sendPacket,
        ifaces::keySet,
        tables::get,
        users::get,
        () -> eventLoop.getSelectorEventLoop(),
        this::onIfacePacketsArrive,
        this::utilRemoveIface,
        this::initIface
    );
    private final NetworkStack netStack = new NetworkStack(swCtx);

    public Switch(String alias, IPPort vxlanBindingAddress, EventLoopGroup eventLoopGroup,
                  int macTableTimeout, int arpTableTimeout, SecurityGroup bareVXLanAccess,
                  int defaultMtu, boolean defaultFloodAllowed) throws AlreadyExistException, ClosedException {
        this.alias = alias;
        this.vxlanBindingAddress = vxlanBindingAddress;
        this.eventLoopGroup = eventLoopGroup;
        this.macTableTimeout = macTableTimeout;
        this.arpTableTimeout = arpTableTimeout;
        this.bareVXLanAccess = bareVXLanAccess;
        this.defaultMtu = defaultMtu;
        this.defaultFloodAllowed = defaultFloodAllowed;

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
        loop.add(sock, EventSet.read(), null, new DatagramInputHandler(swCtx));
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

    private void cancelTables() {
        for (var tbl : tables.values()) {
            tbl.clearCache();
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
        cancelTables();
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
        VProxyThread.current().newUuidDebugInfo();
        assert Logger.lowLevelDebug("trigger arp cache refresh for " + ip.formatToIPString() + " " + mac);

        netStack.resolve(t, ip, mac);
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
        tables.computeIfAbsent(vni, n -> new Table(swCtx, n, eventLoop, v4network, v6network, macTableTimeout, arpTableTimeout, annotations));
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

    public void addUser(String user, String password, int vni,
                        Integer defaultMtu, Boolean defaultFloodAllowed) throws AlreadyExistException, XException {
        user = formatUserName(user);
        Aes256Key key = new Aes256Key(password);
        if (defaultMtu == null) {
            defaultMtu = this.defaultMtu;
        }
        if (defaultFloodAllowed == null) {
            defaultFloodAllowed = this.defaultFloodAllowed;
        }
        UserInfo old = users.putIfAbsent(user, new UserInfo(user, key, password, vni, defaultMtu, defaultFloodAllowed));
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
        return addTap(devPattern, vni, postScript, annotations, null, null);
    }

    // return created dev name
    public String addTap(String devPattern, int vni, String postScript, Annotations annotations,
                         Integer mtu, Boolean floodAllowed) throws XException, IOException {
        NetEventLoop netEventLoop = eventLoop;
        if (netEventLoop == null) {
            throw new XException("the switch " + alias + " is not bond to any event loop, cannot add tap device");
        }
        SelectorEventLoop loop = netEventLoop.getSelectorEventLoop();

        FDs fds = FDProvider.get().getProvided();
        if (!(fds instanceof FDsWithTap)) {
            throw new IOException("tap is not supported by " + fds + ", use -Dvfd=posix or -Dvfd=windows");
        }
        TapIface iface = new TapIface(devPattern, vni, postScript, annotations);
        try {
            initIface(iface);
        } catch (Exception e) {
            iface.destroy();
            throw new XException(Utils.formatErr(e));
        }
        blockAndAddPersistentIface(loop, iface);
        if (mtu != null) {
            iface.setBaseMTU(mtu);
        }
        if (floodAllowed != null) {
            iface.setFloodAllowed(floodAllowed);
        }
        Logger.alert("tap device added: " + iface.getTap().getTap().dev);
        return iface.getTap().getTap().dev;
    }

    private void initIface(Iface iface) throws Exception {
        iface.init(buildIfaceInitParams());
        iface.setBaseMTU(defaultMtu);
        iface.setFloodAllowed(defaultFloodAllowed);
    }

    private IfaceInitParams buildIfaceInitParams() {
        return new IfaceInitParams(this, eventLoop.getSelectorEventLoop(), sock, packetCallback, users);
    }

    @Blocking
    private void blockAndAddPersistentIface(SelectorEventLoop loop, Iface iface) throws IOException {
        BlockCallback<Void, RuntimeException> cb = new BlockCallback<>();
        loop.runOnLoop(() -> {
            ifaces.put(iface, new IfaceTimer(loop, -1, iface));
            cb.succeeded();
        });
        cb.block();
    }

    private void recordIface(Iface iface) {
        final int IFACE_TIMEOUT = 60 * 1000;

        var timer = ifaces.get(iface);
        if (timer == null) {
            timer = new Switch.IfaceTimer(eventLoop.getSelectorEventLoop(), IFACE_TIMEOUT, iface);
        }
        timer.record(iface);
    }

    public void delTap(String devName) throws NotFoundException {
        Iface iface = null;
        for (Iface i : ifaces.keySet()) {
            if (!(i instanceof TapIface)) {
                continue;
            }
            TapIface tapIface = (TapIface) i;
            if (tapIface.getTap().getTap().dev.equals(devName)) {
                iface = i;
                break;
            }
        }
        if (iface == null) {
            throw new NotFoundException("tap", devName);
        }
        utilRemoveIface(iface);
    }

    public void addUserClient(String user, String password, int vni, IPPort remoteAddr) throws AlreadyExistException, IOException, XException {
        user = formatUserName(user);

        for (Iface i : ifaces.keySet()) {
            if (!(i instanceof UserClientIface)) {
                continue;
            }
            UserClientIface ucliIface = (UserClientIface) i;
            if (ucliIface.user.user.equals(user) && ucliIface.remote.equals(remoteAddr)) {
                throw new AlreadyExistException("user-client", user);
            }
        }

        SelectorEventLoop loop = eventLoop.getSelectorEventLoop();

        Aes256Key key = new Aes256Key(password);
        UserInfo info = new UserInfo(user, key, password, vni, defaultMtu, defaultFloodAllowed);

        UserClientIface iface = new UserClientIface(info, remoteAddr);

        try {
            initIface(iface);
        } catch (Exception e) {
            iface.destroy();
            throw new XException(Utils.formatErr(e));
        }
        blockAndAddPersistentIface(loop, iface);
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
            if (ucliIface.user.user.equals(user) && ucliIface.remote.equals(remoteAddr)) {
                iface = ucliIface;
                break;
            }
        }

        if (iface == null) {
            throw new NotFoundException("user-client", user);
        }
        utilRemoveIface(iface);
    }

    public void addRemoteSwitch(String alias, IPPort vxlanSockAddr, boolean addSwitchFlag) throws XException, IOException, AlreadyExistException {
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
        try {
            initIface(iface);
        } catch (Exception e) {
            iface.destroy();
            throw new XException(Utils.formatErr(e));
        }
        blockAndAddPersistentIface(loop, iface);
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

    private void sendPacket(SocketBuffer skb, Iface iface) {
        assert Logger.lowLevelDebug("sendPacket(" + skb + ", " + iface + ")");

        SwitchUtils.checkAndUpdateMss(skb, iface);
        if (Mirror.isEnabled("switch")) {
            Mirror.switchPacket(skb.pkt);
        }

        iface.sendPacket(skb);
    }

    private final CursorList<SocketBuffer> socketBuffersToBeHandled = new CursorList<>(128);

    private void onIfacePacketsArrive() {
        for (Iface iface : ifaces.keySet()) {
            SocketBuffer skb;
            while ((skb = iface.pollPacket()) != null) {
                preHandleInputSkb(skb);
            }
        }
        handleInputSkb();
    }

    private void onIfacePacketsArrive(CursorList<SocketBuffer> ls) {
        while (!ls.isEmpty()) {
            SocketBuffer skb = ls.remove(ls.size() - 1);
            preHandleInputSkb(skb);
        }
        handleInputSkb();
    }

    private void preHandleInputSkb(SocketBuffer skb) {
        if (initSKB(skb)) {
            return;
        }
        // check whether the packet's src and dst mac are the same
        if (skb.pkt.getSrc().equals(skb.pkt.getDst())) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "input packet with the same src and dst: " + skb.pkt.description());
            return;
        }

        recordIface(skb.devin);

        socketBuffersToBeHandled.add(skb);
    }

    private boolean initSKB(SocketBuffer skb) {
        int vni = skb.vni;
        Table table = swCtx.getTable(vni);
        if (table == null) {
            assert Logger.lowLevelDebug("vni not defined: " + vni);
            return true;
        }
        skb.table = table;

        if (Mirror.isEnabled("switch")) {
            Mirror.switchPacket(skb.pkt);
        }
        return false;
    }

    private void handleInputSkb() {
        for (SocketBuffer skb : socketBuffersToBeHandled) {
            try {
                netStack.devInput(skb);
            } catch (Throwable t) {
                Logger.error(LogType.IMPROPER_USE, "unexpected exception in devInput", t);
            }
        }
        socketBuffersToBeHandled.clear();
    }

    private void onIfaceDown(Iface iface) {
        utilRemoveIface(iface);
    }

    private final IfaceInitParams.PacketCallback packetCallback = new IfaceInitParams.PacketCallback() {
        @Override
        public void alertPacketsArrive() {
            onIfacePacketsArrive();
        }

        @Override
        public void alertDeviceDown(Iface iface) {
            onIfaceDown(iface);
        }
    };

    private void utilRemoveIface(Iface iface) {
        var timer = ifaces.remove(iface);
        if (timer != null) {
            timer.cancel();
        }

        for (var table : tables.values()) {
            table.macTable.disconnect(iface);
        }
        Logger.warn(LogType.ALERT, iface + " disconnected from Switch:" + alias);

        iface.destroy();
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
}
