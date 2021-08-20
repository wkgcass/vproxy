package vproxyx.websocks.uot;

import vproxy.base.util.ByteArray;
import vproxy.base.util.Consts;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.vpacket.PacketBytes;
import vproxy.vpacket.TcpPacket;
import vproxy.vpacket.UdpPacket;
import vproxy.vswitch.PacketBuffer;
import vproxy.vswitch.PacketFilterHelper;
import vproxy.vswitch.plugin.FilterResult;
import vproxy.vswitch.plugin.PacketFilter;

import java.util.HashMap;
import java.util.Map;

public class UdpOverTcpPacketFilter implements PacketFilter {
    private static final byte TCP_OPTION_VPROXY_UOT = (byte) 132;

    private final boolean isClient;
    private final Map<LocalRemoteIPPort, UEntry> conntrack = new HashMap<>();

    public UdpOverTcpPacketFilter(boolean isClient) {
        this.isClient = isClient;
    }

    @Override
    public FilterResult handle(PacketFilterHelper helper, PacketBuffer pkb) {
        if (pkb.devout == null) {
            return handleInput(pkb);
        } else {
            return handleOutput(pkb);
        }
    }

    private FilterResult handleInput(PacketBuffer pkb) {
        if (pkb.tcpPkt == null) {
            assert Logger.lowLevelDebug("input pass: no tcpPkt");
            return FilterResult.PASS;
        }
        var pkt = pkb.tcpPkt;

        pkb.ensurePartialPacketParsed();

        TcpPacket.TcpOption uot = null;
        for (var opt : pkt.getOptions()) {
            if (opt.getKind() == TCP_OPTION_VPROXY_UOT) {
                uot = opt;
                break;
            }
        }
        if (uot == null) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "input drop: no uot: " + pkb.pkt.description());
            return FilterResult.DROP;
        }

        if ((pkb.flags & Consts.TCP_FLAGS_FIN) != 0 ||
            (pkb.flags & Consts.TCP_FLAGS_RST) != 0) {
            assert Logger.lowLevelDebug("received flags which will not handle, drop it: " + pkb.pkt.description());
            return FilterResult.DROP;
        }

        var key = new LocalRemoteIPPort(pkb.ipPkt.getDst(), pkb.ipPkt.getSrc(), pkt.getDstPort(), pkt.getSrcPort());
        var entry = conntrack.get(key);

        if (pkt.isSyn() && !pkt.isAck()) {
            // is trying to establish a connection
            if (entry == null) {
                entry = new UEntry(key, this::removeEntry);
                conntrack.put(key, entry);
                Logger.alert("received new connection: " + key);
            }
        } else {
            if (entry == null) {
                Logger.warn(LogType.ALERT, "received unrecorded packet, drop it: " + pkb.pkt.description());
                return FilterResult.DROP;
            }
        }

        entry.resetTimer();

        long pktSeq = pkt.getSeqNum();
        entry.ackId = pktSeq + pkt.getData().length();

        if (isClient) {
            if (pkt.isSyn()) {
                if (!pkt.isAck()) {
                    Logger.warn(LogType.ALERT, "client receives SYN packet, drop it: " + pkb.pkt.description());
                    return FilterResult.DROP;
                }
                // connection establishes
                if (entry.needToSendSyn) {
                    entry.needToSendSyn = false;
                    entry.seqId += 1;
                }
                entry.ackId += 1;
            }
        } else {
            if (pkt.isSyn()) {
                if (pkt.isAck()) {
                    Logger.warn(LogType.ALERT, "server receives SYN+ACK packet, drop it: " + pkb.pkt.description());
                    return FilterResult.DROP;
                }
            } else {
                // connection establishes
                if (entry.needToSendSyn) {
                    entry.needToSendSyn = false;
                    entry.seqId += 1;
                }
            }
        }

        ByteArray data;
        if (pkt.isSyn()) {
            data = null;
            for (var opt : pkt.getOptions()) {
                if (opt.getKind() != TCP_OPTION_VPROXY_UOT) {
                    continue;
                }
                if (data == null) {
                    data = opt.getData();
                } else {
                    data = data.concat(opt.getData());
                }
            }
            assert data != null;
        } else {
            data = pkt.getData();
        }
        if (data.length() == 0) {
            assert Logger.lowLevelDebug("input drop: ignoring empty packet");
            return FilterResult.DROP;
        }

        UdpPacket upkt = new UdpPacket();
        upkt.setSrcPort(pkt.getSrcPort());
        upkt.setDstPort(pkt.getDstPort());
        upkt.setData(new PacketBytes(data));

        pkb.ipPkt.setPacket(Consts.IP_PROTOCOL_UDP, upkt);
        pkb.tcpPkt = null;
        pkb.udpPkt = upkt;

        assert Logger.lowLevelDebug("input formatted packet: " + pkb);
        return FilterResult.PASS;
    }

    private FilterResult handleOutput(PacketBuffer pkb) {
        if (pkb.udpPkt == null) {
            assert Logger.lowLevelDebug("output pass: no udpPkt");
            return FilterResult.PASS;
        }
        var pkt = pkb.udpPkt;

        var key = new LocalRemoteIPPort(pkb.ipPkt.getSrc(), pkb.ipPkt.getDst(), pkt.getSrcPort(), pkt.getDstPort());
        var entry = conntrack.get(key);

        if (entry == null) {
            if (isClient) {
                entry = new UEntry(key, this::removeEntry);
                conntrack.put(key, entry);
                Logger.alert("creating new connection: " + key);
            } else {
                assert Logger.lowLevelDebug("output pass: server is sending unrecorded packet");
                return FilterResult.PASS;
            }
        }

        TcpPacket tpkt = new TcpPacket();
        tpkt.setSrcPort(pkt.getSrcPort());
        tpkt.setDstPort(pkt.getDstPort());
        tpkt.setSeqNum(entry.seqId);
        if (entry.needToSendSyn && entry.ackId != 0) {
            tpkt.setAckNum(entry.ackId + 1);
        } else {
            tpkt.setAckNum(entry.ackId);
        }
        tpkt.setWindow(8192);

        if (entry.needToSendSyn) {
            if (isClient) {
                tpkt.setFlags(Consts.TCP_FLAGS_SYN);
            } else {
                tpkt.setFlags(Consts.TCP_FLAGS_SYN | Consts.TCP_FLAGS_ACK);
            }
        } else {
            tpkt.setFlags(Consts.TCP_FLAGS_PSH | Consts.TCP_FLAGS_ACK);
        }

        ByteArray data = pkt.getData().getRawPacket();
        if (entry.needToSendSyn) {
            ByteArray bytes = data;
            while (true) {
                ByteArray arr;
                if (bytes.length() > 255) {
                    arr = bytes.sub(0, 255);
                    bytes = bytes.sub(255, bytes.length() - 255);
                } else {
                    arr = bytes;
                }
                TcpPacket.TcpOption opt = new TcpPacket.TcpOption();
                opt.setKind(TCP_OPTION_VPROXY_UOT);
                opt.setData(arr);
                tpkt.getOptions().add(opt);
                if (bytes == arr) {
                    break;
                }
            }
            tpkt.setData(ByteArray.allocate(0));
        } else {
            TcpPacket.TcpOption opt = new TcpPacket.TcpOption();
            opt.setKind(TCP_OPTION_VPROXY_UOT);
            opt.setData(ByteArray.allocate(0));
            tpkt.getOptions().add(opt);

            entry.seqId += data.length();
            tpkt.setData(data);
        }

        pkb.ipPkt.setPacket(Consts.IP_PROTOCOL_TCP, tpkt);
        pkb.tcpPkt = tpkt;
        pkb.udpPkt = null;

        assert Logger.lowLevelDebug("output formatted packet: " + pkb);
        return FilterResult.PASS;
    }

    private void removeEntry(UEntry entry) {
        var key = new LocalRemoteIPPort(entry.local.getAddress(), entry.remote.getAddress(), entry.local.getPort(), entry.remote.getPort());
        conntrack.remove(key);
    }
}
