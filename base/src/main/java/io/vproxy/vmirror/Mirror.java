package io.vproxy.vmirror;

import io.vproxy.base.util.*;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.IPv6;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vpacket.*;
import vjson.CharStream;
import vjson.JSON;
import vjson.parser.ParserOptions;
import vjson.parser.ParserUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class Mirror {
    private static final Mirror mirror = new Mirror();

    private volatile boolean enabled = false;

    private List<MirrorConfig> mirrors = Collections.emptyList();
    private Set<String> enabledOrigins = Collections.emptySet();
    private List<FilterConfig> filters = Collections.emptyList();

    private Mirror() {
    }

    public static void destroy() {
        mirror.enabled = false;
        destroyMirrors();
        mirror.filters = Collections.emptyList();
    }

    private static void destroyMirrors() {
        for (var m : mirror.mirrors) {
            m.destroy();
        }
        mirror.mirrors = Collections.emptyList();
    }

    public static boolean isEnabled(String origin) {
        if (!mirror.enabled) {
            return false;
        }
        return mirror.enabledOrigins.contains(origin);
    }

    public static void switchPacket(EthernetPacket packet) {
        if (!mirror.enabled) return;

        Set<MirrorConfig> mirrors = new HashSet<>();
        if (packet.getPacket() instanceof AbstractIpPacket ipPkt) {
            checkHelper(mirrors, "switch", f -> f.matchIp(packet.getSrc(), packet.getDst(), ipPkt.getSrc(), ipPkt.getDst()));
        } else {
            checkHelper(mirrors, "switch", f -> f.matchEthernet(packet.getSrc(), packet.getDst()));
        }

        for (MirrorConfig c : mirrors) {
            mirror.sendPacket(c, packet);
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
                data.origin,
                data.macSrc, data.macDst,
                data.ipSrc, data.ipDst,
                data.transportLayerProtocol,
                data.portSrc, data.portDst,
                data.applicationLayerProtocol,
                data.meta,
                data.flags, data.data);
            mirror.sendPacket(c, packets);
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
        dataList.add(fullData);
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

    public static boolean loadConfig(String content) {
        try {
            JSON.Instance<?> inst = ParserUtils.buildFrom(CharStream.from(content), ParserOptions.allFeatures());
            mirror.parseAndLoad(inst);
            Logger.alert("mirror config reloaded");
            return true;
        } catch (Exception e) {
            Logger.error(LogType.SYS_ERROR, "loading mirror config failed", e);
            return false;
        }
    }

    @SuppressWarnings("RedundantThrows")
    private void parseAndLoad(JSON.Instance<?> inst) throws Exception {
        String handling = "";
        List<FilterConfig> filters;
        Set<String> enabledOrigins;
        List<MirrorConfig> mirrorConfigs;
        try {
            handling = "input";
            JSON.Object o = (JSON.Object) inst;

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

        boolean thisEnabledOld = this.enabled;
        this.enabled = false;
        destroyMirrors();
        this.mirrors = mirrorConfigs;
        this.enabledOrigins = enabledOrigins;
        this.filters = filters;
        this.enabled = true;

        if (thisEnabledOld && !this.enabled) {
            Logger.alert("disable mirror");
        } else if (!thisEnabledOld && this.enabled) {
            Logger.alert("enable mirror");
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
            handling[0] = "output";
            mirrorConfig.outputFilePath = Utils.filename(mirror.getString("output"));
            try {
                if (new File(mirrorConfig.outputFilePath).exists()) {
                    mirrorConfig.output = new FileOutputStream(mirrorConfig.outputFilePath, true);
                } else {
                    mirrorConfig.output = new FileOutputStream(mirrorConfig.outputFilePath);
                    mirrorConfig.output.write(new PcapGlobalHeader().build().toJavaArray());
                }
            } catch (FileNotFoundException e) {
                throw new IllegalArgumentException("file not found: " + mirrorConfig.outputFilePath);
            } catch (IOException e) {
                throw new IllegalArgumentException("writing pcap global header failed: " + mirrorConfig.outputFilePath);
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
                filterConfig.netX = Network.from(network);

                handling[0] = "network2";
                if (filter.containsKey("network2")) {
                    network = filter.getString("network2");
                    filterConfig.netY = Network.from(network);
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

    private void sendPacket(MirrorConfig config, EthernetPacket pkt) {
        sendPacket(config, Collections.singletonList(pkt));
    }

    private void sendPacket(MirrorConfig config, List<? extends EthernetPacket> packets) {
        for (var pkt : packets) {
            var pcap = new PcapPacket(pkt);
            try {
                config.output.write(pcap.build().toJavaArray());
            } catch (Throwable t) {
                Logger.error(LogType.CONN_ERROR, "sending mirror packet to " + config.outputFilePath + " failed");
            }
        }
    }
}
