package io.vproxy.vswitch.node;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vfd.IP;
import io.vproxy.vpacket.AbstractIpPacket;
import io.vproxy.vpacket.TcpPacket;
import io.vproxy.vpacket.conntrack.tcp.*;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.SwitchDelegate;
import io.vproxy.vswitch.VirtualNetwork;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.vproxy.base.util.Logger.tcpStackDebugOn;

@SuppressWarnings("ConstantConditions")
public class TcpStack extends Node {
    private final SwitchDelegate sw;
    private final NodeEgress tcpReset = new NodeEgress("tcp-reset");
    private final NodeEgress l4output = new NodeEgress("l4-output");

    private final Map<IP, PeerCwndHistory> peerCwndMap = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<IP, PeerCwndHistory> eldest) {
            return size() > 1024;
        }
    };

    public TcpStack(SwitchDelegate sw) {
        super("tcp-stack");
        this.sw = sw;
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("tcp-stack", "tcp-reset", "tcp-reset", DEFAULT_EDGE_DISTANCE);
        builder.addEdge("tcp-stack", "l4-output", "l4-output", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(tcpReset);
        fillEdges(l4output);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        var tcp = pkb.tcp;
        if (pkb.debugger.isDebugOn()) {
            pkb.debugger.line(d -> d.append("tcp state ").append(tcp.getState()));
        }
        switch (tcp.getState()) {
            case CLOSED:
                return handleTcpClosed(pkb);
            case SYN_SENT:
                return handleTcpSynSent(pkb);
            case SYN_RECEIVED:
                return handleTcpSynReceived(pkb);
            case ESTABLISHED:
                return handleTcpEstablished(pkb);
            case FIN_WAIT_1:
                return handleTcpFinWait1(pkb);
            case FIN_WAIT_2:
                return handleTcpFinWait2(pkb);
            case CLOSE_WAIT:
                return handleTcpCloseWait(pkb);
            case CLOSING:
                return handleTcpClosing(pkb);
            case LAST_ACK:
                return handleLastAck(pkb);
            case TIME_WAIT:
                return handleTimeWait(pkb);
            default:
                Logger.shouldNotHappen("should not reach here");
                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger.line(d -> d.append("unexpected tcp state ").append(tcp.getState()));
                }
                return _returndrop(pkb);
        }
    }

    private TcpPacket buildSyn(TcpEntry tcp) {
        TcpPacket pkt = TcpUtils.buildCommonTcpResponse(tcp);
        pkt.setFlags(Consts.TCP_FLAGS_SYN);
        buildSynCommon(tcp, pkt);
        return pkt;
    }

    private TcpPacket buildSynAck(PacketBuffer pkb) {
        TcpPacket respondTcp = TcpUtils.buildCommonTcpResponse(pkb.tcp);
        respondTcp.setFlags(Consts.TCP_FLAGS_SYN | Consts.TCP_FLAGS_ACK);
        buildSynCommon(pkb.tcp, respondTcp);
        return respondTcp;
    }

    private void buildSynCommon(TcpEntry tcp, TcpPacket respondTcp) {
        respondTcp.setWindow(65535);
        {
            var optMss = new TcpPacket.TcpOption(respondTcp);
            optMss.setKind(Consts.TCP_OPTION_MSS);
            optMss.setData(ByteArray.allocate(2).int16(0, TcpEntry.RCV_MSS));
            respondTcp.getOptions().add(optMss);
        }
        {
            int scale = tcp.receivingQueue.getWindowScale();
            int cnt = 0;
            while (scale != 1) {
                scale /= 2;
                cnt += 1;
            }
            if (cnt != 0) {
                var optWindowScale = new TcpPacket.TcpOption(respondTcp);
                optWindowScale.setKind(Consts.TCP_OPTION_WINDOW_SCALE);
                optWindowScale.setData(ByteArray.allocate(1).set(0, (byte) cnt));
                respondTcp.getOptions().add(optWindowScale);
            }
        }
        {
            var optSackPermitted = new TcpPacket.TcpOption(respondTcp);
            optSackPermitted.setKind(Consts.TCP_OPTION_SACK_PERMITTED);
            optSackPermitted.setData(ByteArray.allocate(0));
            respondTcp.getOptions().add(optSackPermitted);
        }
    }

    private HandleResult handleTcpClosed(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handleTcpClosed");

        var tcpPkt = pkb.tcpPkt;
        // only handle syn
        if (tcpPkt.getFlags() != Consts.TCP_FLAGS_SYN) {
            assert Logger.lowLevelDebug("not SYN packet, respond with RST");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("not SYN packet, respond with RST"));
            }
            return _returnnext(pkb, tcpReset);
        }
        if (pkb.ensurePartialPacketParsed()) {
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("invalid packet"));
            }
            return _returndropSkipErrorDrop();
        }

        pkb.tcp.setState(TcpState.SYN_RECEIVED);
        initTcp(pkb.tcp, pkb.tcpPkt);

        // SYN-ACK
        TcpPacket respondTcp = buildSynAck(pkb);
        AbstractIpPacket respondIp = TcpUtils.buildIpResponse(pkb.tcp, respondTcp);

        pkb.tcp.sendingQueue.incAllSeq();

        pkb.replacePacket(respondIp);
        return _returnnext(pkb, l4output);
    }

    private void initTcp(TcpEntry tcp, TcpPacket tcpPkt) {
        // get tcp options from the syn
        int mss = TcpEntry.SND_MAX_MSS;
        int windowScale = 1;
        boolean sackPermitted = false;
        for (var opt : tcpPkt.getOptions()) {
            switch (opt.getKind()) {
                case Consts.TCP_OPTION_MSS:
                    mss = opt.getData().uint16(0);
                    if (mss > TcpEntry.SND_MAX_MSS) {
                        mss = TcpEntry.SND_MAX_MSS;
                    }
                    break;
                case Consts.TCP_OPTION_WINDOW_SCALE:
                    int s = opt.getData().uint8(0);
                    windowScale = 1 << s;
                    break;
                case Consts.TCP_OPTION_SACK_PERMITTED:
                    sackPermitted = true;
                    break;
            }
        }
        tcp.setRemoteSackPermitted(sackPermitted);
        tcp.sendingQueue.init(tcpPkt.getWindow(), mss, windowScale,
            getInitialCwndForPeer(tcp.remote.getAddress()));
    }

    private HandleResult handleTcpSynSent(PacketBuffer pkb) {
        var tcpPkt = pkb.tcpPkt;
        if (tcpPkt.isSyn() && tcpPkt.isAck()) {
            // is syn-ack packet
            assert Logger.lowLevelDebug("syn-ack received");
            if (tcpPkt.getAckNum() == pkb.tcp.sendingQueue.getAckSeq()) {
                assert Logger.lowLevelDebug("seq matches");
                if (pkb.tcp.retransmissionTimer != null) {
                    pkb.tcp.retransmissionTimer.cancel();
                    pkb.tcp.retransmissionTimer = null;
                }
                pkb.tcp.receivingQueue.setInitialSeq(tcpPkt.getSeqNum() + 1);
                initTcp(pkb.tcp, pkb.tcpPkt);
                connectionEstablishes(pkb);
                var ack = TcpUtils.buildAckResponse(pkb.tcp);
                var ipPkt = TcpUtils.buildIpResponse(pkb.tcp, ack);
                pkb.replacePacket(ipPkt);
                return _returnnext(pkb, l4output);
            } else {
                assert Logger.lowLevelDebug("received packet ack doesn't match sending seq");
            }
        } else {
            assert Logger.lowLevelDebug("received packet is not syn-ack");
        }
        return _returndrop(pkb);
    }

    private HandleResult handleTcpSynReceived(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handleTcpSynReceived");
        // first check whether the packet has ack, and if so, check the ack number
        var tcpPkt = pkb.tcpPkt;
        if (tcpPkt.isSyn()) {
            assert Logger.lowLevelDebug("probably a syn retransmission");
            if (tcpPkt.getSeqNum() == pkb.tcp.receivingQueue.getAckedSeq() - 1) {
                assert Logger.lowLevelDebug("seq matches");
                pkb.tcp.sendingQueue.decAllSeq();
                TcpPacket respondTcp = buildSynAck(pkb);
                AbstractIpPacket respondIp = TcpUtils.buildIpResponse(pkb.tcp, respondTcp);
                pkb.tcp.sendingQueue.incAllSeq();
                pkb.replacePacket(respondIp);
                return _returnnext(pkb, l4output);
            }
        }
        if (!tcpPkt.isAck()) {
            assert Logger.lowLevelDebug("no ack flag set");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("no ack flag"));
            }
            return _returndrop(pkb);
        }
        if (tcpPkt.getAckNum() != pkb.tcp.sendingQueue.getAckSeq()) {
            assert Logger.lowLevelDebug("wrong ack number");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("wrong ack number"));
            }
            return _returndrop(pkb);
        }
        connectionEstablishes(pkb);

        // then run the same handling as established
        return handleTcpEstablished(pkb);
    }

    private void connectionEstablishes(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("connectionEstablishes");
        pkb.tcp.setState(TcpState.ESTABLISHED);
        // alert that this connection can be retrieved
        var parent = pkb.tcp.getParent();
        if (parent == null) {
            return;
        }
        parent.synBacklog.remove(pkb.tcp);
        parent.backlog.add(pkb.tcp);
        parent.listenHandler.readable(parent);
    }

    private boolean handleTcpGeneralReturnFalse(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handleTcpGeneral");

        var tcpPkt = pkb.tcpPkt;

        // check whether seq matches
        var seq = tcpPkt.getSeqNum();
        var expect = pkb.tcp.receivingQueue.getExpectingSeq();
        var acked = pkb.tcp.receivingQueue.getAckedSeq();
        if (tcpPkt.isFin()) {
            if (seq != acked) {
                assert Logger.lowLevelDebug("data not fully consumed yet but received FIN");
                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger.line(d -> d.append("data not fully consumed yet but received FIN"));
                }
                return true;
            }
        } else if (seq != expect) {
            if (!tcpPkt.isPsh()) {
                assert Logger.lowLevelDebug("invalid sequence number for non-PSH packet");
                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger.line(d -> d.append("invalid sequence number"));
                }
                return true;
            }
            // PSH with seq != expect:
            //   seq < expect: retransmission or partial overlap, ReceivingQueue will handle
            //   seq > expect: out-of-order, ReceivingQueue will buffer it
        }

        if (tcpPkt.isAck()) {
            long ack = tcpPkt.getAckNum();
            int window = tcpPkt.getWindow();
            long oldAckSeq = pkb.tcp.sendingQueue.getAckSeq();
            if (tcpStackDebugOn) {
                Logger.alert("TCP ACK received: ack=" + ack + " oldAckSeq=" + oldAckSeq + " bytesInFlight=" + pkb.tcp.sendingQueue.getBytesInFlight() + " cwnd=" + pkb.tcp.sendingQueue.getCwnd() + " window=" + pkb.tcp.sendingQueue.getWindow());
            }
            pkb.tcp.sendingQueue.ack(ack, window);
            // when new data is acked, immediately try to send more data
            // rather than waiting for the retransmission timer to expire
            if (ack > oldAckSeq) {
                // parse CWND option only when ack num increased
                handleCwndOption(tcpPkt, pkb.tcp);

                pkb.tcp.sendingQueue.resetDupAckCount();
                // cancel existing retransmission timer; we'll send fresh now
                if (pkb.tcp.retransmissionTimer != null) {
                    pkb.tcp.retransmissionTimer.cancel();
                    pkb.tcp.retransmissionTimer = null;
                }
                _tcpStartRetransmission(pkb.network, pkb.tcp);
            } else {
                List<SAckTuple> sackBlocks = null;
                if (pkb.tcp.isRemoteSackPermitted()) {
                    sackBlocks = parseSackBlocks(tcpPkt);
                }
                if (sackBlocks != null && !sackBlocks.isEmpty()) {
                    startSackRetransmit(pkb.network, pkb.tcp, sackBlocks);
                } else if (tcpPkt.getData().length() == 0) {
                    // fast retransmit: trigger after N duplicate ACKs
                    int dupCount = pkb.tcp.sendingQueue.incrementDupAckCount();
                    if (dupCount >= 3) {
                        pkb.tcp.sendingQueue.resetDupAckCount();
                        transmitTcpPsh(pkb.network, pkb.tcp, new RetransContext().retransmissionCount(1));
                    }
                }
                if ((sackBlocks != null || tcpPkt.getData().length() == 0) && pkb.tcp.retransmissionTimer == null) {
                    _tcpStartRetransmission(pkb.network, pkb.tcp);
                }
            }
        }
        return false;
    }

    private HandleResult handleTcpEstablished(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handleTcpEstablished");
        if (handleTcpGeneralReturnFalse(pkb)) {
            return _returndrop(pkb);
        }
        if (pkb.tcpPkt.isSyn() && pkb.tcpPkt.isAck()) {
            assert Logger.lowLevelDebug("received syn-ack, probably a retransmission");
            var respondTcp = TcpUtils.buildAckResponse(pkb.tcp);
            var respondIp = TcpUtils.buildIpResponse(pkb.tcp, respondTcp);
            pkb.replacePacket(respondIp);
            return _returnnext(pkb, l4output);
        }
        var tcpPkt = pkb.tcpPkt;
        if (tcpPkt.isPsh()) {
            long seq = tcpPkt.getSeqNum();
            ByteArray data = tcpPkt.getData();
            pkb.tcp.receivingQueue.store(new Segment(seq, data.copy()));
            _tcpAck(pkb.network, pkb.tcp);
        }
        if (tcpPkt.isFin()) {
            pkb.tcp.setState(TcpState.CLOSE_WAIT);
            pkb.tcp.receivingQueue.incExpectingSeq();
            _tcpAck(pkb.network, pkb.tcp);
            return _return(HandleResult.STOLEN, pkb);
        }
        return _return(HandleResult.STOLEN, pkb);
    }

    private HandleResult handleTcpFinWait1(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handleTcpFinWait1");
        if (handleTcpGeneralReturnFalse(pkb)) {
            return _returndrop(pkb);
        }
        var tcpPkt = pkb.tcpPkt;
        if (tcpPkt.isFin()) {
            // ACK the peer's FIN
            pkb.tcp.receivingQueue.incExpectingSeq();
            if (pkb.tcp.sendingQueue.ackOfFinReceived()) {
                // our FIN is already acked, peer also sent FIN → TIME_WAIT
                assert Logger.lowLevelDebug("simultaneous FIN acked, transform to TIME_WAIT");
                _tcpAck(pkb.network, pkb.tcp);
                pkb.tcp.setState(TcpState.TIME_WAIT);
                scheduleTimeWaitClose(pkb);
            } else {
                // our FIN not yet acked, peer also sent FIN → CLOSING
                assert Logger.lowLevelDebug("simultaneous close, transform to CLOSING");
                _tcpAck(pkb.network, pkb.tcp);
                pkb.tcp.setState(TcpState.CLOSING);
            }
        } else {
            if (pkb.tcp.sendingQueue.ackOfFinReceived()) {
                assert Logger.lowLevelDebug("the sent FIN is acked, transform to FIN_WAIT_2");
                pkb.tcp.setState(TcpState.FIN_WAIT_2);
            }
        }
        return _return(HandleResult.STOLEN, pkb);
    }

    private HandleResult handleTcpFinWait2(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handleTcpFinWait2");
        if (handleTcpGeneralReturnFalse(pkb)) {
            return _returndrop(pkb);
        }
        var tcpPkt = pkb.tcpPkt;
        if (tcpPkt.isFin()) {
            assert Logger.lowLevelDebug("received FIN, transform to TIME_WAIT");
            pkb.tcp.receivingQueue.incExpectingSeq();
            _tcpAck(pkb.network, pkb.tcp);
            pkb.tcp.setState(TcpState.TIME_WAIT);
            scheduleTimeWaitClose(pkb);
            return _return(HandleResult.STOLEN, pkb);
        }
        return _return(HandleResult.STOLEN, pkb);
    }

    private HandleResult handleTcpCloseWait(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handleTcpCloseWait");
        if (handleTcpGeneralReturnFalse(pkb)) {
            return _returndrop(pkb);
        }
        var tcpPkt = pkb.tcpPkt;
        if (tcpPkt.isFin()) {
            assert Logger.lowLevelDebug("received FIN again, maybe it's retransmission");
            if (tcpPkt.getSeqNum() == pkb.tcp.receivingQueue.getExpectingSeq() - 1) {
                _tcpAck(pkb.network, pkb.tcp);
                return _return(HandleResult.STOLEN, pkb);
            }
        }
        return _return(HandleResult.STOLEN, pkb);
    }

    private HandleResult handleTcpClosing(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handleTcpClosing");
        if (handleTcpGeneralReturnFalse(pkb)) {
            return _returndrop(pkb);
        }
        if (pkb.tcp.sendingQueue.ackOfFinReceived()) {
            assert Logger.lowLevelDebug("FIN acked in CLOSING, transform to TIME_WAIT");
            pkb.tcp.setState(TcpState.TIME_WAIT);
            scheduleTimeWaitClose(pkb);
        }
        return _return(HandleResult.STOLEN, pkb);
    }

    private HandleResult handleLastAck(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handleLastAck");
        if (handleTcpGeneralReturnFalse(pkb)) {
            return _returndrop(pkb);
        }
        if (pkb.tcp.sendingQueue.ackOfFinReceived()) {
            assert Logger.lowLevelDebug("FIN acked in LAST_ACK, connection fully closed");
            recordPeerCwnd(pkb.tcp);
            pkb.tcp.setState(TcpState.CLOSED);
            pkb.tcp.destroy();
            pkb.network.conntrack.removeTcp(pkb.tcp.remote, pkb.tcp.local);
        }
        return _return(HandleResult.STOLEN, pkb);
    }

    private HandleResult handleTimeWait(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handleTimeWait");
        var tcpPkt = pkb.tcpPkt;
        // retransmitted FIN — re-ACK it
        if (tcpPkt.isFin()) {
            _tcpAck(pkb.network, pkb.tcp);
            return _return(HandleResult.STOLEN, pkb);
        }
        // drop everything else in TIME_WAIT
        return _return(HandleResult.STOLEN, pkb);
    }

    private void scheduleTimeWaitClose(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("scheduleTimeWaitClose for " + pkb.tcp);
        if (pkb.tcp.retransmissionTimer != null) {
            pkb.tcp.retransmissionTimer.cancel();
            pkb.tcp.retransmissionTimer = null;
        }
        final TcpEntry tcp = pkb.tcp;
        final VirtualNetwork network = pkb.network;
        tcp.retransmissionTimer = sw.getSelectorEventLoop().delay(TcpEntry.TIME_WAIT_TIMEOUT_MS, () -> {
            recordPeerCwnd(tcp);
            tcp.setState(TcpState.CLOSED);
            tcp.destroy();
            network.conntrack.removeTcp(tcp.remote, tcp.local);
        });
    }

    private void _tcpAck(VirtualNetwork network, TcpEntry tcp) {
        assert Logger.lowLevelDebug("tcpAck(" + ", " + network + ", " + tcp + ")");

        if (tcp.receivingQueue.getWindow() == 0) {
            assert Logger.lowLevelDebug("no window, very bad, need to ack immediately");
            sendAck(network, tcp);
            return;
        }
        if (tcp.delayedAckTimer != null) {
            assert Logger.lowLevelDebug("delayed ack already scheduled");
            return;
        }
        int delayedAckTimeout = TcpEntry.DELAYED_ACK_TIMEOUT;
        if (tcp.isRemoteSackPermitted() && tcp.receivingQueue.hasOutOfOrderData()) {
            delayedAckTimeout = TcpEntry.DELAYED_ACK_TIMEOUT_FOR_SACK;
        }
        tcp.delayedAckTimer = sw.getSelectorEventLoop().delay(delayedAckTimeout, () -> sendAck(network, tcp));
    }

    public void tcpAck(VirtualNetwork network, TcpEntry tcp) {
        VProxyThread.current().newUuidDebugInfo();
        _tcpAck(network, tcp);
    }

    private void sendAck(VirtualNetwork network, TcpEntry tcp) {
        VProxyThread.current().newUuidDebugInfo();
        assert Logger.lowLevelDebug("sendAck(" + ", " + network + ", " + tcp + ")");

        if (tcp.delayedAckTimer != null) {
            tcp.delayedAckTimer.cancel();
            tcp.delayedAckTimer = null;
        }

        TcpPacket respondTcp;
        if (tcp.isRemoteSackPermitted()) {
            List<SAckTuple> sackBlocks = tcp.receivingQueue.getSAckBlocks();
            if (!sackBlocks.isEmpty()) {
                respondTcp = TcpUtils.buildAckResponseWithSack(tcp, sackBlocks);
            } else {
                respondTcp = TcpUtils.buildAckResponse(tcp);
            }
        } else {
            respondTcp = TcpUtils.buildAckResponse(tcp);
        }
        // attach CWND option for peer cwnd negotiation
        respondTcp.getOptions().add(buildCwndOption(respondTcp, tcp));
        AbstractIpPacket respondIp = TcpUtils.buildIpResponse(tcp, respondTcp);

        PacketBuffer pkb = PacketBuffer.fromPacket(network, respondIp);
        pkb.tcp = tcp;

        int count = tcp.getAckMultiplier();
        for (int i = 0; i < count; i++) {
            if (i + 1 < count) {
                var dupPkb = pkb.copy();
                dupPkb.tcp = tcp;
                _output(dupPkb);
            } else {
                _output(pkb);
            }
        }
    }

    private List<SAckTuple> parseSackBlocks(TcpPacket tcpPkt) {
        tcpPkt.ensureOptions();

        List<SAckTuple> blocks = null;
        for (var opt : tcpPkt.getOptions()) {
            if (opt.getKind() == Consts.TCP_OPTION_SACK) {
                if (blocks == null) {
                    blocks = new ArrayList<>();
                }
                ByteArray data = opt.getData();
                if (data == null) continue;
                int numBlocks = data.length() / 8;
                for (int i = 0; i < numBlocks; i++) {
                    long start = data.uint32(i * 8);
                    long end = data.uint32(i * 8 + 4);
                    blocks.add(new SAckTuple(start, end));
                }
            }
        }
        if (blocks != null) {
            blocks.sort((a, b) -> (int) (a.seqBeginInclusive - b.seqBeginInclusive));
        }
        return blocks;
    }

    /**
     * Parse a received CWND option and update the TcpEntry's yourCwnd field.
     * The peer advertises its cwnd as selfCwnd in the option; that becomes our yourCwnd.
     */
    private void handleCwndOption(TcpPacket tcpPkt, TcpEntry tcp) {
        tcpPkt.ensureOptions();
        for (var opt : tcpPkt.getOptions()) {
            if (opt.getKind() == Consts.TCP_OPTION_CWND) {
                ByteArray data = opt.getData();
                // the peer's selfCwnd (first 3 bytes) is our yourCwnd
                int peerSelfCwnd = data.uint24(0);
                if (peerSelfCwnd > 0) {
                    tcp.sendingQueue.setYourCwnd(peerSelfCwnd);
                }
                return; // only process the first CWND option
            }
        }
    }

    private void startSackRetransmit(VirtualNetwork network, TcpEntry tcp, List<SAckTuple> sackBlocks) {
        tcp.sendingQueue.markSackRetransmitSegments(sackBlocks);
        if (tcp.retransmissionTimer != null) {
            tcp.retransmissionTimer.cancel();
            tcp.retransmissionTimer = null;
        }
        setRetransmitTimer(network, tcp, new RetransContext().sackRetransmit().retransmissionCount(1));
    }

    private void _tcpStartRetransmission(VirtualNetwork network, TcpEntry tcp) {
        assert Logger.lowLevelDebug("tcpStartRetransmission(" + network + "," + tcp + ")");
        transmitTcp(network, tcp, new RetransContext());
    }

    public void tcpStartRetransmission(VirtualNetwork network, TcpEntry tcp) {
        VProxyThread.current().newUuidDebugInfo();
        _tcpStartRetransmission(network, tcp);
    }

    private void transmitTcp(VirtualNetwork network, TcpEntry tcp, RetransContext ctx) {
        assert Logger.lowLevelDebug("transmitTcp(" + network + "," + tcp + ")");
        if (tcpStackDebugOn) {
            Logger.alert("transmit tcp lastBeginSeq=" + ctx.lastBeginSeq + ", retransmissionCount=" + ctx.retransmissionCount);
        }

        if (tcp.retransmissionTimer != null) { // reset timer
            tcp.retransmissionTimer.cancel();
            tcp.retransmissionTimer = null;
        }

        // check whether need to reset the connection because of too many retransmits
        if (tcp.requireClosing() && ctx.retransmissionCount > TcpEntry.MAX_RETRANSMISSION_AFTER_CLOSING) {
            assert Logger.lowLevelDebug("conn " + tcp + " is closed due to too many retransmission after closing");
            _resetTcpConnection(network, tcp);
            return;
        }

        // check broken pipe: per-segment retransmission timeout
        var timedOutSeg = tcp.sendingQueue.checkRetransmissionTimeout();
        if (timedOutSeg != null) {
            Logger.error(LogType.SYS_ERROR, "Broken Pipe: segment [" + timedOutSeg.seqBeginInclusive + ", " + timedOutSeg.seqEndExclusive + ") retransmission timeout after " + TcpEntry.RETRANSMISSION_TIMEOUT_MS + "ms for " + tcp);
            _resetTcpConnection(network, tcp);
            tcp.destroy();
            return;
        }

        assert Logger.lowLevelDebug("current tcp state is " + tcp.getState());
        if (tcp.getState() == TcpState.CLOSED || tcp.getState() == TcpState.SYN_SENT) {
            transmitTcpSyn(network, tcp, ctx);
        } else {
            transmitTcpPsh(network, tcp, ctx);
        }
    }

    private void transmitTcpSyn(VirtualNetwork network, TcpEntry tcp, RetransContext ctx) {
        tcp.setState(TcpState.SYN_SENT);
        sendTcpSyn(network, tcp);
        ctx.lastBeginSeq = 0;
        setRetransmitTimer(network, tcp, ctx);
    }

    private void transmitTcpPsh(VirtualNetwork network, TcpEntry tcp, RetransContext ctx) {
        // handle retransmission congestion before fetching
        if (ctx.retransmissionCount > 0) {
            if (!ctx.isSpecialAck() && ctx.retransmissionCount >= 2) {
                // only treat as congestion after multiple retransmission attempts,
                // not on the first timer expiry (which may just mean no ACK arrived yet)
                tcp.sendingQueue.onLoss();
            }
        } else if (tcp.sendingQueue.getAvailableSendWindow() <= 0) {
            // check congestion window before fetching data (only for non-retransmissions)
            assert Logger.lowLevelDebug("cwnd full, waiting for ACKs before sending more");
            afterTransmission(network, tcp);
            return;
        }
        List<Segment> segments = tcp.sendingQueue.fetch(ctx.retransmissionCount > 0);
        if (segments.isEmpty()) { // no data to send, check FIN
            if (tcp.sendingQueue.needToSendFin()) {
                assert Logger.lowLevelDebug("need to send FIN");
                // fall through
            } else if (tcp.sendingQueue.getBytesInFlight() > 0) {
                // fetch() returned nothing but there are still in-flight segments, wait for retransmission
                assert Logger.lowLevelDebug("fetch empty but bytesInFlight=" + tcp.sendingQueue.getBytesInFlight());
                if (tcp.retransmissionTimer == null) {
                    setRetransmitTimer(network, tcp, ctx);
                }
                return;
            } else {
                // nothing to send and nothing in flight
                assert Logger.lowLevelDebug("no need to retransmit after " + ctx.retransmissionCount + " time(s)");
                if (tcp.retransmissionTimer != null) {
                    tcp.retransmissionTimer.cancel();
                    tcp.retransmissionTimer = null;
                }
                afterTransmission(network, tcp);
                return;
            }
        }
        long currentBeginSeq = segments.isEmpty() ? tcp.sendingQueue.getFetchSeq() + 1 : segments.get(0).seqBeginInclusive;
        if (currentBeginSeq != ctx.lastBeginSeq) {
            assert Logger.lowLevelDebug("the sequence increased, it's not retransmitting after " + ctx.retransmissionCount + " time(s)");
            ctx.retransmissionCount = 0;
        }

        // initiate timer
        ctx.lastBeginSeq = currentBeginSeq;
        setRetransmitTimer(network, tcp, ctx);

        if (segments.isEmpty()) {
            assert tcp.sendingQueue.needToSendFin();
            sendTcpFin(network, tcp);
        } else {
            // Data packets carry ACK flags, so cancel any pending delayed ACK to avoid
            // sending a redundant standalone ACK after the data packets have already
            // acknowledged the received data.
            if (tcp.delayedAckTimer != null && !tcp.receivingQueue.hasOutOfOrderData()) {
                tcp.delayedAckTimer.cancel();
                tcp.delayedAckTimer = null;
            }
            for (var s : segments) {
                sendTcpPsh(network, tcp, s);
            }
        }
        ctx.resetSpecialAck();
    }

    private void setRetransmitTimer(VirtualNetwork network, TcpEntry tcp, RetransContext ctx) {
        long delay;
        if (ctx.sackRetransmit) {
            delay = TcpEntry.RTO_MIN; // fixed 100ms for SACK retransmit
        } else {
            delay = tcp.sendingQueue.getRto() << ctx.retransmissionCount;
            if (delay <= 0 || delay > TcpEntry.RTO_MAX) { // overflow or exceeds maximum
                delay = TcpEntry.RTO_MAX;
            }
        }
        assert Logger.lowLevelDebug("will delay " + delay + " ms then retransmit");
        final int finalDelay = (int) delay;
        tcp.retransmissionTimer = sw.getSelectorEventLoop().delay(finalDelay, () -> {
            ctx.retransmissionCount++;
            transmitTcp(network, tcp, ctx);
        });
    }

    private void afterTransmission(VirtualNetwork network, TcpEntry tcp) {
        assert Logger.lowLevelDebug("afterTransmission(" + network + "," + tcp + "," + ")");

        if (tcp.requireClosing()) {
            assert Logger.lowLevelDebug("need to be closed");
            _resetTcpConnection(network, tcp);
        }
    }

    private void _resetTcpConnection(VirtualNetwork network, TcpEntry tcp) {
        assert Logger.lowLevelDebug("resetTcpConnection(" + network + "," + tcp + "," + ")");

        PacketBuffer pkb = PacketBuffer.fromPacket(network, TcpUtils.buildIpResponse(tcp, TcpUtils.buildRstResponse(tcp)));
        pkb.tcp = tcp;
        _output(pkb);

        recordPeerCwnd(tcp);
        tcp.setState(TcpState.CLOSED);
        network.conntrack.removeTcp(tcp.remote, tcp.local);
    }

    public void resetTcpConnection(VirtualNetwork network, TcpEntry tcp) {
        VProxyThread.current().newUuidDebugInfo();
        _resetTcpConnection(network, tcp);
    }

    private void sendTcpSyn(VirtualNetwork network, TcpEntry tcp) {
        tcp.sendingQueue.decAllSeq();
        var tcpPkt = buildSyn(tcp);
        AbstractIpPacket ipPkt = TcpUtils.buildIpResponse(tcp, tcpPkt);
        tcp.sendingQueue.incAllSeq();

        PacketBuffer pkb = PacketBuffer.fromPacket(network, ipPkt);
        pkb.tcp = tcp;
        _output(pkb);
    }

    private void sendTcpPsh(VirtualNetwork network, TcpEntry tcp, Segment s) {
        VProxyThread.current().newUuidDebugInfo();
        assert Logger.lowLevelDebug("sendTcpPsh(" + network + "," + tcp + "," + s + ")");
        if (tcpStackDebugOn) {
            Logger.alert("TCP PSH sent: seq=" + s.seqBeginInclusive + " len=" + s.data.length() + " expectAck=" + (s.seqBeginInclusive + s.data.length()));
        }

        TcpPacket tcpPkt = TcpUtils.buildCommonTcpResponse(tcp);
        tcpPkt.setSeqNum(s.seqBeginInclusive);
        tcpPkt.setFlags(Consts.TCP_FLAGS_PSH | Consts.TCP_FLAGS_ACK);
        tcpPkt.setData(s.data);
        // attach CWND option for peer cwnd negotiation
        tcpPkt.getOptions().add(buildCwndOption(tcpPkt, tcp));
        AbstractIpPacket ipPkt = TcpUtils.buildIpResponse(tcp, tcpPkt);

        PacketBuffer pkb = PacketBuffer.fromPacket(network, ipPkt);
        pkb.tcp = tcp;

        int count = tcp.getPshMultiplier(s.data.length());
        for (int i = 0; i < count; i++) {
            if (i + 1 < count) {
                var dupPkb = pkb.copy();
                dupPkb.tcp = tcp;
                _output(dupPkb);
            } else {
                _output(pkb);
            }
        }
    }

    private void sendTcpFin(VirtualNetwork network, TcpEntry tcp) {
        VProxyThread.current().newUuidDebugInfo();
        assert Logger.lowLevelDebug("sendTcpFin(" + network + "," + tcp + ")");

        TcpPacket tcpPkt = TcpUtils.buildCommonTcpResponse(tcp);
        tcpPkt.setSeqNum(tcp.sendingQueue.getFetchSeq());
        tcpPkt.setFlags(Consts.TCP_FLAGS_FIN | Consts.TCP_FLAGS_ACK);
        AbstractIpPacket ipPkt = TcpUtils.buildIpResponse(tcp, tcpPkt);

        PacketBuffer pkb = PacketBuffer.fromPacket(network, ipPkt);
        pkb.tcp = tcp;
        _output(pkb);
    }

    private void recordPeerCwnd(TcpEntry tcp) {
        if (tcp.getState() == TcpState.CLOSED) {
            return;
        }
        if (tcp.remote == null) {
            return;
        }
        int cwnd = tcp.sendingQueue.getCwnd();
        if (cwnd <= 0) {
            return;
        }
        peerCwndMap.computeIfAbsent(tcp.remote.getAddress(), k -> new PeerCwndHistory()).add(cwnd);
    }

    private int getInitialCwndForPeer(IP peerIp) {
        PeerCwndHistory history = peerCwndMap.get(peerIp);
        if (history == null) {
            return TcpEntry.INIT_CWND;
        }
        int avg = history.getAverage();
        if (avg <= 0) {
            return TcpEntry.INIT_CWND;
        }
        return Math.min(avg, TcpEntry.MAX_CWND);
    }

    /**
     * Build a custom CWND TCP option carrying selfCwnd and yourCwnd.
     * Format: kind(1) + length(1) + selfCwnd(3) + yourCwnd(3) = 8 bytes
     */
    private TcpPacket.TcpOption buildCwndOption(TcpPacket tcpPkt, TcpEntry tcp) {
        var opt = new TcpPacket.TcpOption(tcpPkt);
        opt.setKind(Consts.TCP_OPTION_CWND);
        ByteArray data = ByteArray.allocate(6);
        data.int24(0, tcp.sendingQueue.getCwnd());
        data.int24(3,
            tcp.receivingQueue.hasOutOfOrderData() ? 0 :
                tcp.sendingQueue.getYourCwnd());
        opt.setData(data);
        return opt;
    }

    private void _output(PacketBuffer pkb) {
        _schedule(sw.scheduler, pkb, l4output);
    }

    public void output(PacketBuffer pkb) {
        VProxyThread.current().newUuidDebugInfo();
        _output(pkb);
    }

    private static class PeerCwndHistory {
        private final long[] values = new long[10];
        private int count = 0;
        private int idx = 0;
        private long sum = 0;

        void add(int cwnd) {
            if (count == values.length) {
                sum -= values[idx];
            } else {
                count++;
            }
            values[idx] = cwnd;
            sum += cwnd;
            idx = (idx + 1) % values.length;
        }

        int getAverage() {
            if (count == 0) {
                return 0;
            }
            return (int) (sum / count);
        }
    }
}
