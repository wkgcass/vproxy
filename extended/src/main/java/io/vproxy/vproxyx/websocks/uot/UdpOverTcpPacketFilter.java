package io.vproxy.vproxyx.websocks.uot;

import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.*;
import io.vproxy.vpacket.conntrack.tcp.TcpEntry;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.PacketFilterHelper;
import io.vproxy.vswitch.plugin.FilterResult;
import io.vproxy.vswitch.plugin.PacketFilter;
import io.vproxy.vswitch.stack.conntrack.EnhancedUDPEntry;

public class UdpOverTcpPacketFilter implements PacketFilter {
    private static final byte TCP_OPTION_VPROXY_UOT = (byte) 132;

    private final boolean isClient;

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
        assert Logger.lowLevelDebug("uot input got packet: " + pkb);
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

        var remote = new IPPort(pkb.ipPkt.getSrc(), pkt.getSrcPort());
        var local = new IPPort(pkb.ipPkt.getDst(), pkt.getDstPort());
        var udpEntry = pkb.network.conntrack.lookupUdp(remote, local);

        if (udpEntry != null) {
            assert Logger.lowLevelDebug("use fastpath");
            pkb.fastpath = true;
            pkb.udp = udpEntry;
        } else {
            assert Logger.lowLevelDebug("use normal handle");
        }

        var entry = (udpEntry != null) ? (UEntry) udpEntry.userData : null;

        if (pkt.isSyn() && !pkt.isAck()) {
            // is trying to establish a connection
            if (entry == null) {
                entry = new UEntry();
                pkb.fastpathUserData = entry;
                Logger.alert("received new connection: " + remote + "/" + local);
            }
        } else {
            if (entry == null) {
                Logger.warn(LogType.ALERT, "received unrecorded packet, drop it: " + pkb.pkt.description());
                return FilterResult.DROP;
            }
        }

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
            if (pkt.isSyn() && !pkt.isAck()) { // need to respond syn-ack
                return respondEmptySynAck(pkb, entry);
            }
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

    private FilterResult respondEmptySynAck(PacketBuffer pkb, UEntry entry) {
        assert Logger.lowLevelDebug("respondEmptySynAck");
        // need to record udp
        var src = new IPPort(pkb.ipPkt.getSrc(), pkb.tcpPkt.getSrcPort());
        var dst = new IPPort(pkb.ipPkt.getDst(), pkb.tcpPkt.getDstPort());
        var listenEntry = pkb.network.conntrack.lookupUdpListen(dst);
        if (listenEntry == null) {
            assert Logger.lowLevelDebug("not listening " + dst + ", will not respond");
            return FilterResult.DROP;
        }
        var udpEntry = pkb.network.conntrack.recordUdp(src, dst, () ->
            new EnhancedUDPEntry(listenEntry, src, dst, pkb.network, SelectorEventLoop.current()));
        udpEntry.userData = entry;

        var tpkt = buildTcpPacket(entry, pkb.tcpPkt.getDstPort(), pkb.tcpPkt.getSrcPort(), ByteArray.allocate(0));
        var ipPkt = pkb.ipPkt;
        ipPkt.swapSrcDst();
        ipPkt.setPacket(Consts.IP_PROTOCOL_TCP, tpkt);
        ipPkt.setHopLimit(64);

        pkb.replacePacket(ipPkt);
        pkb.fastpath = true;
        pkb.udp = udpEntry;
        pkb.fastpathUserData = null;

        return FilterResult.L3_TX;
    }

    private FilterResult handleOutput(PacketBuffer pkb) {
        if (pkb.udpPkt == null) {
            assert Logger.lowLevelDebug("output pass: no udpPkt");
            return FilterResult.PASS;
        }
        assert Logger.lowLevelDebug("uot output got packet: " + pkb);
        var pkt = pkb.udpPkt;

        var remote = new IPPort(pkb.ipPkt.getDst(), pkt.getDstPort());
        var local = new IPPort(pkb.ipPkt.getSrc(), pkt.getSrcPort());
        var udpEntry = pkb.network.conntrack.lookupUdp(remote, local);

        if (udpEntry == null) {
            assert Logger.lowLevelDebug("output pass: unrecorded packet");
            return FilterResult.PASS;
        }

        var entry = (UEntry) udpEntry.userData;
        if (entry == null) {
            if (isClient) {
                entry = new UEntry();
                udpEntry.userData = entry;
                Logger.alert("creating new connection: " + remote + "/" + local);
            } else {
                assert Logger.lowLevelDebug("output pass: server is sending unrecorded packet");
                return FilterResult.PASS;
            }
        }

        TcpPacket tpkt = buildTcpPacket(
            entry,
            pkt.getSrcPort(), pkt.getDstPort(),
            pkt.getData().getRawPacket(AbstractPacket.FLAG_CHECKSUM_UNNECESSARY));

        pkb.ipPkt.setPacket(Consts.IP_PROTOCOL_TCP, tpkt);
        pkb.tcpPkt = tpkt;
        pkb.udpPkt = null;

        assert Logger.lowLevelDebug("output formatted packet: " + pkb);
        return FilterResult.PASS;
    }

    private TcpPacket buildTcpPacket(UEntry entry, int srcPort, int dstPort, ByteArray data) {
        TcpPacket tpkt = new TcpPacket();
        tpkt.setSrcPort(srcPort);
        tpkt.setDstPort(dstPort);
        tpkt.setSeqNum(entry.seqId);
        if (entry.needToSendSyn && entry.ackId != 0) {
            tpkt.setAckNum(entry.ackId + 1);
        } else {
            tpkt.setAckNum(entry.ackId);
        }
        tpkt.setWindow(65535);

        if (entry.needToSendSyn) {
            if (isClient) {
                tpkt.setFlags(Consts.TCP_FLAGS_SYN);
            } else {
                tpkt.setFlags(Consts.TCP_FLAGS_SYN | Consts.TCP_FLAGS_ACK);
            }
        } else {
            tpkt.setFlags(Consts.TCP_FLAGS_PSH | Consts.TCP_FLAGS_ACK);
        }

        if (entry.needToSendSyn) {
            // need to set mss for syn packets
            var optMss = new TcpPacket.TcpOption(tpkt);
            optMss.setKind(Consts.TCP_OPTION_MSS);
            optMss.setData(ByteArray.allocate(2).int16(0, TcpEntry.RCV_MSS));
            tpkt.getOptions().add(optMss);
            // need to set window scale for syn packets
            var optWindowScale = new TcpPacket.TcpOption(tpkt);
            optWindowScale.setKind(Consts.TCP_OPTION_WINDOW_SCALE);
            optWindowScale.setData(ByteArray.allocate(1).set(0, (byte) 16));
            tpkt.getOptions().add(optWindowScale);

            /* tcp packet max is 60
             * tcp options max is 60-20
             * we need to add mss option, which takes 4 bytes
             * we need to add window scale option, which takes 3 bytes
             * tcp option has 2 bytes for metadata
             * so max data length is 60-20-4-3-2=31 */
            if (data.length() > 31) {
                assert Logger.lowLevelDebug("handshake packets should not exceed 31 bytes, but got " + data.length() + ", will build an empty opt instead");
                data = ByteArray.allocate(0);
            }
            TcpPacket.TcpOption opt = new TcpPacket.TcpOption(tpkt);
            opt.setKind(TCP_OPTION_VPROXY_UOT);
            opt.setData(data);
            tpkt.getOptions().add(opt);

            tpkt.setData(ByteArray.allocate(0));
        } else {
            TcpPacket.TcpOption opt = new TcpPacket.TcpOption(tpkt);
            opt.setKind(TCP_OPTION_VPROXY_UOT);
            opt.setData(ByteArray.allocate(0));
            tpkt.getOptions().add(opt);

            entry.seqId += data.length();
            tpkt.setData(data);
        }
        return tpkt;
    }
}
