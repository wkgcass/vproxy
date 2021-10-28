package io.vproxy.vswitch;

import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.component.elgroup.EventLoopGroupAttach;
import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.selector.PeriodicEvent;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.Timer;
import io.vproxy.base.util.*;
import io.vproxy.base.util.anno.Blocking;
import io.vproxy.base.util.callback.BlockCallback;
import io.vproxy.base.util.coll.IntMap;
import io.vproxy.base.util.coll.RingQueue;
import io.vproxy.base.util.crypto.Aes256Key;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.ClosedException;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.base.util.exception.XException;
import io.vproxy.base.util.objectpool.CursorList;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.component.secure.SecurityGroup;
import io.vproxy.vfd.*;
import io.vproxy.vfd.posix.PosixDatagramFD;
import io.vproxy.vmirror.Mirror;
import io.vproxy.vpacket.EthernetPacket;
import io.vproxy.vpacket.Ipv4Packet;
import io.vproxy.vpacket.Ipv6Packet;
import io.vproxy.vpacket.PartialPacket;
import io.vproxy.vswitch.dispatcher.BPFMapKeySelector;
import io.vproxy.vswitch.iface.*;
import io.vproxy.vswitch.plugin.FilterResult;
import io.vproxy.vswitch.plugin.IfaceWatcher;
import io.vproxy.vswitch.stack.NetworkStack;
import io.vproxy.vswitch.stack.conntrack.EnhancedTCPEntry;
import io.vproxy.vswitch.stack.conntrack.EnhancedUDPEntry;
import io.vproxy.vswitch.stack.conntrack.Fastpath;
import io.vproxy.vswitch.util.CSumRecalcType;
import io.vproxy.vswitch.util.SwitchUtils;
import io.vproxy.vswitch.util.UMemChunkByteArray;
import io.vproxy.vswitch.util.UserInfo;
import io.vproxy.xdp.BPFMap;
import io.vproxy.xdp.BPFMode;
import io.vproxy.xdp.UMem;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Switch {
    public final String alias;
    public final IPPort vxlanBindingAddress;
    public final EventLoopGroup eventLoopGroup;
    private NetEventLoop eventLoop;
    private PeriodicEvent refreshCacheEvent;
    private int macTableTimeout;
    private int arpTableTimeout;
    public SecurityGroup bareVXLanAccess;
    public final IfaceParams defaultIfaceParams = new IfaceParams();

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
                  int macTableTimeout, int arpTableTimeout, SecurityGroup bareVXLanAccess)
        throws AlreadyExistException, ClosedException {
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
                if (vxlanBindingAddress.getAddress().isBroadcast()) {
                    if (sock instanceof PosixDatagramFD) {
                        ((PosixDatagramFD) sock).ensureDummyFD();
                    }
                } else {
                    sock.bind(vxlanBindingAddress);
                }
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
                net.conntrack.removeTcpListen(entry.listening);
            }
            for (var entry : net.conntrack.listTcpEntries()) {
                entry.destroy();
                net.conntrack.removeTcp(entry.source, entry.destination);
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
        VirtualNetwork t = new VirtualNetwork(swCtx, vni, eventLoop, v4network, v6network, macTableTimeout, arpTableTimeout, annotations);
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
                        IfaceParams ifaceParams) throws AlreadyExistException, XException {
        user = formatUserName(user);
        Aes256Key key = new Aes256Key(password);
        ifaceParams.fillNullFields(defaultIfaceParams);
        UserInfo old = users.putIfAbsent(user, new UserInfo(user, key, password, vni, ifaceParams));
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

    public TapIface addTap(String dev, int vni, String postScript) throws XException, IOException {
        NetEventLoop netEventLoop = eventLoop;
        if (netEventLoop == null) {
            throw new XException("the switch " + alias + " is not bond to any event loop, cannot add tap device");
        }
        SelectorEventLoop loop = netEventLoop.getSelectorEventLoop();

        FDs fds = FDProvider.get().getProvided();
        if (!(fds instanceof FDsWithTap)) {
            throw new IOException("tap is not supported by " + fds + ", use -Dvfd=posix or -Dvfd=windows");
        }
        TapIface iface = new TapIface(dev, vni, postScript);
        try {
            initIface(iface);
        } catch (Exception e) {
            iface.destroy();
            throw new XException(Utils.formatErr(e));
        }
        blockAndAddPersistentIface(loop, iface);
        Logger.alert("tap device added: " + iface.getTap().getTap().dev);
        return iface;
    }

    private void initIface(Iface iface) throws Exception {
        iface.init(buildIfaceInitParams());
        iface.getParams().set(defaultIfaceParams);
    }

    private final AtomicInteger ifaceIndexes = new AtomicInteger(0); // only increases, never decreases

    private IfaceInitParams buildIfaceInitParams() {
        return new IfaceInitParams(ifaceIndexes.incrementAndGet(), this, eventLoop.getSelectorEventLoop(), sock, packetCallback, users);
    }

    @Blocking
    private void blockAndAddPersistentIface(SelectorEventLoop loop, Iface iface) {
        BlockCallback<Void, RuntimeException> cb = new BlockCallback<>();
        loop.runOnLoop(() -> {
            IfaceTimer timer = ifaces.get(iface);
            if (timer != null) {
                timer.setTimeout(-1);
            } else {
                timer = new IfaceTimer(loop, -1, iface);
                timer.resetTimer();
                ifaces.put(iface, timer);
            }
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
            timer.record();
        } else {
            timer.resetTimer();
        }
    }

    public void delIface(String ifaceName) throws NotFoundException {
        Iface iface = null;
        for (Iface i : ifaces.keySet()) {
            if (i.name().equals(ifaceName)) {
                iface = i;
                break;
            }
        }
        if (iface == null) {
            throw new NotFoundException("iface", ifaceName);
        }
        utilRemoveIface(iface);
    }

    public TunIface addTun(String dev, int vni, MacAddress mac, String postScript) throws XException, IOException {
        NetEventLoop netEventLoop = eventLoop;
        if (netEventLoop == null) {
            throw new XException("the switch " + alias + " is not bond to any event loop, cannot add tun device");
        }
        SelectorEventLoop loop = netEventLoop.getSelectorEventLoop();

        FDs fds = FDProvider.get().getProvided();
        if (!(fds instanceof FDsWithTap)) {
            throw new IOException("tun is not supported by " + fds + ", use -Dvfd=posix or -Dvfd=windows");
        }
        TunIface iface = new TunIface(dev, vni, mac, postScript);
        try {
            initIface(iface);
        } catch (Exception e) {
            iface.destroy();
            throw new XException(Utils.formatErr(e));
        }
        blockAndAddPersistentIface(loop, iface);
        Logger.alert("tun device added: " + iface.getTun().getTap().dev);
        return iface;
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
        UserInfo info = new UserInfo(user, key, password, vni, defaultIfaceParams);

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

    public VLanAdaptorIface addVLanAdaptor(String parentIfaceName, int vlan, int localVni) throws XException, AlreadyExistException, NotFoundException {
        NetEventLoop netEventLoop = eventLoop;
        if (netEventLoop == null) {
            throw new XException("the switch " + alias + " is not bond to any event loop, cannot add vlan adaptor");
        }
        SelectorEventLoop loop = netEventLoop.getSelectorEventLoop();

        Iface parentIface = null;
        for (var iface : ifaces.keySet()) {
            if (iface.name().equals(parentIfaceName)) {
                parentIface = iface;
                break;
            }
        }
        if (parentIface == null) {
            throw new NotFoundException("iface", parentIfaceName);
        }
        var vif = new VLanAdaptorIface(parentIface, vlan, localVni);
        parentIface.addVLanAdaptor(vif);

        try {
            initIface(vif);
        } catch (Exception e) {
            vif.destroy();
            throw new XException(Utils.formatErr(e));
        }

        blockAndAddPersistentIface(loop, vif);
        vif.setReady();

        return vif;
    }

    public XDPIface addXDP(String nic, BPFMap map, BPFMap macMap, UMem umem,
                           int queueId, int rxRingSize, int txRingSize, BPFMode mode, boolean zeroCopy,
                           int busyPollBudget, boolean rxGenChecksum,
                           int vni, BPFMapKeySelector keySelector, boolean offload) throws XException, AlreadyExistException {
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

        var blockingCallback = new BlockCallback<XDPIface, XException>();
        loop.runOnLoop(() -> {
            var iface = new XDPIface(nic, map, macMap, umem,
                queueId, rxRingSize, txRingSize, mode, zeroCopy,
                busyPollBudget, rxGenChecksum,
                vni, keySelector, offload);
            try {
                initIface(iface);
            } catch (Exception e) {
                iface.destroy();
                blockingCallback.failed(new XException(Utils.formatErr(e)));
                return;
            }
            blockingCallback.succeeded(iface);
        });
        var iface = blockingCallback.block();

        blockAndAddPersistentIface(loop, iface);

        return iface;
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
        assert Logger.lowLevelDebug("sendPacket(" + pkb + ", " + iface.name() + ")");

        // handle fastpath
        if (pkb.fastpath) {
            assert Logger.lowLevelDebug("try to handle fastpath: " + pkb.tcp + " or " + pkb.udp);
            pkb.fastpath = false;
            if (pkb.tcp instanceof EnhancedTCPEntry) {
                var tcp = (EnhancedTCPEntry) pkb.tcp;
                tcp.fastpath = new Fastpath(iface, pkb.vni, pkb.pkt.getSrc(), pkb.pkt.getDst());
                assert Logger.lowLevelDebug("recording tcp fastpath on output: " + tcp.fastpath);
            } else if (pkb.udp instanceof EnhancedUDPEntry) {
                var udp = (EnhancedUDPEntry) pkb.udp;
                udp.fastpath = new Fastpath(iface, pkb.vni, pkb.pkt.getSrc(), pkb.pkt.getDst());
                assert Logger.lowLevelDebug("recording udp fastpath on output: " + udp.fastpath);
            }
        } else {
            assert Logger.lowLevelDebug("fastpath flag not set, will not set info into fastpath");
        }

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

        // need to remove unused vlan
        if (pkb.pkt.getVlan() == EthernetPacket.PENDING_VLAN_CODE) {
            if (!(iface instanceof VLanAdaptorIface)) {
                pkb.pkt.setVlan(EthernetPacket.NO_VLAN_CODE);
            }
        }

        // check whether the iface is disabled
        if (!pkb.assumeIfaceEnabled && iface.isDisabled()) {
            assert Logger.lowLevelDebug("iface " + iface.name() + " is disabled, drop the packet when sending");
            return;
        }
        pkb.assumeIfaceEnabled = false; // clear the state

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

    private void onIfacePacketsArrive(Iface iface) {
        PacketBuffer pkb;
        while ((pkb = iface.pollPacket()) != null) {
            pkb.ifaceInput = true;
            preHandleInputPkb(pkb);
        }
        handleInputPkb();
    }

    private void onIfacePacketsArrive(RingQueue<PacketBuffer> ls) {
        PacketBuffer pkb;
        while ((pkb = ls.poll()) != null) {
            pkb.ifaceInput = true;
            preHandleInputPkb(pkb);
        }
        handleInputPkb();
    }

    private void onIfacePacketsArrive(PacketBuffer pkb) {
        pkb.ifaceInput = true;
        preHandleInputPkb(pkb);
        handleInputPkb();
    }

    private void preHandleInputPkb(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("received packet: " + pkb);
        var fullbufBackup = pkb.fullbuf;
        if (__preHandleInputPkb0(pkb)) {
            packetBuffersToBeHandled.add(pkb);
            if (pkb.fullbuf != fullbufBackup) {
                releaseUMemChunkIfPossible(fullbufBackup);
            }
        } else {
            releaseUMemChunkIfPossible(fullbufBackup);
        }
    }

    private boolean __preHandleInputPkb0(PacketBuffer pkb) {
        // execute pre handlers
        FilterResult preHandlersResult = SwitchUtils.applyFilters(pkb.devin.getPreHandlers(), UnsupportedPacketFilterHelper.instance, pkb);
        if (preHandlersResult == FilterResult.DROP) {
            assert Logger.lowLevelDebug("pre handlers return DROP");
            return false;
        }
        if (preHandlersResult != FilterResult.PASS) {
            Logger.error(LogType.IMPROPER_USE, "pre handlers can only return DROP or PASS");
            return false;
        }

        // ensure the dev is recorded
        recordIface(pkb.devin);

        // check whether the iface is disabled
        if (!pkb.assumeIfaceEnabled && pkb.devin.isDisabled()) {
            assert Logger.lowLevelDebug("iface " + pkb.devin.name() + " is disabled");
            return false;
        }
        pkb.assumeIfaceEnabled = false; // clear the state

        // try to handle vlan
        if (pkb.pkt != null) { // note that tun dev will generate ip pkt only
            int vlan = pkb.pkt.getVlan();
            if (vlan >= 0) {
                VLanAdaptorIface vif = pkb.devin.lookupVLanAdaptor(vlan);
                if (vif == null) {
                    assert Logger.lowLevelDebug("unable to find proper vlan adaptor for " + pkb);
                    return false;
                } else {
                    assert Logger.lowLevelDebug("handled by vlan adaptor " + vif.remoteVLan);
                    vif.handle(pkb);

                    // execute pre handlers
                    preHandlersResult = SwitchUtils.applyFilters(vif.getPreHandlers(), UnsupportedPacketFilterHelper.instance, pkb);
                    if (preHandlersResult == FilterResult.DROP) {
                        assert Logger.lowLevelDebug("pre handlers return DROP");
                        return false;
                    }
                    if (preHandlersResult != FilterResult.PASS) {
                        Logger.error(LogType.IMPROPER_USE, "pre handlers can only return DROP or PASS");
                        return false;
                    }

                    // check whether the iface is disabled
                    if (!pkb.assumeIfaceEnabled && pkb.devin.isDisabled()) {
                        assert Logger.lowLevelDebug("iface " + pkb.devin.name() + " is disabled");
                        return false;
                    }
                    pkb.assumeIfaceEnabled = false; // clear the state
                }
            }
        }

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
            var route = network.routeTable.lookup(pkb.ipPkt.getDst());
            if (route != null && route.isLocalDirect(network.vni)) {
                assert Logger.lowLevelDebug("packet from " + pkb.devin + " to " + pkb.ipPkt.getDst() + " requires no routing");
                var mac = network.arpTable.lookup(pkb.ipPkt.getDst());
                if (mac != null) {
                    buildEthernetHeaderForTunDev(pkb, mac);
                } else {
                    generateArpOrNdpRequestForTunDev(pkb);
                }
            } else if (route == null || route.ip == null) {
                assert Logger.lowLevelDebug("packet from " + pkb.devin + " to " + pkb.ipPkt.getDst() + " " +
                    "is not gateway routing, or route does not exist");
                var mac = network.ips.lookup(pkb.ipPkt.getDst());
                if (mac == null) {
                    assert Logger.lowLevelDebug("the target address is not a synthetic ip");
                    var ipmac = network.ips.findAnyIPForRouting(pkb.ipPkt instanceof Ipv6Packet);
                    if (ipmac == null) {
                        assert Logger.lowLevelDebug("no routable ip found");
                        return false; // drop
                    }
                    mac = ipmac.mac;
                }
                buildEthernetHeaderForTunDev(pkb, mac);
            } else {
                assert Logger.lowLevelDebug("packet from " + pkb.devin + " to " + pkb.ipPkt.getDst() + " requires gateway routing to " + route.ip);
                var mac = network.arpTable.lookup(route.ip);
                if (mac == null) {
                    mac = network.ips.lookup(route.ip);
                }
                if (mac != null) {
                    buildEthernetHeaderForTunDev(pkb, mac);
                } else {
                    generateArpOrNdpRequestForTunDev(pkb, route.ip);
                }
            }
        }
        assert pkb.pkt != null;

        // handle it by packet filter
        var ingressFilters = pkb.devin.getIngressFilters();
        if (ingressFilters.isEmpty()) {
            assert Logger.lowLevelDebug("no ingress filter on " + pkb.devin);
        } else {
            assert Logger.lowLevelDebug("run ingress filters " + ingressFilters + " on " + pkb);
            var res = SwitchUtils.applyFilters(ingressFilters, packetFilterHelper, pkb);
            if (res != FilterResult.PASS) {
                handleIngressFilterResult(pkb, res);
                return false;
            }
            assert Logger.lowLevelDebug("the filter returns pass");
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
            assert Logger.lowLevelDebug("releasing the umem chunk after packet handled or dropped or rewrote");
            ((UMemChunkByteArray) fullBuf).releaseRef();
        }
    }

    private void handleIngressFilterResult(PacketBuffer pkb, FilterResult res) {
        if (res == FilterResult.DROP) {
            assert Logger.lowLevelDebug("ingress filter drops the packet: " + pkb);
            return;
        }
        if (res == FilterResult.REDIRECT) {
            assert Logger.lowLevelDebug("ingress filter redirects the packet: " + pkb + ", " + pkb.devredirect);
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
                preHandleInputPkb(pkb); // the packet is not actually handled yet, so run the preHandle would be enough
            } else {
                sendPacket(pkb, redirect);
            }
            return;
        } else if (res == FilterResult.TX) {
            assert Logger.lowLevelDebug("ingress filter returns TX: " + pkb);
            sendPacket(pkb, pkb.devin);
            return;
        }
        Logger.error(LogType.IMPROPER_USE, "filter returns unexpected result " + res + " on packet ingress");
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
        generateArpOrNdpRequestForTunDev(pkb, pkb.ipPkt.getDst());
    }

    private void generateArpOrNdpRequestForTunDev(PacketBuffer pkb, IP target) {
        if (target instanceof IPv4) {
            generateArpRequestForTunDev(pkb, (IPv4) target);
        } else {
            generateNdpRequestForTunDev(pkb, (IPv6) target);
        }
    }

    private void generateArpRequestForTunDev(PacketBuffer pkb, IPv4 target) {
        var tun = (TunIface) pkb.devin;
        var arp = SwitchUtils.buildArpPacket(Consts.ARP_PROTOCOL_OPCODE_REQ,
            SwitchUtils.ZERO_MAC, target,
            tun.mac, (IPv4) pkb.ipPkt.getSrc());
        var ether = SwitchUtils.buildEtherArpPacket(SwitchUtils.BROADCAST_MAC, tun.mac, arp);

        assert Logger.lowLevelDebug("original input " + pkb + " is replaced with arp request " + ether);
        pkb.replacePacket(ether);
    }

    private void generateNdpRequestForTunDev(PacketBuffer pkb, IPv6 target) {
        var tun = (TunIface) pkb.devin;
        var ipv6 = SwitchUtils.buildNeighborSolicitationPacket(target, tun.mac, (IPv6) pkb.ipPkt.getSrc());
        var ether = SwitchUtils.buildEtherIpPacket(SwitchUtils.BROADCAST_MAC, tun.mac, ipv6);

        assert Logger.lowLevelDebug("original input " + pkb + " is replaced with ndp request " + ether);
        pkb.replacePacket(ether);
    }

    private void handleInputPkb() {
        for (PacketBuffer pkb : packetBuffersToBeHandled) {
            // clear csum if required
            if (pkb.devin.getParams().getCSumRecalc() != CSumRecalcType.none) {
                if (pkb.ipPkt != null) {
                    if (pkb.ipPkt.getPacket() != null) {
                        assert Logger.lowLevelDebug("checksum cleared for " + pkb.ipPkt.getPacket().description());
                        pkb.ipPkt.getPacket().clearChecksum();
                    }
                    if (pkb.devin.getParams().getCSumRecalc() == CSumRecalcType.all) {
                        assert Logger.lowLevelDebug("checksum cleared for " + pkb.ipPkt.description());
                        pkb.ipPkt.clearChecksum();
                    }
                }
            }

            // the umem chunk need to be released after processing
            var fullbufBackup = pkb.fullbuf;

            // try fastpath
            boolean fastpathHandled = false;
            if (pkb.fastpath) {
                pkb.fastpath = false; // clear the field
                if (pkb.udp != null) {
                    assert Logger.lowLevelDebug("fastpath for udp on input: " + pkb);
                    pkb.udp.update();
                    pkb.ensurePartialPacketParsed(PartialPacket.LEVEL_HANDLED_FIELDS);
                    pkb.udp.listenEntry.receivingQueue.store(
                        pkb.ipPkt.getSrc(), pkb.udpPkt.getSrcPort(),
                        pkb.udpPkt.getData().getRawPacket(0));
                    fastpathHandled = true;
                } else {
                    assert Logger.lowLevelDebug("fastpath set but pkb.udp is null");
                }
            }

            if (!fastpathHandled) {
                assert Logger.lowLevelDebug("no fastpath, handle normally: " + pkb);

                // normal input
                try {
                    netStack.devInput(pkb);
                } catch (Throwable t) {
                    Logger.error(LogType.IMPROPER_USE, "unexpected exception in devInput", t);
                }
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
        public void alertPacketsArrive(Iface iface) {
            onIfacePacketsArrive(iface);
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
        Logger.warn(LogType.ALERT, iface.name() + " disconnected from Switch:" + alias);

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

        void record() {
            if (ifaces.putIfAbsent(iface, this) == null) {
                Logger.alert(iface.name() + " connected to Switch:" + alias);
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
