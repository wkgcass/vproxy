package vproxy.vswitch;

import vproxy.base.component.elgroup.EventLoopGroup;
import vproxy.base.component.elgroup.EventLoopGroupAttach;
import vproxy.base.connection.NetEventLoop;
import vproxy.base.selector.PeriodicEvent;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.util.Timer;
import vproxy.base.util.*;
import vproxy.base.util.anno.Blocking;
import vproxy.base.util.callback.BlockCallback;
import vproxy.base.util.coll.IntMap;
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
import vproxy.vpacket.EthernetPacket;
import vproxy.vpacket.Ipv4Packet;
import vproxy.vswitch.dispatcher.BPFMapKeySelector;
import vproxy.vswitch.iface.*;
import vproxy.vswitch.plugin.FilterResult;
import vproxy.vswitch.plugin.IfaceWatcher;
import vproxy.vswitch.stack.NetworkStack;
import vproxy.vswitch.util.SwitchUtils;
import vproxy.vswitch.util.UMemChunkByteArray;
import vproxy.vswitch.util.UserInfo;
import vproxy.xdp.BPFMap;
import vproxy.xdp.BPFMode;
import vproxy.xdp.UMem;

import java.io.IOException;
import java.util.*;

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

    private final List<IfaceWatcher> ifaceWatchers = new LinkedList<>();

    private boolean started = false;
    private boolean wantStart = false;

    private final Map<String, UserInfo> users = new HashMap<>();
    private DatagramFD sock;
    private final IntMap<VirtualNetwork> networks = new IntMap<>();
    private final Map<Iface, IfaceTimer> ifaces = new LinkedHashMap<>();

    private final Map<String, UMem> umems = new HashMap<>();

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
        networks::get,
        users::get,
        () -> eventLoop.getSelectorEventLoop(),
        this::onIfacePacketsArrive,
        this::utilRemoveIface,
        this::initIface,
        this::recordIface
    );
    private final PacketFilterHelper packetFilterHelper = new PacketFilterHelper(
        this::sendPacket
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
        networks.values().forEach(t -> t.setLoop(loop));
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
        for (var net : networks.values()) {
            for (var entry : net.conntrack.listListenEntries()) {
                entry.destroy();
                net.conntrack.removeListen(entry.listening);
            }
            for (var entry : net.conntrack.listTcpEntries()) {
                entry.destroy();
                net.conntrack.remove(entry.source, entry.destination);
            }
        }
    }

    private void cancelNetworks() {
        for (var net : networks.values()) {
            net.clearCache();
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
        cancelNetworks();
        started = false;
    }

    public synchronized void destroy() {
        wantStart = false;
        releaseSock();
        stop();
    }

    private void refreshCache() {
        for (VirtualNetwork t : networks.values()) {
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

    private void refreshArpCache(VirtualNetwork t, IP ip, MacAddress mac) {
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
        for (var net : networks.values()) {
            net.setMacTableTimeout(macTableTimeout);
        }
    }

    public void setArpTableTimeout(int arpTableTimeout) {
        this.arpTableTimeout = arpTableTimeout;
        for (var net : networks.values()) {
            net.setArpTableTimeout(arpTableTimeout);
        }
    }

    public IntMap<VirtualNetwork> getNetworks() {
        return networks;
    }

    public VirtualNetwork getNetwork(int vni) throws NotFoundException {
        VirtualNetwork t = networks.get(vni);
        if (t == null) {
            throw new NotFoundException("vni", "" + vni);
        }
        return t;
    }

    public VirtualNetwork addNetwork(int vni, Network v4network, Network v6network, Annotations annotations) throws AlreadyExistException, XException {
        if (networks.containsKey(vni)) {
            throw new AlreadyExistException("vni " + vni + " already exists in switch " + alias);
        }
        if (eventLoop == null) {
            throw new XException("the switch " + alias + " is not bond to any event loop, cannot add vni");
        }
        VirtualNetwork t = new VirtualNetwork(vni, eventLoop, v4network, v6network, macTableTimeout, arpTableTimeout, annotations);
        networks.put(vni, t);
        return t;
    }

    public void delNetwork(int vni) throws NotFoundException {
        VirtualNetwork t = networks.remove(vni);
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

    public TapIface addTap(String devPattern, int vni, String postScript) throws XException, IOException {
        return addTap(devPattern, vni, postScript, null, null);
    }

    public TapIface addTap(String devPattern, int vni, String postScript,
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
        TapIface iface = new TapIface(devPattern, vni, postScript);
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
        return iface;
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
    private void blockAndAddPersistentIface(SelectorEventLoop loop, Iface iface) {
        BlockCallback<Void, RuntimeException> cb = new BlockCallback<>();
        loop.runOnLoop(() -> {
            ifaces.put(iface, new IfaceTimer(loop, -1, iface));
            cb.succeeded();

            ifaceAdded(iface);
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

    public TunIface addTun(String devPattern, int vni, MacAddress mac, String postScript)
        throws XException, IOException {
        return addTun(devPattern, vni, mac, postScript, defaultMtu, defaultFloodAllowed);
    }

    public TunIface addTun(String devPattern, int vni, MacAddress mac, String postScript,
                           Integer mtu, Boolean floodAllowed) throws XException, IOException {
        NetEventLoop netEventLoop = eventLoop;
        if (netEventLoop == null) {
            throw new XException("the switch " + alias + " is not bond to any event loop, cannot add tun device");
        }
        SelectorEventLoop loop = netEventLoop.getSelectorEventLoop();

        FDs fds = FDProvider.get().getProvided();
        if (!(fds instanceof FDsWithTap)) {
            throw new IOException("tun is not supported by " + fds + ", use -Dvfd=posix or -Dvfd=windows");
        }
        TunIface iface = new TunIface(devPattern, vni, mac, postScript);
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
        Logger.alert("tun device added: " + iface.getTun().getTap().dev);
        return iface;
    }

    public void delTun(String devName) throws NotFoundException {
        Iface iface = null;
        for (Iface i : ifaces.keySet()) {
            if (!(i instanceof TunIface)) {
                continue;
            }
            TunIface tunIface = (TunIface) i;
            if (tunIface.getTun().getTap().dev.equals(devName)) {
                iface = i;
                break;
            }
        }
        if (iface == null) {
            throw new NotFoundException("tun", devName);
        }
        utilRemoveIface(iface);
    }

    public UserClientIface addUserClient(String user, String password, int vni, IPPort remoteAddr) throws AlreadyExistException, XException {
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

        UserClientIface iface = new UserClientIface(info, key, remoteAddr);

        try {
            initIface(iface);
        } catch (Exception e) {
            iface.destroy();
            throw new XException(Utils.formatErr(e));
        }
        blockAndAddPersistentIface(loop, iface);

        return iface;
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

    public RemoteSwitchIface addRemoteSwitch(String alias, IPPort vxlanSockAddr, boolean addSwitchFlag) throws XException, AlreadyExistException {
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
        var iface = new RemoteSwitchIface(alias, vxlanSockAddr, addSwitchFlag);
        try {
            initIface(iface);
        } catch (Exception e) {
            iface.destroy();
            throw new XException(Utils.formatErr(e));
        }
        blockAndAddPersistentIface(loop, iface);

        return iface;
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

    public XDPIface addXDP(String nic, BPFMap map, UMem umem,
                           int queueId, int rxRingSize, int txRingSize, BPFMode mode, boolean zeroCopy,
                           int busyPollBudget,
                           int vni, BPFMapKeySelector keySelector) throws XException, AlreadyExistException {
        NetEventLoop netEventLoop = eventLoop;
        if (netEventLoop == null) {
            throw new XException("the switch " + this.alias + " is not bond to any event loop, cannot add xdp");
        }
        SelectorEventLoop loop = netEventLoop.getSelectorEventLoop();

        if (!umems.containsValue(umem)) {
            throw new XException("the provided umem does not belong to this switch");
        }

        for (Iface i : ifaces.keySet()) {
            if (!(i instanceof XDPIface)) {
                continue;
            }
            XDPIface xdpiface = (XDPIface) i;
            if (nic.equals(xdpiface.nic)) {
                throw new AlreadyExistException("xdp", alias);
            }
        }

        var iface = new XDPIface(nic, map, umem,
            queueId, rxRingSize, txRingSize, mode, zeroCopy,
            busyPollBudget,
            vni, keySelector);
        try {
            initIface(iface);
        } catch (Exception e) {
            iface.destroy();
            throw new XException(Utils.formatErr(e));
        }

        blockAndAddPersistentIface(loop, iface);

        return iface;
    }

    public void delXDP(String nic) throws NotFoundException {
        Iface iface = null;
        for (Iface i : ifaces.keySet()) {
            if (!(i instanceof XDPIface)) {
                continue;
            }
            XDPIface xdp = (XDPIface) i;
            if (nic.equals(xdp.nic)) {
                iface = i;
                break;
            }
        }
        if (iface == null) {
            throw new NotFoundException("xdp", nic);
        }
        utilRemoveIface(iface);
    }

    public UMem addUMem(String alias, int chunksSize, int fillRingSize, int compRingSize,
                        int frameSize) throws AlreadyExistException, IOException {
        if (umems.containsKey(alias)) {
            throw new AlreadyExistException("umem", alias);
        }
        var umem = UMem.create(alias, chunksSize, fillRingSize, compRingSize, frameSize, SwitchUtils.RCV_HEAD_PRESERVE_LEN);
        umems.put(alias, umem);
        return umem;
    }

    public void delUMem(String alias) throws NotFoundException, XException {
        UMem umem = umems.get(alias);
        if (umem == null) {
            throw new NotFoundException("umem", alias);
        }

        for (Iface iface : ifaces.keySet()) {
            if (iface instanceof XDPIface) {
                XDPIface xdp = ((XDPIface) iface);
                if (xdp.umem.equals(umem)) {
                    throw new XException("umem " + alias + " is used by xdp " + xdp.nic);
                }
            }
        }

        umems.remove(alias);
        umem.release();
    }

    public List<UMem> getUMems() {
        List<UMem> ls = new ArrayList<>(umems.size());
        ls.addAll(umems.values());
        return ls;
    }

    public Map<String, UserInfo> getUsers() {
        var ret = new LinkedHashMap<String, UserInfo>();
        for (var entry : users.entrySet()) {
            ret.put(entry.getKey().replace(Consts.USER_PADDING, ""), entry.getValue());
        }
        return ret;
    }

    private void sendPacket(PacketBuffer pkb, Iface iface) {
        assert Logger.lowLevelDebug("sendPacket(" + pkb + ", " + iface + ")");

        // handle it by packet filter
        var egressFilters = iface.getEgressFilters();
        if (egressFilters.isEmpty()) {
            assert Logger.lowLevelDebug("no egress filter on " + iface);
        } else {
            var devoutBackup = pkb.devout; // in case of recursive output
            pkb.devout = iface;

            assert Logger.lowLevelDebug("run egress filters " + egressFilters + " on " + pkb);
            var res = SwitchUtils.applyFilters(egressFilters, packetFilterHelper, pkb);

            pkb.devout = devoutBackup; // restore devout

            if (res != FilterResult.PASS) {
                handleEgressFilterResult(pkb, res);
                return;
            }
            assert Logger.lowLevelDebug("the filter returns pass");
        }

        SwitchUtils.checkAndUpdateMss(pkb, iface);
        if (Mirror.isEnabled("switch")) {
            Mirror.switchPacket(pkb.pkt);
        }

        iface.sendPacket(pkb);
    }

    private void referenceUMemChunkIfPossible(ByteArray fullBuf) {
        if (fullBuf instanceof UMemChunkByteArray) {
            assert Logger.lowLevelDebug("reference the umem chunk before packet re-input from egress");
            ((UMemChunkByteArray) fullBuf).reference();
        }
    }

    private void handleEgressFilterResult(PacketBuffer pkb, FilterResult res) {
        if (res == FilterResult.DROP) {
            assert Logger.lowLevelDebug("egress filter drops the packet: " + pkb);
            return;
        }
        if (res == FilterResult.REDIRECT) {
            assert Logger.lowLevelDebug("egress filter redirects the packet: " + pkb + ", " + pkb.devredirect);
            var redirect = pkb.devredirect;
            var reinput = pkb.reinput;
            if (redirect == null && !reinput) {
                Logger.error(LogType.IMPROPER_USE, "filter returns REDIRECT, but devredirect is not set and reinput is false");
                return; // drop the packet
            }
            pkb.clearFilterFields();
            if (reinput) {
                assert Logger.lowLevelDebug("reinput the packet");
                pkb.network = null;
                if (redirect != null) {
                    assert Logger.lowLevelDebug("set devin to " + redirect);
                    pkb.devin = redirect;
                }
                // need to increase ref
                referenceUMemChunkIfPossible(pkb.fullbuf);
                onIfacePacketsArrive(pkb);
            } else {
                assert Logger.lowLevelDebug("send the packet to " + redirect);
                sendPacket(pkb, redirect);
            }
            return;
        }
        Logger.error(LogType.IMPROPER_USE, "filter returns unexpected result " + res + " on packet egress");
    }

    private final CursorList<PacketBuffer> packetBuffersToBeHandled = new CursorList<>(128);

    private void onIfacePacketsArrive() {
        for (Iface iface : ifaces.keySet()) {
            PacketBuffer pkb;
            while ((pkb = iface.pollPacket()) != null) {
                preHandleInputPkb(pkb);
            }
        }
        handleInputPkb();
    }

    private void onIfacePacketsArrive(CursorList<PacketBuffer> ls) {
        while (!ls.isEmpty()) {
            PacketBuffer pkb = ls.remove(ls.size() - 1);
            preHandleInputPkb(pkb);
        }
        handleInputPkb();
    }

    private void onIfacePacketsArrive(PacketBuffer pkb) {
        preHandleInputPkb(pkb);
        handleInputPkb();
    }

    private void preHandleInputPkb(PacketBuffer pkb) {
        var fullbufBackup = pkb.fullbuf;
        if (__preHandleInputPkb0(pkb)) {
            packetBuffersToBeHandled.add(pkb);
        } else {
            releaseUMemChunkIfPossible(fullbufBackup);
        }
    }

    private boolean __preHandleInputPkb0(PacketBuffer pkb) {
        // ensure the dev is recorded
        recordIface(pkb.devin);

        // init vpc network
        int vni = pkb.vni;
        VirtualNetwork network = swCtx.getNetwork(vni);
        if (network == null) {
            assert Logger.lowLevelDebug("vni not defined: " + vni);
            return false;
        }
        pkb.network = network;

        // init tun packets
        if (pkb.pkt == null) {
            if (!(pkb.devin instanceof TunIface)) {
                Logger.shouldNotHappen("only tun interfaces can input non-ethernet packets, but got " + pkb.devin);
                return false; // drop
            }
            var mac = network.arpTable.lookup(pkb.ipPkt.getDst());
            if (mac != null) {
                buildEthernetHeaderForTunDev(pkb, mac);
            } else {
                generateArpOrNdpRequestForTunDev(pkb);
            }
        }
        assert pkb.pkt != null;

        // handle it by packet filter
        var ingressFilters = pkb.devin.getIngressFilters();
        if (ingressFilters.isEmpty()) {
            assert Logger.lowLevelDebug("no ingress filter on " + pkb.devin);
        } else {
            assert Logger.lowLevelDebug("run ingress filters " + ingressFilters + " on " + pkb);
            var fullbufBackup = pkb.fullbuf;
            var res = SwitchUtils.applyFilters(ingressFilters, packetFilterHelper, pkb);
            if (res != FilterResult.PASS) {
                handleIngressFilterResult(fullbufBackup, pkb, res);
                return false;
            }
            assert Logger.lowLevelDebug("the filter returns pass");
        }

        // drop packets with vlan tag
        if (pkb.pkt instanceof EthernetPacket && ((EthernetPacket) pkb.pkt).getVlan() >= 0) {
            assert Logger.lowLevelDebug("vlan packets are dropped");
            return false;
        }

        // mirror the packet
        if (Mirror.isEnabled("switch")) {
            Mirror.switchPacket(pkb.pkt);
        }

        // check whether the packet's src and dst mac are the same
        if (pkb.pkt.getSrc().equals(pkb.pkt.getDst())) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "input packet with the same src and dst: " + pkb.pkt.description());
            return false;
        }

        return true;
    }

    private void releaseUMemChunkIfPossible(ByteArray fullBuf) {
        if (fullBuf instanceof UMemChunkByteArray) {
            assert Logger.lowLevelDebug("releasing the umem chunk after packet handled or dropped");
            ((UMemChunkByteArray) fullBuf).releaseRef();
        }
    }

    private void handleIngressFilterResult(ByteArray fullbufBackup, PacketBuffer pkb, FilterResult res) {
        if (res == FilterResult.DROP) {
            assert Logger.lowLevelDebug("ingress filter drops the packet: " + pkb);
            releaseUMemChunkIfPossible(fullbufBackup);
            return;
        }
        if (res == FilterResult.REDIRECT) {
            assert Logger.lowLevelDebug("ingress filter redirects the packet: " + pkb + ", " + pkb.devredirect);
            var redirect = pkb.devredirect;
            var reinput = pkb.reinput;
            if (redirect == null && !reinput) {
                Logger.error(LogType.IMPROPER_USE, "filter returns REDIRECT, but devredirect is not set and reinput is false");
                releaseUMemChunkIfPossible(fullbufBackup);
                return; // drop the packet
            }
            pkb.clearFilterFields();
            if (reinput) {
                assert Logger.lowLevelDebug("reinput the packet");
                pkb.network = null;
                if (redirect != null) {
                    assert Logger.lowLevelDebug("set devin to " + redirect);
                    pkb.devin = redirect;
                }
                preHandleInputPkb(pkb); // the packet is not actually handled yet, so run the preHandle would be enough
            } else {
                sendPacket(pkb, redirect);
                releaseUMemChunkIfPossible(fullbufBackup);
            }
            return;
        }
        Logger.error(LogType.IMPROPER_USE, "filter returns unexpected result " + res + " on packet ingress");
        releaseUMemChunkIfPossible(fullbufBackup);
    }

    private void buildEthernetHeaderForTunDev(PacketBuffer pkb, MacAddress mac) {
        TunIface tun = (TunIface) pkb.devin;
        assert pkb.pktOff >= 14; // should have enough space for ethernet header
        // dst
        int off = pkb.pktOff - 14;
        for (int i = 0; i < mac.bytes.length(); ++i) {
            pkb.fullbuf.set(off + i, mac.bytes.get(i));
        }
        // src
        off += 6;
        for (int i = 0; i < tun.mac.bytes.length(); ++i) {
            pkb.fullbuf.set(off + i, tun.mac.bytes.get(i));
        }
        // type
        off += 6;
        int dltype;
        if (pkb.ipPkt instanceof Ipv4Packet) {
            dltype = Consts.ETHER_TYPE_IPv4;
        } else {
            dltype = Consts.ETHER_TYPE_IPv6;
        }
        pkb.fullbuf.int16(off, dltype);

        EthernetPacket ether = new EthernetPacket();
        ether.from(pkb.fullbuf.sub(pkb.pktOff - 14, 14), pkb.ipPkt);

        pkb.pkt = ether;
        pkb.flags = 0;
    }

    private void generateArpOrNdpRequestForTunDev(PacketBuffer pkb) {
        if (pkb.ipPkt instanceof Ipv4Packet) {
            generateArpRequestForTunDev(pkb);
        } else {
            generateNdpRequestForTunDev(pkb);
        }
    }

    private void generateArpRequestForTunDev(PacketBuffer pkb) {
        var tun = (TunIface) pkb.devin;
        var arp = SwitchUtils.buildArpPacket(Consts.ARP_PROTOCOL_OPCODE_REQ,
            SwitchUtils.ZERO_MAC, (IPv4) pkb.ipPkt.getDst(),
            tun.mac, (IPv4) pkb.ipPkt.getSrc());
        var ether = SwitchUtils.buildEtherArpPacket(SwitchUtils.BROADCAST_MAC, tun.mac, arp);

        assert Logger.lowLevelDebug("original input " + pkb + " is replaced with arp request " + ether);
        pkb.replacePacket(ether);
    }

    private void generateNdpRequestForTunDev(PacketBuffer pkb) {
        var tun = (TunIface) pkb.devin;
        var ipv6 = SwitchUtils.buildNeighborSolicitationPacket((IPv6) pkb.ipPkt.getDst(), tun.mac, (IPv6) pkb.ipPkt.getSrc());
        var ether = SwitchUtils.buildEtherIpPacket(SwitchUtils.BROADCAST_MAC, tun.mac, ipv6);

        assert Logger.lowLevelDebug("original input " + pkb + " is replaced with ndp request " + ether);
        pkb.replacePacket(ether);
    }

    private void handleInputPkb() {
        for (PacketBuffer pkb : packetBuffersToBeHandled) {
            // the umem chunk need to be released after processing
            var fullbufBackup = pkb.fullbuf;

            try {
                netStack.devInput(pkb);
            } catch (Throwable t) {
                Logger.error(LogType.IMPROPER_USE, "unexpected exception in devInput", t);
            }

            releaseUMemChunkIfPossible(fullbufBackup);
        }
        packetBuffersToBeHandled.clear();

        for (Iface iface : ifaces.keySet()) {
            iface.completeTx();
        }
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

    private void ifaceAdded(Iface iface) {
        var watchers = this.ifaceWatchers;
        for (var watcher : watchers) {
            watcher.ifaceAdded(iface);
        }
    }

    private void utilRemoveIface(Iface iface) {
        var timer = ifaces.remove(iface);
        if (timer != null) {
            timer.cancel();
        }

        for (var net : networks.values()) {
            net.macTable.disconnect(iface);
        }
        Logger.warn(LogType.ALERT, iface + " disconnected from Switch:" + alias);

        iface.destroy();

        var watchers = this.ifaceWatchers;
        for (var watcher : watchers) {
            watcher.ifaceRemoved(iface);
        }
    }

    public List<IfaceWatcher> getIfaceWatchers() {
        return ifaceWatchers;
    }

    public boolean addIfaceWatcher(IfaceWatcher watcher) {
        if (ifaceWatchers.contains(watcher)) {
            return false;
        }
        this.ifaceWatchers.add(watcher);

        // init with existing ifaces
        for (var iface : ifaces.keySet()) {
            watcher.ifaceAdded(iface);
        }
        return true;
    }

    public boolean removeIfaceWatcher(IfaceWatcher watcher) {
        return ifaceWatchers.remove(watcher);
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
                ifaceAdded(iface);
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
