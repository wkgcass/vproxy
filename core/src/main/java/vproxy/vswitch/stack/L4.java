package vproxy.vswitch.stack;

import vproxy.base.util.ByteArray;
import vproxy.base.util.Consts;
import vproxy.base.util.Logger;
import vproxy.vfd.IPPort;
import vproxy.vfd.IPv4;
import vproxy.vfd.IPv6;
import vproxy.vpacket.AbstractIpPacket;
import vproxy.vpacket.Ipv4Packet;
import vproxy.vpacket.Ipv6Packet;
import vproxy.vpacket.TcpPacket;
import vproxy.vpacket.conntrack.tcp.*;
import vproxy.vswitch.Table;

import java.util.Collections;
import java.util.List;

public class L4 {
    private final L3 L3;
    private final SwitchContext swCtx;

    public L4(SwitchContext swCtx, L3 l3) {
        this.swCtx = swCtx;
        L3 = l3;
    }

    public boolean input(InputPacketL4Context ctx) {
        assert Logger.lowLevelDebug("L4.input(" + ctx + ")");
        if (!wantToHandle(ctx)) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " L4 stack doesn't handle this packet");
            return false;
        }
        if (ctx.needTcpReset) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " reset the packet");
            sendRst(ctx);
            return true;
        }
        if (ctx.tcp != null) {
            handleTcp(ctx);
            return true;
        }
        // implement more L4 protocols in the future
        assert Logger.lowLevelDebug(ctx.handlingUUID + " this packet is not handled by L4");
        return true;
    }

    private boolean wantToHandle(InputPacketL4Context ctx) {
        assert Logger.lowLevelDebug("wantToHandle(" + ctx + ")");

        // implement more L4 protocols in the future
        boolean result = false;

        var ipPkt = ctx.inputIpPacket;
        if (ipPkt.getPacket() instanceof TcpPacket) {
            var tcpPkt = (TcpPacket) ipPkt.getPacket();
            IPPort src = new IPPort(ipPkt.getSrc(), tcpPkt.getSrcPort());
            IPPort dst = new IPPort(ipPkt.getDst(), tcpPkt.getDstPort());
            var tcpEntry = ctx.table.conntrack.lookup(src, dst);
            if (tcpEntry != null) {
                ctx.tcp = tcpEntry;
                result = true;
            } else if (tcpPkt.getFlags() == Consts.TCP_FLAGS_SYN) {
                // only consider the packets with only SYN on it
                var listenEntry = ctx.table.conntrack.lookupListen(dst);
                if (listenEntry != null) {
                    assert Logger.lowLevelDebug(ctx.handlingUUID + " got new connection");

                    // check backlog
                    if (listenEntry.synBacklog.size() >= ListenEntry.MAX_SYN_BACKLOG_SIZE) {
                        assert Logger.lowLevelDebug(ctx.handlingUUID + " syn-backlog is full");
                        // here we reset the connection instead of dropping it like linux
                        ctx.needTcpReset = true;
                    } else {
                        tcpEntry = ctx.table.conntrack.create(listenEntry, src, dst, tcpPkt.getSeqNum());
                        listenEntry.synBacklog.add(tcpEntry);
                        ctx.tcp = tcpEntry;
                    }
                    result = true;
                }
            } else {
                assert Logger.lowLevelDebug("need to reset");
                ctx.needTcpReset = true;
                result = true;
            }
        }

        assert Logger.lowLevelDebug("wantToHandle(" + ctx + ") = " + result);
        return result;
    }

    private void handleTcp(InputPacketL4Context ctx) {
        assert Logger.lowLevelDebug("handleTcp(" + ctx + ")");
        var tcp = ctx.tcp;
        switch (tcp.getState()) {
            case CLOSED:
                handleTcpClosed(ctx);
                break;
            case SYN_SENT:
                handleTcpSynSent(ctx);
                break;
            case SYN_RECEIVED:
                handleTcpSynReceived(ctx);
                break;
            case ESTABLISHED:
                handleTcpEstablished(ctx);
                break;
            case FIN_WAIT_1:
                handleTcpFinWait1(ctx);
                break;
            case FIN_WAIT_2:
                handleTcpFinWait2(ctx);
                break;
            case CLOSE_WAIT:
                handleTcpCloseWait(ctx);
                break;
            case CLOSING:
                handleTcpClosing(ctx);
                break;
            case LAST_ACK:
                handleLastAck(ctx);
                break;
            case TIME_WAIT:
                handleTimeWait(ctx);
                break;
            default:
                Logger.shouldNotHappen("should not reach here");
        }
    }

    private TcpPacket buildSynAck(InputPacketL4Context ctx) {
        TcpPacket respondTcp = TcpUtils.buildCommonTcpResponse(ctx.tcp);
        respondTcp.setFlags(Consts.TCP_FLAGS_SYN | Consts.TCP_FLAGS_ACK);
        respondTcp.setWindow(65535);
        {
            var optMss = new TcpPacket.TcpOption();
            optMss.setKind(Consts.TCP_OPTION_MSS);
            optMss.setData(ByteArray.allocate(2).int16(0, TcpEntry.RCV_MSS));
            respondTcp.getOptions().add(optMss);
        }
        {
            int scale = ctx.tcp.receivingQueue.getWindowScale();
            int cnt = 0;
            while (scale != 1) {
                scale /= 2;
                cnt += 1;
            }
            if (cnt != 0) {
                var optWindowScale = new TcpPacket.TcpOption();
                optWindowScale.setKind(Consts.TCP_OPTION_WINDOW_SCALE);
                optWindowScale.setData(ByteArray.allocate(1).set(0, (byte) cnt));
                respondTcp.getOptions().add(optWindowScale);
            }
        }
        return respondTcp;
    }

    private void sendRst(InputPacketL4Context ctx) {
        assert Logger.lowLevelDebug("sendRst(" + ctx + ")");

        // maybe there's no tcp entry when sending rst
        // so we can only use the fields in the received packet

        var inputTcpPkt = (TcpPacket) ctx.inputIpPacket.getPacket();

        TcpPacket respondTcp = new TcpPacket();
        respondTcp.setSrcPort(inputTcpPkt.getDstPort());
        respondTcp.setDstPort(inputTcpPkt.getSrcPort());
        respondTcp.setSeqNum(inputTcpPkt.getAckNum());
        respondTcp.setAckNum(inputTcpPkt.getSeqNum());
        respondTcp.setFlags(Consts.TCP_FLAGS_RST);
        respondTcp.setWindow(0);

        AbstractIpPacket ipPkt;
        if (ctx.inputIpPacket.getSrc() instanceof IPv4) {
            var ipv4 = new Ipv4Packet();
            ipv4.setSrc((IPv4) ctx.inputIpPacket.getDst());
            ipv4.setDst((IPv4) ctx.inputIpPacket.getSrc());
            var tcpBytes = respondTcp.buildIPv4TcpPacket(ipv4);

            ipv4.setVersion(4);
            ipv4.setIhl(5);
            ipv4.setTotalLength(20 + tcpBytes.length());
            ipv4.setTtl(64);
            ipv4.setProtocol(Consts.IP_PROTOCOL_TCP);
            ipv4.setOptions(ByteArray.allocate(0));

            ipv4.setPacket(respondTcp);
            ipPkt = ipv4;
        } else {
            var ipv6 = new Ipv6Packet();
            ipv6.setSrc((IPv6) ctx.inputIpPacket.getDst());
            ipv6.setDst((IPv6) ctx.inputIpPacket.getSrc());
            var tcpBytes = respondTcp.buildIPv6TcpPacket(ipv6);

            ipv6.setVersion(6);
            ipv6.setNextHeader(Consts.IP_PROTOCOL_TCP);
            ipv6.setPayloadLength(tcpBytes.length());
            ipv6.setHopLimit(64);
            ipv6.setExtHeaders(Collections.emptyList());

            ipv6.setPacket(respondTcp);
            ipPkt = ipv6;
        }

        L3.output(new OutputPacketL3Context(ctx.handlingUUID, ctx.table, ipPkt));
    }

    private void handleTcpClosed(InputPacketL4Context ctx) {
        assert Logger.lowLevelDebug(ctx.handlingUUID + " handleTcpClosed");

        var tcpPkt = (TcpPacket) ctx.inputIpPacket.getPacket();
        // only handle syn
        if (tcpPkt.getFlags() != Consts.TCP_FLAGS_SYN) {
            assert Logger.lowLevelDebug("not SYN packet");
            return;
        }
        ctx.tcp.setState(TcpState.SYN_RECEIVED);
        // get tcp options from the syn
        int mss = TcpEntry.SND_DEFAULT_MSS;
        int windowScale = 1;
        for (var opt : tcpPkt.getOptions()) {
            switch (opt.getKind()) {
                case Consts.TCP_OPTION_MSS:
                    mss = opt.getData().uint16(0);
                    break;
                case Consts.TCP_OPTION_WINDOW_SCALE:
                    int s = opt.getData().uint8(0);
                    windowScale = 1 << s;
                    break;
            }
        }
        ctx.tcp.sendingQueue.init(tcpPkt.getWindow(), mss, windowScale);

        // SYN-ACK
        TcpPacket respondTcp = buildSynAck(ctx);
        AbstractIpPacket respondIp = TcpUtils.buildIpResponse(ctx.tcp, respondTcp);

        ctx.tcp.sendingQueue.incAllSeq();

        L3.output(new OutputPacketL3Context(ctx.handlingUUID, ctx.table, respondIp));
    }

    private void handleTcpSynSent(@SuppressWarnings("unused") InputPacketL4Context ctx) {
        Logger.shouldNotHappen("unsupported yet: syn-sent state");
    }

    private void handleTcpSynReceived(InputPacketL4Context ctx) {
        assert Logger.lowLevelDebug(ctx.handlingUUID + " handleTcpSynReceived");
        // first check whether the packet has ack, and if so, check the ack number
        var tcpPkt = (TcpPacket) ctx.inputIpPacket.getPacket();
        if (tcpPkt.isSyn()) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " probably a syn retransmission");
            if (tcpPkt.getSeqNum() == ctx.tcp.receivingQueue.getAckedSeq() - 1) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " seq matches");
                ctx.tcp.sendingQueue.decAllSeq();
                TcpPacket respondTcp = buildSynAck(ctx);
                AbstractIpPacket respondIp = TcpUtils.buildIpResponse(ctx.tcp, respondTcp);
                ctx.tcp.sendingQueue.incAllSeq();
                L3.output(new OutputPacketL3Context(ctx.handlingUUID, ctx.table, respondIp));
                return;
            }
        }
        if (!tcpPkt.isAck()) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " no ack flag set");
            return;
        }
        if (tcpPkt.getAckNum() != ctx.tcp.sendingQueue.getAckSeq()) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " wrong ack number");
            return;
        }
        connectionEstablishes(ctx);

        // then run the same handling as established
        handleTcpEstablished(ctx);
    }

    private void connectionEstablishes(InputPacketL4Context ctx) {
        assert Logger.lowLevelDebug(ctx.handlingUUID + " connectionEstablishes");
        ctx.tcp.setState(TcpState.ESTABLISHED);
        // alert that this connection can be retrieved
        var parent = ctx.tcp.getParent();
        parent.synBacklog.remove(ctx.tcp);
        parent.backlog.add(ctx.tcp);
        parent.listenHandler.readable(parent);
    }

    private boolean handleTcpGeneralReturnFalse(InputPacketL4Context ctx) {
        assert Logger.lowLevelDebug(ctx.handlingUUID + " handleTcpGeneral");

        var tcpPkt = (TcpPacket) ctx.inputIpPacket.getPacket();

        // check whether seq matches
        var seq = tcpPkt.getSeqNum();
        var expect = ctx.tcp.receivingQueue.getExpectingSeq();
        var acked = ctx.tcp.receivingQueue.getAckedSeq();
        if (tcpPkt.isFin()) {
            if (seq != acked) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " data not fully consumed yet but received FIN");
                return true;
            }
        } else if (seq != expect) {
            if (!tcpPkt.isPsh() || seq > expect) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " invalid sequence number");
                return true;
            }
        }

        if (tcpPkt.isAck()) {
            long ack = tcpPkt.getAckNum();
            int window = tcpPkt.getWindow();
            ctx.tcp.sendingQueue.ack(ack, window);
            // then check whether there's data to send
            // because the window may forbid it from sending
            // ack resets the window so it might get chance to send data
            if (ctx.tcp.retransmissionTimer == null) {
                tcpStartRetransmission(ctx.handlingUUID, ctx.table, ctx.tcp);
            }
        }
        return false;
    }

    private void handleTcpEstablished(InputPacketL4Context ctx) {
        assert Logger.lowLevelDebug(ctx.handlingUUID + " handleTcpEstablished");
        if (handleTcpGeneralReturnFalse(ctx)) {
            return;
        }
        var tcpPkt = (TcpPacket) ctx.inputIpPacket.getPacket();
        if (tcpPkt.isPsh()) {
            long seq = tcpPkt.getSeqNum();
            ByteArray data = tcpPkt.getData();
            ctx.tcp.receivingQueue.store(new Segment(seq, data));
        }
        if (tcpPkt.isFin()) {
            ctx.tcp.setState(TcpState.CLOSE_WAIT);
            ctx.tcp.receivingQueue.incExpectingSeq();
            tcpAck(ctx.handlingUUID, ctx.table, ctx.tcp);
        }
    }

    private void handleTcpFinWait1(InputPacketL4Context ctx) {
        assert Logger.lowLevelDebug(ctx.handlingUUID + " handleTcpFinWait1");
        if (handleTcpGeneralReturnFalse(ctx)) {
            return;
        }
        var tcpPkt = (TcpPacket) ctx.inputIpPacket.getPacket();
        if (tcpPkt.isFin()) {
            if (ctx.tcp.sendingQueue.ackOfFinReceived()) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " transform to CLOSING");
                ctx.tcp.setState(TcpState.CLOSING);
                sendRst(ctx);
            } else {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " received FIN but the previous sent FIN not acked");
            }
        } else {
            if (ctx.tcp.sendingQueue.ackOfFinReceived()) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " the sent FIN is acked, transform to FIN_WAIT_2");
                ctx.tcp.setState(TcpState.FIN_WAIT_2);
            }
        }
    }

    private void handleTcpFinWait2(InputPacketL4Context ctx) {
        assert Logger.lowLevelDebug(ctx.handlingUUID + " handleTcpFinWait2");
        if (handleTcpGeneralReturnFalse(ctx)) {
            return;
        }
        var tcpPkt = (TcpPacket) ctx.inputIpPacket.getPacket();
        if (tcpPkt.isFin()) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " transform to CLOSING");
            ctx.tcp.setState(TcpState.CLOSING);
            sendRst(ctx);
        }
    }

    private void handleTcpCloseWait(InputPacketL4Context ctx) {
        assert Logger.lowLevelDebug(ctx.handlingUUID + " handleTcpCloseWait");
        if (handleTcpGeneralReturnFalse(ctx)) {
            return;
        }
        var tcpPkt = (TcpPacket) ctx.inputIpPacket.getPacket();
        if (tcpPkt.isFin()) {
            assert Logger.lowLevelDebug("received FIN again, maybe it's retransmission");
            if (tcpPkt.getSeqNum() == ctx.tcp.receivingQueue.getExpectingSeq() - 1) {
                tcpAck(ctx.handlingUUID, ctx.table, ctx.tcp);
            }
        }
    }

    private void handleTcpClosing(InputPacketL4Context ctx) {
        assert Logger.lowLevelDebug(ctx.handlingUUID + " handleTcpClosing");
        if (handleTcpGeneralReturnFalse(ctx)) {
            return;
        }
        assert Logger.lowLevelDebug(ctx.handlingUUID + " drop any packet when it's in CLOSING state");
    }

    private void handleLastAck(@SuppressWarnings("unused") InputPacketL4Context ctx) {
        Logger.shouldNotHappen("unsupported yet: last-ack state");
    }

    private void handleTimeWait(@SuppressWarnings("unused") InputPacketL4Context ctx) {
        Logger.shouldNotHappen("unsupported yet: time-wait state");
    }

    public void output(OutputPacketL3Context ctx) {
        assert Logger.lowLevelDebug("L4.output(" + ctx + ")");
        L3.output(ctx);
    }

    public void tcpAck(String handlingUUID, Table table, TcpEntry tcp) {
        assert Logger.lowLevelDebug("tcpAck(" + handlingUUID + ", " + table + ", " + tcp + ")");

        if (tcp.receivingQueue.getWindow() == 0) {
            assert Logger.lowLevelDebug(handlingUUID + " no window, very bad, need to ack immediately");
            if (tcp.delayedAckTimer != null) {
                assert Logger.lowLevelDebug(handlingUUID + " cancel the timer");
                tcp.delayedAckTimer.cancel();
                tcp.delayedAckTimer = null;
            }
            sendAck(handlingUUID, table, tcp);
            return;
        }
        if (tcp.delayedAckTimer != null) {
            assert Logger.lowLevelDebug(handlingUUID + " delayed ack already scheduled");
            return;
        }
        tcp.delayedAckTimer = swCtx.getSelectorEventLoop().delay(TcpEntry.DELAYED_ACK_TIMEOUT, () -> sendAck(handlingUUID, table, tcp));
    }

    private void sendAck(String handlingUUID, Table table, TcpEntry tcp) {
        assert Logger.lowLevelDebug("sendAck(" + handlingUUID + ", " + table + ", " + tcp + ")");

        if (tcp.delayedAckTimer != null) {
            tcp.delayedAckTimer.cancel();
            tcp.delayedAckTimer = null;
        }

        TcpPacket respondTcp = TcpUtils.buildAckResponse(tcp);
        AbstractIpPacket respondIp = TcpUtils.buildIpResponse(tcp, respondTcp);
        L3.output(new OutputPacketL3Context(handlingUUID, table, respondIp));
    }

    public void tcpStartRetransmission(String handlingUUID, Table table, TcpEntry tcp) {
        assert Logger.lowLevelDebug("tcpStartRetransmission(" + handlingUUID + "," + table + "," + tcp + ")");
        transmitTcp(handlingUUID, table, tcp, 0, 0);
    }

    private void transmitTcp(String handlingUUID, Table table, TcpEntry tcp, long lastBeginSeq, int retransmissionCount) {
        assert Logger.lowLevelDebug("transmitTcp(" + handlingUUID + "," + table + "," + tcp + ")");

        if (tcp.retransmissionTimer != null) { // reset timer
            tcp.retransmissionTimer.cancel();
            tcp.retransmissionTimer = null;
        }

        // check whether need to reset the connection because of too many retransmits
        if (tcp.requireClosing() && retransmissionCount > TcpEntry.MAX_RETRANSMISSION_AFTER_CLOSING) {
            assert Logger.lowLevelDebug(handlingUUID + " conn " + tcp + " is closed due to too many retransmission after closing");
            resetTcpConnection(handlingUUID, table, tcp);
            return;
        }

        List<Segment> segments = tcp.sendingQueue.fetch();
        if (segments.isEmpty()) { // no data to send, check FIN
            if (tcp.sendingQueue.needToSendFin()) {
                assert Logger.lowLevelDebug(handlingUUID + " need to send FIN");
                // fall through
            } else {
                // nothing to send
                assert Logger.lowLevelDebug(handlingUUID + " no need to retransmit after " + retransmissionCount + " time(s)");
                if (tcp.retransmissionTimer != null) {
                    tcp.retransmissionTimer.cancel();
                    tcp.retransmissionTimer = null;
                }
                afterTransmission(handlingUUID, table, tcp);
                return;
            }
        }
        long currentBeginSeq = segments.isEmpty() ? tcp.sendingQueue.getFetchSeq() + 1 : segments.get(0).seqBeginInclusive;
        if (currentBeginSeq != lastBeginSeq) {
            assert Logger.lowLevelDebug(handlingUUID + " the sequence increased, it's not retransmitting after " + retransmissionCount + " time(s)");
            retransmissionCount = 0;
        }

        // initiate timer
        int delay = TcpEntry.RTO_MIN << retransmissionCount;
        if (delay <= 0 || delay > TcpEntry.RTO_MAX) { // overflow or exceeds maximum
            delay = TcpEntry.RTO_MAX;
        }
        assert Logger.lowLevelDebug(handlingUUID + " will delay " + delay + " ms then retransmit");
        final int finalRetransmissionCount = retransmissionCount;
        tcp.retransmissionTimer = swCtx.getSelectorEventLoop().delay(delay, () ->
            transmitTcp(handlingUUID, table, tcp, currentBeginSeq, finalRetransmissionCount + 1)
        );

        if (segments.isEmpty()) {
            assert tcp.sendingQueue.needToSendFin();
            sendTcpFin(handlingUUID, table, tcp);
        } else {
            for (var s : segments) {
                sendTcpPsh(handlingUUID, table, tcp, s);
            }
        }
    }

    private void afterTransmission(String handlingUUID, Table table, TcpEntry tcp) {
        assert Logger.lowLevelDebug("afterTransmission(" + handlingUUID + "," + table + "," + tcp + "," + ")");

        if (tcp.requireClosing()) {
            assert Logger.lowLevelDebug(handlingUUID + " need to be closed");
            resetTcpConnection(handlingUUID, table, tcp);
        }
    }

    public void resetTcpConnection(String handlingUUID, Table table, TcpEntry tcp) {
        assert Logger.lowLevelDebug("sendTcpRst(" + handlingUUID + "," + table + "," + tcp + "," + ")");

        output(new OutputPacketL3Context(handlingUUID, table,
            TcpUtils.buildIpResponse(tcp, TcpUtils.buildRstResponse(tcp))));
        tcp.setState(TcpState.CLOSED);
        table.conntrack.remove(tcp.source, tcp.destination);
    }

    private void sendTcpPsh(String handlingUUID, Table table, TcpEntry tcp, Segment s) {
        assert Logger.lowLevelDebug("sendTcpPsh(" + handlingUUID + "," + table + "," + tcp + "," + s + ")");

        TcpPacket tcpPkt = TcpUtils.buildCommonTcpResponse(tcp);
        tcpPkt.setSeqNum(s.seqBeginInclusive);
        tcpPkt.setFlags(Consts.TCP_FLAGS_PSH | Consts.TCP_FLAGS_ACK);
        tcpPkt.setData(s.data);
        AbstractIpPacket ipPkt = TcpUtils.buildIpResponse(tcp, tcpPkt);

        L3.output(new OutputPacketL3Context(handlingUUID, table, ipPkt));
    }

    private void sendTcpFin(String handlingUUID, Table table, TcpEntry tcp) {
        assert Logger.lowLevelDebug("sendTcpFin(" + handlingUUID + "," + table + "," + tcp + ")");

        TcpPacket tcpPkt = TcpUtils.buildCommonTcpResponse(tcp);
        tcpPkt.setSeqNum(tcp.sendingQueue.getFetchSeq());
        tcpPkt.setFlags(Consts.TCP_FLAGS_FIN | Consts.TCP_FLAGS_ACK);
        AbstractIpPacket ipPkt = TcpUtils.buildIpResponse(tcp, tcpPkt);

        L3.output(new OutputPacketL3Context(handlingUUID, table, ipPkt));
    }
}
