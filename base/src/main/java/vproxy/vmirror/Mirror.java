package vproxy.vmirror;

import vjson.JSON;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.util.*;
import vproxy.base.util.direct.DirectByteBuffer;
import vproxy.base.util.direct.DirectMemoryUtils;
import vproxy.base.util.thread.VProxyThread;
import vproxy.vfd.*;
import vproxy.vpacket.*;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Mirror {
    private static final Mirror mirror = new Mirror();

    private static final int LOAD_CONFIG_SUCCESS_WAIT_INTERVAL = 2 * 1000;
    private static final int LOAD_CONFIG_FAIL_WAIT_INTERVAL = 10 * 1000;

    private boolean initialized = false;
    private boolean enabled = false;
    private String conf;
    private long lastTimestamp = 0;
    private SelectorEventLoop loop;

    private List<MirrorConfig> mirrors = Collections.emptyList();
    private Set<String> enabledOrigins = Collections.emptySet();
    private List<FilterConfig> filters = Collections.emptyList();

    private Mirror() {
    }

    public static void init(String conf) throws Exception {
        if (mirror.initialized)
            throw new IllegalStateException("cannot initialize twice");
        mirror.conf = conf;
        mirror.loop = SelectorEventLoop.open();
        mirror.loop.loop(r -> VProxyThread.create(r, "mirror"));
        mirror.initialized = true;
        mirror.loadConfig();
        mirror.loop.delay(LOAD_CONFIG_SUCCESS_WAIT_INTERVAL, mirror::loadConfigAndSetTimer);
    }

    public static void destroy() {
        mirror.initialized = false;
        mirror.enabled = false;
        mirror.conf = null;
        mirror.lastTimestamp = 0;
        if (mirror.loop != null) {
            try {
                mirror.loop.close();
            } catch (IOException e) {
                Logger.shouldNotHappen("closing selector event loop for mirror failed", e);
            }
            mirror.loop = null;
        }
        mirror.destroyTaps(mirror.mirrors);
        mirror.mirrors = Collections.emptyList();
        mirror.filters = Collections.emptyList();
    }

    public static boolean isEnabled(String origin) {
        if (!mirror.enabled) {
            return false;
        }
        return mirror.enabledOrigins.contains(origin);
    }

    public static void switchPacket(AbstractEthernetPacket packet) {
        if (!mirror.enabled) return;

        Set<MirrorConfig> mirrors = new HashSet<>();
        if (packet.getPacket() instanceof AbstractIpPacket) {
            AbstractIpPacket ipPkt = (AbstractIpPacket) packet.getPacket();
            checkHelper(mirrors, "switch", f -> f.matchIp(packet.getSrc(), packet.getDst(), ipPkt.getSrc(), ipPkt.getDst()));
        } else {
            checkHelper(mirrors, "switch", f -> f.matchEthernet(packet.getSrc(), packet.getDst()));
        }

        for (MirrorConfig c : mirrors) {
            mirror.sendPacket(c.tap, packet);
        }
    }

    public static void mirror(MirrorData data) {
        if (!mirror.enabled) return;

        if (data.macSrc == null || data.macDst == null) {
            Logger.error(LogType.IMPROPER_USE, "should not set mac src nor dst to null");
            return;
        }
        if (data.data == null) {
            Logger.error(LogType.IMPROPER_USE, "missing data");
            return;
        }
        if (data.origin == null) {
            Logger.error(LogType.IMPROPER_USE, "origin must not be null");
            return;
        }
        Set<MirrorConfig> mirrors = new HashSet<>();
        if (data.ipSrc == null || data.ipDst == null) {
            // run ether check
            checkHelper(mirrors, data.origin, f -> f.matchEthernet(data.macSrc, data.macDst));
        } else if (data.transportLayerProtocol == null) {
            // run ip check
            checkHelper(mirrors, data.origin, f -> f.matchIp(data.macSrc, data.macDst, data.ipSrc, data.ipDst));
        } else if (data.applicationLayerProtocol == null) {
            // run transport check
            checkHelper(mirrors, data.origin, f -> f.matchTransport(data.macSrc, data.macDst, data.ipSrc, data.ipDst, data.transportLayerProtocol, data.portSrc, data.portDst));
        } else {
            // run app check
            checkHelper(mirrors, data.origin, f -> f.matchApplication(data.macSrc, data.macDst, data.ipSrc, data.ipDst, data.transportLayerProtocol, data.portSrc, data.portDst, data.applicationLayerProtocol));
        }

        for (MirrorConfig c : mirrors) {
            var packets = formatPacket(data.ctx,
                c.mtu, data.origin,
                data.macSrc, data.macDst,
                data.ipSrc, data.ipDst,
                data.transportLayerProtocol,
                data.portSrc, data.portDst,
                data.applicationLayerProtocol,
                data.meta,
                data.flags, data.data);
            mirror.sendPacket(c.tap, packets);
        }
    }

    private static void checkHelper(Set<MirrorConfig> mirrors, String origin, Function<FilterConfig, Boolean> func) {
        for (FilterConfig f : mirror.filters) {
            if (f.originConfig.origin.equals(origin) && func.apply(f)) {
                mirrors.add(f.originConfig.mirror);
            }
        }
    }

    private static List<EthernetPacket> formatPacket(MirrorContext ctx,
                                                     int mtu,
                                                     String originType,
                                                     MacAddress macSrc, MacAddress macDst,
                                                     IP ipSrc, IP ipDst,
                                                     String transportLayerProtocol,
                                                     int portSrc, int portDst,
                                                     String applicationLayerProtocol,
                                                     String meta,
                                                     byte flags,
                                                     ByteArray fullData) {
        {
            // get default mac if not present
            if (macSrc == null) {
                macSrc = new MacAddress("00:00:00:00:00:00");
            }
            if (macDst == null) {
                macDst = new MacAddress("ff:ff:ff:ff:ff:ff");
            }
            // get default ip if not present
            byte[] b = Utils.allocateByteArrayInitZero(16);
            b[0] = (byte) 0xfd;
            if (ipSrc == null) {
                ipSrc = IP.fromIPv6(b);
            }
            if (ipDst == null) {
                ipDst = IP.fromIPv6(b);
            }
            // use ipv6 if it's provided as ipv4
            ipSrc = getIpv6FromIpv4(ipSrc);
            ipDst = getIpv6FromIpv4(ipDst);
            // set default transport layer protocol
            if (transportLayerProtocol == null) {
                transportLayerProtocol = "";
            }
            // set default application layer protocol;
            if (applicationLayerProtocol == null) {
                applicationLayerProtocol = "";
            }
        }
        // prepare port
        ByteArray srcPort = ByteArray.allocate(2).int16(0, portSrc);
        ByteArray dstPort = ByteArray.allocate(2).int16(0, portDst);
        // prepare payload
        List<ByteArray> dataList = new ArrayList<>();
        while (true) {
            if (fullData.length() <= mtu) {
                dataList.add(fullData);
                break;
            }
            dataList.add(fullData.sub(0, mtu));
            fullData = fullData.sub(mtu, fullData.length() - mtu);
        }
        // the result array
        List<EthernetPacket> retList = new ArrayList<>(dataList.size());

        for (ByteArray data : dataList) {
            final int dataOffset = 5; // 20 bytes
            byte dataOffsetByte = (byte) (dataOffset << 4);
            ByteArray tcpPacketBytes =
                srcPort.concat(dstPort) // src(2) dst(2)
                    .concat(ByteArray.allocate(16)
                            .int32(0, ctx.getSeq()) // seq(4)
                            // ack(4)
                            .set(8, dataOffsetByte).set(9, flags).int16(10, 0xffff) // dataOffset,flags,windowSize
                        // checksum and urg-ptr
                    )
                    .concat(data);

            PacketBytes tcpPacket = new PacketBytes();
            tcpPacket.setBytes(tcpPacketBytes);

            int ipPacketPayloadLenField = tcpPacketBytes.length();

            final byte DUMMY_ORIGIN_NEXT_HEADER = Consts.IPv6_NEXT_HEADER_HOP_BY_HOP_OPTIONS;
            final byte DUMMY_META_TCP_OPTION = Consts.IPv6_NEXT_HEADER_ROUTING;

            Ipv6Packet ip = new Ipv6Packet();
            ip.setVersion(6);
            ip.setTrafficClass(0);
            ip.setFlowLabel(0);
            ip.setNextHeader(DUMMY_ORIGIN_NEXT_HEADER);
            ip.setHopLimit(255);
            ip.setSrc(IP.fromIPv6(ipSrc.getAddress()));
            ip.setDst(IP.fromIPv6(ipDst.getAddress()));
            ip.setExtHeaders(new ArrayList<>());
            ip.setPacket(tcpPacket);

            // build ext headers
            {
                ByteArray opBytes = ByteArray.from((
                    "o=" + originType + ";l=" + transportLayerProtocol + ";" + "p=" + applicationLayerProtocol + ";").getBytes());

                ByteArray other = ByteArray.allocate(2).set(0, (byte) 254).set(1, (byte) opBytes.length())
                    .concat(opBytes);
                if ((2 + other.length()) % 8 != 0) {
                    int foo = (2 + other.length()) / 8;
                    int pad = (foo + 1) * 8 - (2 + other.length());
                    other = other.concat(ByteArray.allocate(pad));
                }

                Ipv6Packet.ExtHeader ext = new Ipv6Packet.ExtHeader();
                ext.setNextHeader(DUMMY_META_TCP_OPTION);
                ext.setHdrExtLen((other.length() - 6) / 8);
                ext.setOther(other);

                ipPacketPayloadLenField += ext.getRawPacket(0).length();
                ip.getExtHeaders().add(ext);
            }
            {
                ByteArray metaBytes = ByteArray.from(meta.getBytes());

                ByteArray other = ByteArray.allocate(2).set(0, (byte) 254) // routing type
                    .concat(metaBytes);
                if ((2 + other.length()) % 8 != 0) {
                    int foo = (2 + other.length()) / 8;
                    int pad = (foo + 1) * 8 - (2 + other.length());
                    other = other.concat(ByteArray.allocate(pad));
                }

                Ipv6Packet.ExtHeader ext = new Ipv6Packet.ExtHeader();
                ext.setNextHeader(Consts.IP_PROTOCOL_TCP);
                ext.setHdrExtLen((other.length() - 6) / 8);
                ext.setOther(other);

                ipPacketPayloadLenField += ext.getRawPacket(0).length();
                ip.getExtHeaders().add(ext);
            }
            // set length
            ip.setPayloadLength(ipPacketPayloadLenField);

            // calculate tcp checksum
            {
                ByteArray pseudoHeader = Utils.buildPseudoIPv6Header(ip, Consts.IP_PROTOCOL_TCP, tcpPacketBytes.length());
                var foo = pseudoHeader.concat(tcpPacketBytes);
                int cksm = Utils.calculateChecksum(foo, foo.length());
                tcpPacketBytes.int16(16, cksm);
            }

            EthernetPacket pkt = new EthernetPacket();
            pkt.setDst(macDst);
            pkt.setSrc(macSrc);
            pkt.setType(Consts.ETHER_TYPE_IPv6);
            pkt.setPacket(ip);

            retList.add(pkt);

            // increase the sequence
            ctx.incrSeq(data.length());
        }
        return retList;
    }

    private static IPv6 getIpv6FromIpv4(IP ipSrc) {
        if (ipSrc instanceof IPv4) {
            byte[] v4 = ipSrc.getAddress();
            byte[] v6 = Utils.allocateByteArrayInitZero(16);
            v6[10] = (byte) 0xff;
            v6[11] = (byte) 0xff;
            v6[12] = v4[0];
            v6[13] = v4[1];
            v6[14] = v4[2];
            v6[15] = v4[3];
            return IP.fromIPv6(v6);
        } else {
            return (IPv6) ipSrc;
        }
    }

    private void loadConfig() throws Exception {
        File f = new File(conf);
        if (!f.exists())
            throw new IOException(f.getAbsolutePath() + " does not exist");
        if (!f.isFile())
            throw new IOException(f.getAbsolutePath() + " is not a file");
        long ts = f.lastModified();
        if (mirror.lastTimestamp == ts) { // nothing changes, no need to load
            return;
        }
        FileInputStream fis = new FileInputStream(f);
        BufferedReader br = new BufferedReader(new InputStreamReader(fis));
        String s;
        StringBuilder sb = new StringBuilder();
        while ((s = br.readLine()) != null) {
            sb.append(s);
        }
        JSON.Instance<?> inst = JSON.parse(sb.toString());
        parseAndLoad(inst);
        mirror.lastTimestamp = ts;
        Logger.alert("mirror config reloaded");
    }

    private void loadConfigAndSetTimer() {
        int timeout;
        try {
            loadConfig();
            timeout = LOAD_CONFIG_SUCCESS_WAIT_INTERVAL;
        } catch (Exception e) {
            Logger.error(LogType.SYS_ERROR, "loading mirror config failed", e);
            timeout = LOAD_CONFIG_FAIL_WAIT_INTERVAL;
        }
        loop.delay(timeout, this::loadConfigAndSetTimer);
    }

    private void parseAndLoad(JSON.Instance<?> inst) throws Exception {
        String handling = "";
        boolean enabled;
        List<FilterConfig> filters;
        Set<String> enabledOrigins;
        List<MirrorConfig> mirrorConfigs;
        try {
            handling = "input";
            JSON.Object o = (JSON.Object) inst;

            handling = "enabled";
            enabled = o.getBool("enabled");

            handling = "mirrors";
            JSON.Array mirrors = o.getArray("mirrors");

            filters = new LinkedList<>();
            enabledOrigins = new HashSet<>();
            mirrorConfigs = new LinkedList<>();

            for (int i = 0; i < mirrors.length(); ++i) {
                MirrorConfig mirrorConfig = new MirrorConfig();
                handling = "mirrors[" + i + "]";
                parseAndLoadMirror(enabledOrigins, filters, mirrorConfig, (JSON.Object) mirrors.get(i));
                mirrorConfigs.add(mirrorConfig);
            }
        } catch (ClassCastException e) {
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                throw new ClassCastException("type error when handling " + handling + "." + e.getMessage());
            } else {
                throw new ClassCastException("type error when handling " + handling);
            }
        } catch (NoSuchElementException e) {
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                throw new NoSuchElementException("missing field when handling " + handling + "." + e.getMessage());
            } else {
                throw new NoSuchElementException("missing field when handling " + handling);
            }
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                throw new IllegalArgumentException("invalid value when handling " + handling + "." + e.getMessage());
            } else {
                throw new IllegalArgumentException("invalid value when handling " + handling);
            }
        }

        FDs fds = FDProvider.get().getProvided();
        if (!(fds instanceof FDsWithTap)) {
            throw new Exception("the provided fd impl does not support tap devices, use -Dvfd=posix or -Dvfd=windows instead");
        }
        FDsWithTap tapFDs = (FDsWithTap) fds;

        // check for tap devices
        List<MirrorConfig> toCreate = new LinkedList<>();
        List<TapDatagramFD> toDelete = new LinkedList<>();
        for (MirrorConfig m : mirrorConfigs) {
            boolean found = false;
            for (MirrorConfig thisM : this.mirrors) {
                if (thisM.tap.getTap().dev.equals(m.tapName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                toCreate.add(m);
            }
        }
        for (MirrorConfig thisM : this.mirrors) {
            boolean found = false;
            for (MirrorConfig m : mirrorConfigs) {
                if (thisM.tap.getTap().dev.equals(m.tapName)) {
                    found = true;
                    m.tap = thisM.tap;
                    break;
                }
            }
            if (!found) {
                toDelete.add(thisM.tap);
            }
        }

        // create
        for (MirrorConfig m : toCreate) {
            TapDatagramFD fd;
            try {
                fd = tapFDs.openTap(m.tapName);
            } catch (Throwable t) {
                Logger.error(LogType.SYS_ERROR, "cannot open tap device for mirror: " + m.tapName, t);
                destroyTaps(toCreate);
                throw new Exception("open tap device " + m.tapName + " failed");
            }
            m.tap = fd;
            if (!fd.getTap().dev.equals(m.tapName)) {
                Logger.error(LogType.IMPROPER_USE, "should not specify tap pattern when mirroring. please create one before using");
                destroyTaps(toCreate);
                throw new Exception("should not specify tap pattern when mirroring. please create one before using");
            }
            Logger.alert("open mirror tap device " + m.tapName);
        }

        boolean thisEnabledOld = this.enabled;
        this.enabled = false;
        this.mirrors = mirrorConfigs;
        this.enabledOrigins = enabledOrigins;
        this.filters = filters;
        this.enabled = enabled;

        // delete
        destroyTaps0(toDelete);

        if (thisEnabledOld && !this.enabled) {
            Logger.alert("disable mirror");
        } else if (!thisEnabledOld && this.enabled) {
            Logger.alert("enable mirror");
        }
    }

    private void destroyTaps(List<MirrorConfig> list) {
        List<TapDatagramFD> ls = list.stream().filter(m -> m.tap != null).map(m -> m.tap).collect(Collectors.toList());
        destroyTaps0(ls);
    }

    private void destroyTaps0(List<TapDatagramFD> list) {
        for (TapDatagramFD fd : list) {
            try {
                fd.close();
                Logger.warn(LogType.ALERT, "close mirror tap device " + fd.getTap().dev);
            } catch (IOException e) {
                Logger.shouldNotHappen("closing fd " + fd + "failed", e);
            }
        }
    }

    private void runSub(Consumer<String[]> r) {
        String[] handling = new String[]{""};
        try {
            r.accept(handling);
        } catch (ClassCastException e) {
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                throw new ClassCastException(handling[0] + "." + e.getMessage());
            } else {
                throw new ClassCastException(handling[0]);
            }
        } catch (NoSuchElementException e) {
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                throw new NoSuchElementException(handling[0] + "." + e.getMessage());
            } else {
                throw new NoSuchElementException(handling[0]);
            }
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                throw new IllegalArgumentException(handling[0] + "." + e.getMessage());
            } else {
                throw new IllegalArgumentException(handling[0]);
            }
        }
    }

    private void parseAndLoadMirror(Set<String> enabledOrigins, List<FilterConfig> filters, MirrorConfig mirrorConfig, JSON.Object mirror) {
        runSub(handling -> {
            handling[0] = "tap";
            mirrorConfig.tapName = mirror.getString("tap");

            handling[0] = "packetSize";
            mirrorConfig.mtu = mirror.getInt("mtu");
            if (mirrorConfig.mtu < 0)
                throw new IllegalArgumentException("mtu should > 0");
            if (mirrorConfig.mtu > 1500) {
                throw new IllegalArgumentException("mtu should < 1500");
            }

            handling[0] = "origins";
            JSON.Array origins = mirror.getArray("origins");
            for (int i = 0; i < origins.length(); ++i) {
                OriginConfig originConfig = new OriginConfig(mirrorConfig);
                handling[0] = "origins[" + i + "]";
                parseAndLoadOrigin(enabledOrigins, filters, originConfig, (JSON.Object) origins.get(i));
            }
        });
    }

    private void parseAndLoadOrigin(Set<String> enabledOrigins, List<FilterConfig> filterConfigs, OriginConfig originConfig, JSON.Object origin) {
        runSub(handling -> {
            handling[0] = "origin";
            originConfig.origin = origin.getString("origin");
            enabledOrigins.add(originConfig.origin);

            handling[0] = "filters";
            JSON.Array filters = origin.getArray("filters");
            for (int i = 0; i < filters.length(); ++i) {
                handling[0] = "filters[" + i + "]";
                JSON.Object filter = (JSON.Object) filters.get(i);

                FilterConfig filterConfig = new FilterConfig(originConfig);
                parseAndLoadFilter(filterConfig, filter);
                filterConfigs.add(filterConfig);
            }
        });
    }

    private void parseAndLoadFilter(FilterConfig filterConfig, JSON.Object filter) {
        runSub(handling -> {
            handling[0] = "mac";
            if (filter.containsKey("mac")) {
                String mac = filter.getString("mac");
                filterConfig.macX = new MacAddress(mac);

                handling[0] = "mac2";
                if (filter.containsKey("mac2")) {
                    mac = filter.getString("mac2");
                    filterConfig.macY = new MacAddress(mac);
                }
            }

            handling[0] = "network";
            if (filter.containsKey("network")) {
                String network = filter.getString("network");
                filterConfig.netX = new Network(network);

                handling[0] = "network2";
                if (filter.containsKey("network2")) {
                    network = filter.getString("network2");
                    filterConfig.netY = new Network(network);
                }
            }

            handling[0] = "transportLayerProtocol";
            if (filter.containsKey("transportLayerProtocol")) {
                filterConfig.transportLayerProtocol = filter.getString("transportLayerProtocol");
            }

            handling[0] = "port";
            if (filter.containsKey("port")) {
                JSON.Array arr = filter.getArray("port");
                int min = arr.getInt(0);
                int max = arr.getInt(1);
                if (min > max)
                    throw new IllegalArgumentException();
                filterConfig.portX = new int[]{min, max};

                handling[0] = "port2";
                if (filter.containsKey("port2")) {
                    arr = filter.getArray("port2");
                    min = arr.getInt(0);
                    max = arr.getInt(1);
                    if (min > max)
                        throw new IllegalArgumentException();
                    filterConfig.portY = new int[]{min, max};
                }
            }

            handling[0] = "applicationLayerProtocol";
            if (filter.containsKey("applicationLayerProtocol")) {
                filterConfig.applicationLayerProtocol = filter.getString("applicationLayerProtocol");
            }
        });
    }

    private void sendPacket(TapDatagramFD tap, AbstractEthernetPacket pkt) {
        sendPacket(tap, Collections.singletonList(pkt));
    }

    private final DirectByteBuffer writeBuffer = DirectMemoryUtils.allocateDirectBuffer(2048);

    private void sendPacket(TapDatagramFD tap, List<? extends AbstractEthernetPacket> packets) {
        for (AbstractEthernetPacket pkt : packets) {
            try {
                writeBuffer.limit(writeBuffer.capacity()).position(0);
                writeBuffer.put(pkt.getRawPacket(0).toJavaArray());
                writeBuffer.flip();
                tap.write(writeBuffer.realBuffer());
                // ignore write result
            } catch (Throwable t) {
                Logger.error(LogType.CONN_ERROR, "sending mirror packet to " + tap + " failed");
            }
        }
    }
}
