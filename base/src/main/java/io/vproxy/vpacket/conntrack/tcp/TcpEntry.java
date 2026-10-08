package io.vproxy.vpacket.conntrack.tcp;

import io.vproxy.base.Config;
import io.vproxy.base.selector.TimerEvent;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.OS;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.bytearray.SimpleByteArray;
import io.vproxy.base.util.misc.WithUserData;
import io.vproxy.vfd.IPPort;

import java.nio.ByteBuffer;
import java.util.*;

public class TcpEntry implements WithUserData {
    public static final int WMEM_MAX = OS.isIOS() ? 64 * 1024 : 1048576; // 1MB for high-latency BDP, 64K on iOS
    public static final int RMEM_MAX = OS.isIOS() ? 64 * 1024 : 1048576;
    public static final int SND_MAX_MSS = 1400; // xdp frame size = 2048, hardware reserved = 256, headroom = 128, mac+ip+tcp_hdr=14+40+20
    public static final int RCV_MSS = 1400;
    public static final int MIN_MSS = 48; // same floor as Linux TCP_MIN_SND_MSS, prevents DoS via tiny-MSS
    public static final int TCP_SEQ_INIT_MIN = Integer.MAX_VALUE / 3;
    public static final int TCP_SEQ_RAND = Integer.MAX_VALUE / 2;
    public static final int RTO_MIN = 100;
    public static final int RTO_MAX = 2_000;
    public static final int DELAYED_ACK_TIMEOUT = 20; // balanced for high-latency
    public static final int DELAYED_ACK_TIMEOUT_FOR_SACK = 20; // balanced for high-latency
    public static final int IMMEDIATE_ACK_DATA_SIZE = 100 * 1024 * 1024 / 8 / 1000 * DELAYED_ACK_TIMEOUT / 2; // bandwidth(100Mbps)*1024*1024/8 / 1000 * DELAYED_ACK_TIMEOUT / 2(half)
    public static final int MAX_REMOTE_WINDOW = 16 * 1024 * 1024; // 16MB, safety cap for peer's advertised window
    public static final int MAX_CWND = 20 * 1024 * 1024; // 20MB max cwnd
    public static final int INIT_CWND = MAX_CWND / 10;
    public static final int MAX_RETRANSMISSION_AFTER_CLOSING = 14;
    public static final int TIME_WAIT_TIMEOUT_MS = 24_000;
    public static final int RETRANSMISSION_TIMEOUT_MS = 15_000;
    // 多倍发包参数
    private int pshMultiplier = 1;
    private int ackMultiplier = 1;

    // 高延迟/高丢包网络优化参数
    public static final int HIGH_LATENCY_MIN_CWND_MSS = 10; // 最小cwnd为10*MSS

    public final IPPort remote;
    public final IPPort local;
    private TcpState state;
    private boolean needClosing = false;
    private boolean remoteSackPermitted = false;

    public final SendingQueue sendingQueue;
    public final ReceivingQueue receivingQueue;
    final TcpConnectionDiagnostics diagnostics;

    public TimerEvent retransmissionTimer = null;
    public TimerEvent persistTimer = null;
    public TimerEvent delayedAckTimer = null;
    public int persistProbeCount = 0;
    public long synBacklogTimestamp = 0;

    // only one of these fields can be null
    // {
    private ConnectionHandler connectionHandler;
    private TcpListenEntry parent;
    private TcpNat nat;
    // }

    public TcpEntry(TcpListenEntry listenEntry, IPPort remote, IPPort local, long seq) {
        this.parent = listenEntry;
        this.remote = remote;
        this.local = local;
        this.state = TcpState.CLOSED;
        this.sendingQueue = new SendingQueue(new Random().nextInt(TCP_SEQ_RAND) + TCP_SEQ_INIT_MIN);
        this.receivingQueue = new ReceivingQueue(seq == 0 ? 0 : seq + 1 /* the sequence is syn_packet.seq + 1 */);
        this.diagnostics = TcpConnectionDiagnostics.enabled() ? new TcpConnectionDiagnostics() : null;
        this.synBacklogTimestamp = Config.currentTimestamp;
    }

    public TcpEntry(IPPort remote, IPPort local) {
        this.remote = remote;
        this.local = local;
        this.sendingQueue = null;
        this.receivingQueue = null;
        this.diagnostics = null;
        this.state = TcpState.CLOSED;
    }

    public TcpListenEntry getParent() {
        return parent;
    }

    public void setConnectionHandler(ConnectionHandler connectionHandler) {
        if (this.connectionHandler != null) {
            throw new IllegalStateException("cannot set connectionHandler because it already exists");
        }
        if (nat != null) {
            throw new IllegalStateException("this is a connection being handled by nat, cannot set connectionHandler on this connection");
        }
        this.connectionHandler = connectionHandler;
        this.parent = null;
    }

    public TcpNat getNat() {
        return nat;
    }

    public void setNat(TcpNat nat) {
        if (this.nat != null) {
            throw new IllegalStateException("cannot set nat because it already exists");
        }
        if (connectionHandler != null || parent != null) {
            throw new IllegalStateException("this is a connection being handled by tcp stack, cannot set nat on this connection");
        }
        this.nat = nat;
    }

    public void destroy() {
        TcpState oldState = state;
        state = TcpState.CLOSED;
        if (diagnostics != null) {
            diagnostics.reportOnce(this, oldState);
        }
        if (retransmissionTimer != null) {
            retransmissionTimer.cancel();
            retransmissionTimer = null;
        }
        if (persistTimer != null) {
            persistTimer.cancel();
            persistTimer = null;
        }
        if (delayedAckTimer != null) {
            delayedAckTimer.cancel();
            delayedAckTimer = null;
        }
        if (connectionHandler != null) {
            connectionHandler.destroy(this);
            connectionHandler = null;
        }
    }

    public TcpState getState() {
        return state;
    }

    public void setState(TcpState state) {
        var old = this.state;
        assert Logger.lowLevelDebug("tcp state changing: " + old + " -> " + state + ", connectionHandler = " + connectionHandler);
        this.state = state;
        if (!old.remoteClosed && state.remoteClosed) {
            if (connectionHandler != null) {
                connectionHandler.readable(this);
            }
        }
        if (old == TcpState.SYN_SENT && state == TcpState.ESTABLISHED) {
            if (connectionHandler != null) {
                connectionHandler.writable(this);
            }
        }
    }

    public boolean diagnosticsEnabled() {
        return diagnostics != null;
    }

    public void diagnosticReceived(long seq, int bytes) {
        if (diagnostics != null) diagnostics.received(this, seq, bytes);
    }

    public void diagnosticSent(long seq, int bytes, String retransmissionReason) {
        if (diagnostics != null) diagnostics.sent(this, seq, bytes, retransmissionReason);
    }

    public void diagnosticAcknowledged(long ackSeq, long bytes) {
        if (diagnostics != null) diagnostics.acknowledged(this, ackSeq, bytes);
    }

    public void diagnosticPersistProbe(int bytes) {
        if (diagnostics != null) diagnostics.persistProbe(this, bytes);
    }

    public void diagnosticReceivedFin() {
        if (diagnostics != null) diagnostics.receivedFin();
    }

    public void diagnosticSentFin() {
        if (diagnostics != null) diagnostics.sentFin();
    }

    public void diagnosticReceivedRst() {
        if (diagnostics != null) diagnostics.receivedRst();
    }

    public void diagnosticSentRst() {
        if (diagnostics != null) diagnostics.sentRst();
    }

    public void diagnosticCloseReason(String reason) {
        if (diagnostics != null) diagnostics.closeReason(reason);
    }

    public void diagnosticRetransmitTimer(long expectedAt, long actualAt) {
        if (diagnostics != null) diagnostics.timer(this, TcpConnectionDiagnostics.TIMER_RETRANSMIT, expectedAt, actualAt);
    }

    public void diagnosticPersistTimer(long expectedAt, long actualAt) {
        if (diagnostics != null) diagnostics.timer(this, TcpConnectionDiagnostics.TIMER_PERSIST, expectedAt, actualAt);
    }

    public void diagnosticDelayedAckTimer(long expectedAt, long actualAt) {
        if (diagnostics != null) diagnostics.timer(this, TcpConnectionDiagnostics.TIMER_DELAYED_ACK, expectedAt, actualAt);
    }

    public void diagnosticTimeWaitTimer(long expectedAt, long actualAt) {
        if (diagnostics != null) diagnostics.timer(this, TcpConnectionDiagnostics.TIMER_TIME_WAIT, expectedAt, actualAt);
    }

    public int getPshMultiplier(int dataSize) {
        if (dataSize > 1024) {
            return 1;
        }
        if (dataSize > 512) {
            return Math.max(1, pshMultiplier - 1);
        }
        return pshMultiplier;
    }

    public void setPshMultiplier(int pshMultiplier) {
        this.pshMultiplier = pshMultiplier;
    }

    public int getAckMultiplier() {
        return ackMultiplier;
    }

    public void setAckMultiplier(int ackMultiplier) {
        this.ackMultiplier = ackMultiplier;
    }

    public boolean requireClosing() {
        return needClosing;
    }

    public boolean isRemoteSackPermitted() {
        return remoteSackPermitted;
    }

    public void setRemoteSackPermitted(boolean remoteSackPermitted) {
        this.remoteSackPermitted = remoteSackPermitted;
    }


    public void doClose() {
        this.needClosing = true;
    }


    public class SendingQueue {
        private static final double CUBIC_C;
        private static final double CUBIC_BETA;
        private static final double RTT_ALPHA;
        private static final double RTT_BETA;

        static {
            // more aggressive growth for high-latency
            var cubicC = Utils.getSystemProperty("cubic_c", "1");
            // 高延迟网络，减少恢复时间
            var cubicBeta = Utils.getSystemProperty("cubic_beta", "0.8");
            var rttAlpha = Utils.getSystemProperty("rtt_alpha", "0.25");
            // higher weight for high-latency variance
            var rttBeta = Utils.getSystemProperty("rtt_beta", "0.2");

            CUBIC_C = Double.parseDouble(cubicC);
            CUBIC_BETA = Double.parseDouble(cubicBeta);
            RTT_ALPHA = Double.parseDouble(rttAlpha);
            RTT_BETA = Double.parseDouble(rttBeta);
        }

        private final ArrayDeque<Segment> q = new ArrayDeque<>();
        // References to q's not-fully-sent segments. ACK/SACK/retransmission
        // keep using q; ordinary sends avoid walking its sent prefix again.
        private final ArrayDeque<Segment> unsent = new ArrayDeque<>(8);
        private int currentSize = 0;
        private long latestSeq;
        private long ackSeq;
        private long fetchSeq;
        private int window = 0;
        private int mss = 0;
        private int windowScale = 1;
        private long peerWindowRightEdge;
        private long zeroWindowProbeSeq = -1;
        private boolean finSentOnWire;
        private boolean finAcked = false;

        // congestion control state
        private int cwnd;
        private int ssthresh;
        private long lastLossTime;
        private int lastLossCwnd; // cwnd (in bytes) at the time of last loss
        private int bytesInFlight = 0;
        private int dupAckCount = 0;

        // RTT estimation (RFC 6298)
        private long srttUs = -1;   // smoothed RTT in microseconds (-1 = no sample yet)
        private long rttVarUs = -1; // RTT variance in microseconds
        private long rto = RTO_MIN; // computed RTO in ms

        public SendingQueue(int seq) {
            this.latestSeq = seq;
            this.ackSeq = seq;
            this.fetchSeq = seq;
        }

        public void init(int window, int mss, int windowScale) {
            init(window, mss, windowScale, INIT_CWND);
        }

        public void init(int window, int mss, int windowScale, int initialCwnd) {
            this.windowScale = Math.max(1, windowScale);
            this.window = scaledWindow(window);
            this.mss = mss;
            this.peerWindowRightEdge = ackSeq + this.window;
            this.cwnd = initialCwnd;
            this.ssthresh = Integer.MAX_VALUE;
            this.lastLossTime = Config.currentTimestamp;
            this.lastLossCwnd = cwnd;
        }

        public void setCwnd(int cwnd) {
            this.cwnd = cwnd;
        }

        private int scaledWindow(int window) {
            long scaled = (long) window * windowScale;
            return (int) Math.max(0, Math.min(MAX_REMOTE_WINDOW, scaled));
        }

        private void updateWindow(long ack, int window) {
            // A stale or impossible ACK must not move the receive-window edge backwards
            // or open it beyond data the peer could have acknowledged.
            if (ack < ackSeq || ack > maxAckableSequence()) {
                return;
            }
            this.window = scaledWindow(window);
            this.peerWindowRightEdge = ack + this.window;
        }

        public boolean isAckAcceptable(long seq) {
            return seq >= ackSeq && seq <= maxAckableSequence();
        }

        private long maxAckableSequence() {
            long max = fetchSeq;
            if (zeroWindowProbeSeq == fetchSeq) {
                ++max;
            }
            if (finSentOnWire && state.finSent && fetchSeq == latestSeq) {
                ++max;
            }
            return max;
        }

        public void incAllSeq() {
            this.latestSeq += 1;
            this.ackSeq += 1;
            this.fetchSeq += 1;
            this.peerWindowRightEdge += 1;
        }

        public void decAllSeq() {
            this.latestSeq -= 1;
            this.ackSeq -= 1;
            this.fetchSeq -= 1;
            this.peerWindowRightEdge -= 1;
        }

        public boolean hasMoreSpace() {
            return currentSize < WMEM_MAX;
        }

        public boolean hasMoreData() {
            return !q.isEmpty();
        }

        public int apiWrite(ByteBuffer buffer) {
            if (state.finSent) {
                Logger.error(LogType.IMPROPER_USE, "FIN is set but still writing data");
                return 0;
            }

            int total = 0;
            while (true) {
                int ret = push0(buffer);
                if (ret == 0) {
                    break;
                }
                total += ret;
            }
            return total;
        }

        private int push0(ByteBuffer buffer) {
            int len = buffer.limit() - buffer.position();
            if (len > mss) {
                len = mss;
            }
            if (currentSize + len > WMEM_MAX) {
                len = WMEM_MAX - currentSize;
            }
            if (len <= 0) {
                return 0;
            }
            byte[] bytes = Utils.allocateByteArray(len);
            buffer.get(bytes);
            var data = ByteArray.from(bytes);

            Segment segment = new Segment(latestSeq, data);
            q.add(segment);
            unsent.add(segment);
            latestSeq += data.length();
            currentSize += data.length();
            return len;
        }

        public List<Segment> fetch() {
            return fetch(false);
        }

        private final List<Segment> REUSEABLE_BUFFER_FOR_fetch = new ArrayList<>(8);

        public List<Segment> fetch(boolean isRetransmit) {
            int sendLimit = getAvailableSendWindow();
            if (sendLimit <= 0 && !isRetransmit) {
                return Collections.emptyList();
            }

            List<Segment> ret = REUSEABLE_BUFFER_FOR_fetch;
            ret.clear();
            long oldFetchSeq = fetchSeq;
            long newFetchEnd = fetchSeq;

            // The common single-segment case needs no iterator allocation.
            // Keep cursor removal after the complete fetch has succeeded.
            Segment single = !isRetransmit && unsent.size() == 1 ? unsent.peekFirst() : null;
            Iterator<Segment> iterator = single != null ? null : (isRetransmit ? q : unsent).iterator();
            while (single != null || (iterator != null && iterator.hasNext())) {
                Segment s = single != null ? single : iterator.next();
                single = null;
                if (s.sacked) {
                    continue;
                }
                if (isRetransmit) {
                    // Retransmit only sequence space that has actually been sent. A
                    // window-limited partial segment may still have an unsent suffix.
                    long from = Math.max(s.seqBeginInclusive, ackSeq);
                    long to = Math.min(s.seqEndExclusive, oldFetchSeq);
                    if (from >= to) {
                        continue;
                    }
                    s.retransmitted++;
                    ret.add(segmentSlice(s, from, to));
                    continue;
                }

                if (s.seqEndExclusive <= newFetchEnd) {
                    continue;
                }
                if (s.seqBeginInclusive > newFetchEnd) {
                    break;
                }
                int remaining = sendLimit - (int) (newFetchEnd - oldFetchSeq);
                if (remaining <= 0) {
                    break;
                }
                long from = Math.max(s.seqBeginInclusive, newFetchEnd);
                long to = Math.min(s.seqEndExclusive, from + remaining);
                if (from >= to) {
                    break;
                }
                Segment sending = segmentSlice(s, from, to);
                ret.add(sending);
                newFetchEnd = to;
                if (s.firstSentTime == 0) {
                    long now = Config.currentTimestamp;
                    s.firstSentTime = now;
                    s.lastSendTime = now;
                }
            }

            if (!ret.isEmpty()) {
                int newBytes = (int) (newFetchEnd - oldFetchSeq);
                bytesInFlight += newBytes;
                fetchSeq = newFetchEnd;
                if (!isRetransmit) discardSentPrefix();
            }

            return ret;
        }

        private void discardSentPrefix() {
            while (!unsent.isEmpty() && unsent.peekFirst().seqEndExclusive <= fetchSeq) {
                unsent.pollFirst();
            }
        }

        /**
         * Returns one unsent byte as a TCP persist probe while the peer has
         * advertised a zero window. It deliberately does not advance SND.NXT
         * or consume congestion-window accounting; ordinary fetch() remains
         * blocked until a window update arrives. If only FIN remains, use an
         * empty probe at SND.NXT - 1 to recover a lost window update.
         */
        public Segment fetchZeroWindowProbe() {
            if (window != 0) {
                return null;
            }
            if (fetchSeq >= latestSeq) {
                // FIN also needs a window slot. An empty probe just before
                // SND.NXT solicits a window update without sending FIN early.
                return needToSendFin() ? new Segment(fetchSeq - 1, ByteArray.allocate(0)) : null;
            }
            for (Segment segment : q) {
                if (segment.seqBeginInclusive <= fetchSeq && fetchSeq < segment.seqEndExclusive) {
                    zeroWindowProbeSeq = fetchSeq;
                    return segmentSlice(segment, fetchSeq, fetchSeq + 1);
                }
            }
            return null;
        }

        public boolean hasUnsentData() {
            return fetchSeq < latestSeq;
        }

        public void markFinSent() {
            finSentOnWire = true;
        }

        private Segment segmentSlice(Segment source, long begin, long end) {
            if (begin == source.seqBeginInclusive && end == source.seqEndExclusive) {
                return source;
            }
            return new Segment(begin, source.data.sub((int) (begin - source.seqBeginInclusive), (int) (end - begin)));
        }

        /**
         * Returns how many bytes may be sent right now, considering both
         * the peer's receive window and our congestion window.
         */
        public int getAvailableSendWindow() {
            int congestionAvailable = Math.max(0, cwnd - bytesInFlight);
            long peerWindowAvailable = peerWindowRightEdge - fetchSeq;
            if (peerWindowAvailable <= 0) {
                return 0;
            }
            return (int) Math.min(congestionAvailable, Math.min(Integer.MAX_VALUE, peerWindowAvailable));
        }

        /**
         * @param sackBlocks list of [start, end) pairs representing received data ranges
         */
        public void markSackRetransmitSegments(List<SAckTuple> sackBlocks) {
            if (q.isEmpty()) return;
            if (sackBlocks.isEmpty()) return;

            var sackIte = sackBlocks.iterator();
            var currSAckBlock = sackIte.hasNext() ? sackIte.next() : null;
            var qite = q.iterator();
            var moveQIteNext = true;
            Segment s = null;
            while (qite.hasNext() || !moveQIteNext) {
                if (moveQIteNext) {
                    s = qite.next();
                } else {
                    moveQIteNext = true;
                }
                assert s != null;

                // 已经sack的不需要处理
                if (s.sacked) continue;
                // 比fetchSeq大的segments不需要处理
                if (s.seqBeginInclusive >= fetchSeq) {
                    break;
                }
                // 已经没有sack要处理了？
                if (currSAckBlock == null) {
                    break;
                }
                // 太小的？
                if (s.seqEndExclusive <= currSAckBlock.seqBeginInclusive) {
                    continue;
                }
                // 完全位于sack内？
                if (s.seqBeginInclusive >= currSAckBlock.seqBeginInclusive && s.seqEndExclusive <= currSAckBlock.seqEndExclusive) {
                    s.sacked = true;
                    continue;
                }
                // 部分包含的（开头在sack里）？
                if (s.seqBeginInclusive >= currSAckBlock.seqBeginInclusive && s.seqBeginInclusive < currSAckBlock.seqEndExclusive) {
                    continue;
                }
                // 部分包含的（结尾在sack里的）？
                if (s.seqEndExclusive <= currSAckBlock.seqEndExclusive) {
                    continue;
                }
                // 当前segment超出sack的
                // roll forward
                currSAckBlock = sackIte.hasNext() ? sackIte.next() : null;
                moveQIteNext = false; // 当前segment还要再和新的sack进行对比处理
            }
        }

        public void ack(long seq, int window) {
            if (finAcked) { // nothing to do because the output is completely shutdown
                return;
            }

            if (!isAckAcceptable(seq)) {
                return;
            }

            updateWindow(seq, window);

            if (finSentOnWire && state.finSent && seq >= latestSeq + 1) {
                ackSeq = latestSeq + 1;
                fetchSeq = latestSeq + 1;
                finAcked = true;
                bytesInFlight = 0;
                q.clear();
                unsent.clear();
                currentSize = 0;
                return;
            }
            boolean advancedByProbe = zeroWindowProbeSeq == fetchSeq && seq > fetchSeq;
            if (advancedByProbe) {
                fetchSeq = zeroWindowProbeSeq + 1;
                zeroWindowProbeSeq = -1;
            } else if (window > 0) {
                zeroWindowProbeSeq = -1;
            }

            // compute newly acked bytes for congestion control
            long newlyAcked = seq - ackSeq;
            if (newlyAcked > 0) {
                bytesInFlight = Math.max(0, bytesInFlight - (int) newlyAcked);
                // CUBIC update: grow cwnd for each newly acked segment
                cubicOnAck((int) newlyAcked);
            }

            if (ackSeq < seq) {
                ackSeq = seq;
            }
            boolean sampled = false;
            boolean becameWritable = false;
            while (!q.isEmpty()) {
                var s = q.peekFirst();
                if (s.seqBeginInclusive >= seq) {
                    break; // entirely after ACK, nothing to do
                }
                if (s.seqEndExclusive <= seq) {
                    // fully acked — remove
                    if (!sampled) {
                        sampleRtt(s);
                        sampled = true;
                    }
                    currentSize -= s.data.length();
                    q.pollFirst();
                    if (unsent.peekFirst() == s) unsent.pollFirst();
                    becameWritable = true;
                } else {
                    // Trim a partially acknowledged head so its consumed prefix no
                    // longer occupies the send buffer or appears in a retransmission.
                    int acked = (int) (seq - s.seqBeginInclusive);
                    if (acked > 0) {
                        if (!sampled) {
                            sampleRtt(s);
                            sampled = true;
                        }
                        q.pollFirst();
                        currentSize -= acked;
                        Segment remaining = new Segment(seq, s.data.sub(acked, s.data.length() - acked));
                        if (seq < fetchSeq) {
                            remaining.firstSentTime = s.firstSentTime;
                            remaining.lastSendTime = s.lastSendTime;
                            remaining.retransmitted = s.retransmitted;
                            remaining.sacked = s.sacked;
                        }
                        q.addFirst(remaining);
                        // A partial segment can have both sent and unsent
                        // bytes. Keep the cursor on q's replacement object.
                        if (unsent.peekFirst() == s) {
                            unsent.pollFirst();
                            unsent.addFirst(remaining);
                        }
                        becameWritable = true;
                    }
                    break;
                }
            }
            // Acknowledging a zero-window probe can advance fetchSeq without
            // an ordinary fetch. Update the cursor before writable reentry.
            if (advancedByProbe) discardSentPrefix();
            if (becameWritable && connectionHandler != null) {
                connectionHandler.writable(TcpEntry.this);
            }
        }

        private void sampleRtt(Segment s) {
            if (s.lastSendTime == 0) {
                return;
            }
            // Karn's algorithm: RTT samples from retransmitted segments are ambiguous, skip them
            if (s.retransmitted != 0) {
                return;
            }
            long now = Config.currentTimestamp;
            long rttMs = now - s.lastSendTime;
            if (rttMs <= 0) {
                return;
            }
            long rttUs = rttMs * 1000L;
            if (srttUs < 0) {
                // first sample (RFC 6298)
                srttUs = rttUs;
                rttVarUs = rttUs / 2;
            } else {
                // update SRTT and RTTVAR - 使用更高的方差权重适应高延迟波动
                long diff = srttUs - rttUs;
                if (diff < 0) diff = -diff;
                rttVarUs = (long) ((1 - RTT_BETA) * rttVarUs + RTT_BETA * diff);
                srttUs = (long) ((1 - RTT_ALPHA) * srttUs + RTT_ALPHA * rttUs);
            }
            rto = Math.max(RTO_MIN, Math.min(RTO_MAX, (srttUs + 4 * rttVarUs) / 1000));
        }

        private void cubicOnAck(int newlyAcked) {
            // number of MSS-sized increments to apply (per RFC 5681)
            int mssCount = Math.max(1, newlyAcked / mss);
            if (cwnd < ssthresh) {
                // slow start: increase by newlyAcked bytes (exponential)
                cwnd += newlyAcked;
                if (cwnd > ssthresh) {
                    cwnd = ssthresh;
                }
            } else {
                // CUBIC congestion avoidance
                double elapsedSec = (Config.currentTimestamp - lastLossTime) / 1000.0;
                double wMaxMss = (double) lastLossCwnd / mss; // W_max in MSS units
                double k = Math.cbrt(wMaxMss * (1 - CUBIC_BETA) / CUBIC_C);
                double tMinusK = elapsedSec - k;
                double wCubic = CUBIC_C * tMinusK * tMinusK * tMinusK + wMaxMss;
                int targetCwnd = (int) (wCubic * mss);

                // ensure target is at least 2*MSS
                targetCwnd = Math.max(targetCwnd, 2 * mss);

                // ACK-clocking increment (standard TCP rate: MSS^2 / cwnd per ACK)
                // apply once per MSS acked, so large ACKs grow cwnd proportionally
                for (int i = 0; i < mssCount; i++) {
                    int increment = Math.max(1, (int) ((long) mss * mss / cwnd));
                    if (cwnd < targetCwnd) {
                        // cwnd below CUBIC target — grow toward it
                        cwnd += increment;
                        // don't overshoot the target
                        if (cwnd > targetCwnd) {
                            cwnd = targetCwnd;
                        }
                    } else {
                        // cwnd at or above CUBIC target — grow at standard TCP rate
                        // (TCP-friendly region per RFC 8312 Section 4.2)
                        cwnd += increment;
                    }
                }
            }
            // cap cwnd to MAX_CWND
            cwnd = Math.min(cwnd, MAX_CWND);
        }

        /**
         * Called on RTO (retransmission timeout) to signal congestion.
         * 高延迟网络：更温和的丢包恢复，保留更多带宽
         */
        public void onLoss() {
            // save cwnd as W_max for CUBIC recovery if it's higher than the previous peak
            if (cwnd > lastLossCwnd) {
                lastLossCwnd = cwnd;
            }
            // use CUBIC_BETA consistently — this matches the K calculation
            // in cubicOnAck which also uses CUBIC_BETA
            int newCwnd = (int) (cwnd * CUBIC_BETA);
            ssthresh = Math.max(newCwnd, HIGH_LATENCY_MIN_CWND_MSS * mss);
            cwnd = ssthresh;
            lastLossTime = Config.currentTimestamp;
        }

        public int getCurrentSize() {
            return currentSize;
        }

        public int getBytesInFlight() {
            return bytesInFlight;
        }

        public long getLatestSeq() {
            return latestSeq;
        }

        public long getAckSeq() {
            return ackSeq;
        }

        public long getFetchSeq() {
            return fetchSeq;
        }

        public int getWindow() {
            return window;
        }

        public int getMss() {
            return mss;
        }

        public int getWindowScale() {
            return windowScale;
        }

        public long getRto() {
            return rto;
        }

        public int getCwnd() {
            if (cwnd > 0xffffff) {
                return 0xffffff;
            }
            return cwnd;
        }


        public boolean needToSendFin() {
            return state.finSent && !finAcked;
        }

        public boolean ackOfFinReceived() {
            return finAcked;
        }

        public int incrementDupAckCount() {
            return ++dupAckCount;
        }

        public void resetDupAckCount() {
            dupAckCount = 0;
        }

        /**
         * 检查最早未ACK的分片是否已超过重传超时时间。
         * @return 超时的分片，如果没有超时返回 null
         */
        public Segment checkRetransmissionTimeout() {
            if (q.isEmpty()) return null;
            long now = Config.currentTimestamp;
            for (var s : q) {
                if (s.firstSentTime == 0) {
                    // 队列是有序的，所以遇到没有发送时间的分片就可以直接退出循环了
                    return null;
                }
                if (s.sacked) {
                    continue;
                }
                if (s.firstSentTime > 0 && now - s.firstSentTime >= RETRANSMISSION_TIMEOUT_MS) {
                    return s;
                }
                break;
            }
            return null;
        }
    }

    private static final class CopiedReceiveSegment extends Segment {
        private CopiedReceiveSegment(long seq, ByteArray data) {
            super(seq, data);
        }
    }

    public class ReceivingQueue {
        private final ArrayDeque<Segment> q = new ArrayDeque<>();
        private final TreeMap<Long, Segment> oooBuffer = new TreeMap<>();
        private int currentSize = 0;
        private int oooSize = 0;
        private long expectingSeq; // receive point: everything before this is held contiguously (RCV.NXT)
        private long readSeq; // app-read point: everything before this has been consumed via apiRead
        private long lastSentAckSeq; // ack number carried by the last outgoing ACK/data packet (quickack bookkeeping)
        private int window = RMEM_MAX;
        private int windowScale = 64;
        private boolean oooGapFilled = false;
        // Queue-owned head storage is stable. Query its backing only once per
        // head, rather than through several virtual calls on every small read.
        private long copiedHeadBacking = 0;
        // A private copied head can advance without allocating a SubByteArray
        // and Segment per read. Legacy shared input keeps offset zero.
        private int copiedHeadOffset = 0;

        public ReceivingQueue(long seq) {
            this.expectingSeq = seq;
            this.readSeq = seq;
            this.lastSentAckSeq = seq;
        }

        public void incExpectingSeq() {
            assert readSeq == expectingSeq;
            expectingSeq += 1;
            readSeq += 1;
        }

        public void setInitialSeq(long seq) {
            if (this.expectingSeq == 0 && this.readSeq == 0) {
                this.expectingSeq = seq;
                this.readSeq = seq;
            } else {
                Logger.error(LogType.IMPROPER_USE, "calling setInitialSeq while expectingSeq(" + expectingSeq + ") or readSeq(" + readSeq + ") is not 0");
            }
        }

        public boolean hasMoreDataToRead() {
            return !q.isEmpty();
        }

        /**
         * Copy a packet payload into queue-owned storage. The caller may reuse
         * its input immediately. Unlike store(Segment), this opt-in path may
         * compact unread slices. When all input uses this entry point, queued
         * backing is bounded by four times the queued logical bytes (excluding
         * views already returned to users). Mixing legacy shared input disables
         * compaction of any segment that includes that shared input.
         * Returned ByteArray views remain valid over their visible range, but
         * do not promise shared backing with a subsequently compacted tail.
         */
        public void storeCopied(long seq, ByteArray data) {
            store(new CopiedReceiveSegment(seq, data.copy()));
        }

        public void store(Segment segment) {
            if (state.remoteClosed) {
                Logger.error(LogType.IMPROPER_USE, "FIN received but is still storing data");
                return;
            }
            int payloadLength = segment.data.length();
            // Empty payloads are not data or SACK ranges. In particular, do not
            // allow arbitrary future sequence numbers to fill the OOO map
            // without consuming any of the logical receive-memory budget.
            if (payloadLength == 0) {
                return;
            }
            int available = RMEM_MAX - currentSize - oooSize;
            if (available <= 0) {
                // memory is full
                return;
            }
            long segBegin = segment.seqBeginInclusive;
            // entirely before expectingSeq - already received
            if (segment.seqEndExclusive <= expectingSeq) {
                return;
            }
            ByteArray data = segment.data;
            if (segBegin < expectingSeq) {
                // Trim duplicates before applying the free-byte limit. Otherwise
                // a duplicate prefix can consume that limit and lose a new tail.
                int trim = (int) (expectingSeq - segBegin);
                payloadLength -= trim;
                data = data.sub(trim, payloadLength);
                segBegin = expectingSeq;
            }
            // OOO payload occupies sequence space as well as physical memory.
            // Keep it within readSeq + RMEM_MAX so holes always have space to
            // be filled; a full OOO map must not close the window on its gap.
            long offset = segBegin - readSeq;
            if (offset < 0 || offset >= RMEM_MAX) {
                return;
            }
            int limit = (int) Math.min(available, RMEM_MAX - offset);
            if (payloadLength > limit) {
                // The unaccepted suffix will be retransmitted.
                byte[] prefix = Utils.allocateByteArray(limit);
                data.byteBufferPut(ByteBuffer.wrap(prefix), 0, limit);
                data = ByteArray.from(prefix);
            }

            // starts at or before expectingSeq - can add to ready queue directly
            if (segBegin <= expectingSeq) {
                q.add(receiveSegment(expectingSeq, data,
                    segment instanceof CopiedReceiveSegment, segment));
                expectingSeq += data.length();
                currentSize += data.length();

                // drain any OOO segments that are now contiguous
                boolean hadOOO = !oooBuffer.isEmpty();
                drainOOOBuffer();
                if (hadOOO && oooBuffer.isEmpty()) {
                    oooGapFilled = true;
                }

                resetWindow();

                // run callback
                if (connectionHandler != null) {
                    connectionHandler.readable(TcpEntry.this);
                }
                return;
            }

            // out of order (segBegin > expectingSeq) - buffer for later
            bufferOOOSegment(segment, segBegin, data);
        }

        private Segment receiveSegment(long seq, ByteArray data, boolean copied, Segment original) {
            if (!copied) {
                return new Segment(seq, data);
            }
            // Check only views/composites. A private SimpleByteArray already
            // has backing == length. Quarter-size compaction bounds retained
            // payload and amortizes repeated partial reads geometrically.
            if (!(data instanceof SimpleByteArray) && data.retainedBackingSize() > data.length() * 4L) {
                data = data.copy();
            }
            if (original.seqBeginInclusive == seq && original.data == data) {
                return original;
            }
            return new CopiedReceiveSegment(seq, data);
        }

        private void bufferOOOSegment(Segment segment, long begin, ByteArray data) {
            long end = begin + data.length();
            boolean copied = segment instanceof CopiedReceiveSegment;

            // check for overlap with segment that starts before our begin
            Map.Entry<Long, Segment> floor = oooBuffer.floorEntry(begin);
            if (floor != null) {
                Segment fs = floor.getValue();
                if (fs.seqEndExclusive >= end) {
                    // new segment is entirely within an existing one
                    return;
                }
                if (fs.seqEndExclusive > begin) {
                    // partial overlap - extend from the left
                    oooSize -= fs.data.length();
                    oooBuffer.remove(floor.getKey());

                    int overlapFromNew = (int) (fs.seqEndExclusive - begin);
                    ByteArray tailFromNew = data.sub(overlapFromNew, data.length() - overlapFromNew);
                    data = fs.data.concat(tailFromNew);
                    copied &= fs instanceof CopiedReceiveSegment;
                    begin = fs.seqBeginInclusive;
                }
            }

            // check and merge overlapping segments on the right
            while (true) {
                Map.Entry<Long, Segment> ceiling = oooBuffer.ceilingEntry(begin);
                if (ceiling == null || ceiling.getValue().seqBeginInclusive >= end) {
                    break;
                }
                Segment cs = ceiling.getValue();
                oooSize -= cs.data.length();
                oooBuffer.remove(ceiling.getKey());

                if (cs.seqEndExclusive > end) {
                    // extends beyond our current end, append its tail
                    int overlapInExisting = (int) (end - cs.seqBeginInclusive);
                    ByteArray tailFromExisting = cs.data.sub(overlapInExisting, cs.data.length() - overlapInExisting);
                    data = data.concat(tailFromExisting);
                    copied &= cs instanceof CopiedReceiveSegment;
                    end = cs.seqEndExclusive;
                }
                // if cs.seqEndExclusive <= end, it's fully contained, already removed
            }

            Segment merged = receiveSegment(begin, data, copied, segment);
            oooBuffer.put(begin, merged);
            oooSize += data.length();
            resetWindow();
        }

        private void drainOOOBuffer() {
            while (!oooBuffer.isEmpty()) {
                Map.Entry<Long, Segment> first = oooBuffer.firstEntry();
                Segment s = first.getValue();
                if (s.seqBeginInclusive > expectingSeq) {
                    break; // gap still exists
                }
                oooBuffer.remove(first.getKey());
                oooSize -= s.data.length();

                if (s.seqEndExclusive <= expectingSeq) {
                    // fully covered by already-received data
                    continue;
                }

                // partial or full overlap
                ByteArray data = s.data;
                if (s.seqBeginInclusive < expectingSeq) {
                    int trim = (int) (expectingSeq - s.seqBeginInclusive);
                    data = data.sub(trim, data.length() - trim);
                }
                q.add(receiveSegment(expectingSeq, data,
                    s instanceof CopiedReceiveSegment, s));
                expectingSeq += data.length();
                currentSize += data.length();
            }
        }

        public ByteArray apiRead(int maxLen) {
            int len = Math.min(currentSize, maxLen);
            if (len <= 0 || q.isEmpty()) {
                resetWindow();
                return null;
            }
            Segment first = q.peekFirst();
            if (first.data.length() - copiedHeadOffset >= len) {
                ByteArray result = first.data.sub(copiedHeadOffset, len);
                consumeReadBytes(len);
                resetWindow();
                return result;
            }

            // Coalesce once when a read crosses multiple TCP segments. This keeps
            // the application-facing ByteArray shallow and releases old backing
            // segments as soon as they are consumed.
            byte[] bytes = Utils.allocateByteArray(len);
            int written = 0;
            while (written < len) {
                Segment segment = q.peekFirst();
                int n = Math.min(len - written, segment.data.length() - copiedHeadOffset);
                segment.data.sub(copiedHeadOffset, n).toNewJavaArray(bytes, written);
                consumeReadBytes(n);
                written += n;
            }
            resetWindow();
            return ByteArray.from(bytes);
        }

        /**
         * Drain directly into an application buffer, avoiding an intermediate
         * flattened byte[] for Composite/SubByteArray payloads.
         */
        public int apiRead(ByteBuffer dst) {
            if (dst.isReadOnly()) {
                throw new java.nio.ReadOnlyBufferException();
            }
            int len = Math.min(currentSize, dst.remaining());
            if (len <= 0 || q.isEmpty()) {
                resetWindow();
                return 0;
            }
            int remaining = len;
            while (remaining > 0) {
                Segment segment = q.peekFirst();
                int n = Math.min(remaining, segment.data.length() - copiedHeadOffset);
                segment.data.byteBufferPut(dst, copiedHeadOffset, n);
                consumeReadBytes(n);
                remaining -= n;
            }
            resetWindow();
            return len;
        }

        private void consumeReadBytes(int len) {
            while (len > 0) {
                Segment segment = q.peekFirst();
                int segmentLen = segment.data.length() - copiedHeadOffset;
                int consumed = Math.min(len, segmentLen);
                if (consumed == segmentLen) {
                    q.pollFirst();
                    currentSize -= consumed;
                    readSeq = segment.seqEndExclusive;
                    copiedHeadBacking = 0;
                    copiedHeadOffset = 0;
                } else if (segment instanceof CopiedReceiveSegment) {
                    int offset = copiedHeadOffset + consumed;
                    int remainingLen = segmentLen - consumed;
                    long backing = copiedHeadBacking == 0 ? segment.data.retainedBackingSize() : copiedHeadBacking;
                    if (backing > remainingLen * 4L) {
                        ByteArray data = segment.data.sub(offset, remainingLen).copy();
                        var remaining = new CopiedReceiveSegment(segment.seqBeginInclusive + offset, data);
                        // Commit only after the compacted payload/metadata exists.
                        q.pollFirst();
                        q.addFirst(remaining);
                        copiedHeadOffset = 0;
                        copiedHeadBacking = remainingLen;
                    } else {
                        copiedHeadOffset = offset;
                        copiedHeadBacking = backing;
                    }
                    currentSize -= consumed;
                    readSeq = segment.seqBeginInclusive + offset;
                } else {
                    int remainingLen = segmentLen - consumed;
                    ByteArray data = segment.data.sub(consumed, remainingLen);
                    var remaining = new Segment(segment.seqBeginInclusive + consumed, data);
                    // Commit after the remaining payload/metadata is built.
                    q.pollFirst();
                    currentSize -= consumed;
                    q.addFirst(remaining);
                    readSeq = remaining.seqBeginInclusive;
                    copiedHeadBacking = 0;
                }
                len -= consumed;
            }
        }

        public int getCurrentSize() {
            return currentSize;
        }

        public long getExpectingSeq() {
            return expectingSeq;
        }

        public long getReadSeq() {
            return readSeq;
        }

        public long getLastSentAckSeq() {
            return lastSentAckSeq;
        }

        public void setLastSentAckSeq(long lastSentAckSeq) {
            this.lastSentAckSeq = lastSentAckSeq;
        }

        public int getWindow() {
            return window;
        }

        public void resetWindow() {
            this.window = RMEM_MAX - currentSize - oooSize;
        }

        public int getWindowScale() {
            return windowScale;
        }

        /**
         * Quick O(1) check for whether out-of-order data is buffered.
         * Used by the ACK path to decide whether to send SACK immediately.
         */
        public boolean hasOutOfOrderData() {
            return !oooBuffer.isEmpty();
        }

        /**
         * Returns SACK blocks representing the out-of-order segments currently buffered.
         * Each block is a [begin, end) pair describing contiguous received data beyond
         * expectingSeq (the ACK point carried by outgoing packets, so blocks never
         * overlap what the cumulative ACK already covers).
         */
        public List<SAckTuple> getSAckBlocks() {
            if (oooBuffer.isEmpty()) {
                return Collections.emptyList();
            }
            var blocks = new ArrayList<SAckTuple>();
            for (var entry : oooBuffer.values()) {
                long begin = Math.max(entry.seqBeginInclusive, expectingSeq);
                if (begin < entry.seqEndExclusive) {
                    blocks.add(new SAckTuple(begin, entry.seqEndExclusive));
                }
            }
            return blocks;
        }

        public boolean consumeOooGapFilled() {
            var v = oooGapFilled;
            oooGapFilled = false;
            return v;
        }
    }

    private Map<Object, Object> userdata;

    @Override
    public Object getUserData(Object key) {
        if (userdata == null) {
            return null;
        }
        return userdata.get(key);
    }

    @Override
    public Object putUserData(Object key, Object value) {
        if (userdata == null) {
            userdata = new HashMap<>();
        }
        return userdata.put(key, value);
    }

    @Override
    public Object removeUserData(Object key) {
        if (userdata == null) {
            return null;
        }
        return userdata.remove(key);
    }

    @Override
    public String toString() {
        return "TcpEntry{" +
               "parent=" + parent +
               ", remote=" + remote +
               ", local=" + local +
               ", state=" + state +
               ", nat=" + nat +
               '}';
    }

    public String description() {
        return (parent == null ? "null" : parent.description()) + ",remote=" + remote + ",local=" + local;
    }
}
