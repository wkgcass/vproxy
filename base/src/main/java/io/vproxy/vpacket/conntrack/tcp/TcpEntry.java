package io.vproxy.vpacket.conntrack.tcp;

import io.vproxy.base.selector.TimerEvent;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.misc.WithUserData;
import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.conntrack.tcp.cwnd.CwndNegotiator;

import java.nio.ByteBuffer;
import java.util.*;

public class TcpEntry implements WithUserData {
    public static final int WMEM_MAX = 1048576; // 1MB, for high-latency BDP
    public static final int RMEM_MAX = 1048576; // 1MB, for high-latency BDP
    public static final int SND_MAX_MSS = 1400; // xdp frame size = 2048, hardware reserved = 256, headroom = 128, mac+ip+tcp_hdr=14+40+20
    public static final int RCV_MSS = 1400;
    public static final int TCP_SEQ_INIT_MIN = Integer.MAX_VALUE / 3;
    public static final int TCP_SEQ_RAND = Integer.MAX_VALUE / 2;
    public static final int RTO_MIN = 100;
    public static final int RTO_MAX = 2_000;
    public static final int DELAYED_ACK_TIMEOUT = 20; // balanced for high-latency
    public static final int DELAYED_ACK_TIMEOUT_FOR_SACK = 20; // balanced for high-latency
    public static final int MAX_REMOTE_WINDOW = 16 * 1024 * 1024; // 16MB, safety cap for peer's advertised window
    public static final int MAX_CWND = 20 * 1024 * 1024; // 20MB max cwnd
    public static final int INIT_CWND = MAX_CWND / 10;
    public static final int MAX_RETRANSMISSION_AFTER_CLOSING = 14;
    public static final int TIME_WAIT_TIMEOUT_MS = 24_000;

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
    private CwndNegotiator cwndNegotiator = CwndNegotiator.createDefault();

    public final SendingQueue sendingQueue;
    public final ReceivingQueue receivingQueue;

    public TimerEvent retransmissionTimer = null;
    public TimerEvent delayedAckTimer = null;

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
    }

    public TcpEntry(IPPort remote, IPPort local) {
        this.remote = remote;
        this.local = local;
        this.sendingQueue = null;
        this.receivingQueue = null;
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
        state = TcpState.CLOSED;
        if (retransmissionTimer != null) {
            retransmissionTimer.cancel();
        }
        if (delayedAckTimer != null) {
            delayedAckTimer.cancel();
        }
        if (connectionHandler != null) {
            connectionHandler.destroy(this);
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

    public CwndNegotiator getCwndNegotiator() {
        return cwndNegotiator;
    }

    public void setCwndNegotiator(CwndNegotiator cwndNegotiator) {
        this.cwndNegotiator = cwndNegotiator;
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
        private int currentSize = 0;
        private long latestSeq;
        private long ackSeq;
        private long fetchSeq;
        private int window = 0;
        private int mss = 0;
        private int windowScale = 1;
        private boolean finAcked = false;

        // congestion control state
        private int cwnd;
        private int ssthresh;
        private long lastLossTime;
        private int lastLossCwnd; // cwnd (in bytes) at the time of last loss
        private int bytesInFlight = 0;
        private int dupAckCount = 0;

        // cwnd negotiation state
        private int yourCwnd = 0;  // last received peer cwnd, 0 means not yet received
        private int minCwnd = 0;   // negotiated floor, 0 means not yet negotiated

        // RTT estimation (RFC 6298)
        private long srttUs = -1;   // smoothed RTT in microseconds (-1 = no sample yet)
        private long rttVarUs = -1; // RTT variance in microseconds
        private long rto = RTO_MIN; // computed RTO in ms

        // per-segment send timestamps for RTT sampling (seq -> sendTimeMs)
        private final java.util.Map<Long, Long> sendTimes = new java.util.HashMap<>();

        public SendingQueue(int seq) {
            this.latestSeq = seq;
            this.ackSeq = seq;
            this.fetchSeq = seq;
        }

        public void init(int window, int mss, int windowScale) {
            init(window, mss, windowScale, INIT_CWND);
        }

        public void init(int window, int mss, int windowScale, int initialCwnd) {
            this.window = Math.min(MAX_REMOTE_WINDOW, window * windowScale);
            this.mss = mss;
            this.windowScale = windowScale;
            this.cwnd = initialCwnd;
            this.ssthresh = Integer.MAX_VALUE;
            this.lastLossTime = System.currentTimeMillis();
            this.lastLossCwnd = cwnd;
        }

        public void setCwnd(int cwnd) {
            this.cwnd = cwnd;
        }

        private void updateWindow(int window) {
            this.window = Math.min(MAX_REMOTE_WINDOW, window * windowScale);
        }

        public void incAllSeq() {
            this.latestSeq += 1;
            this.ackSeq += 1;
            this.fetchSeq += 1;
        }

        public void decAllSeq() {
            this.latestSeq -= 1;
            this.ackSeq -= 1;
            this.fetchSeq -= 1;
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

            q.add(new Segment(latestSeq, data));
            latestSeq += data.length();
            currentSize += data.length();
            return len;
        }

        public List<Segment> fetch() {
            return fetch(false);
        }

        private final List<Segment> REUSEABLE_BUFFER_FOR_fetch = new ArrayList<>(1024);

        public List<Segment> fetch(boolean isRetransmit) {
            int sendLimit = getAvailableSendWindow();
            if (sendLimit <= 0 && !isRetransmit) {
                return Collections.emptyList();
            }

            long startSeq = isRetransmit ? ackSeq : fetchSeq;
            List<Segment> ret = REUSEABLE_BUFFER_FOR_fetch;
            ret.clear();
            int newBytes = 0; // bytes beyond fetchSeq → added to bytesInFlight

            for (var s : q) {
                // 跳过无需处理的
                if (s.seqEndExclusive <= startSeq) continue;
                // 已经sack的无需处理
                if (s.sacked) continue;

                // 新发送的？
                if (s.seqBeginInclusive > fetchSeq) {
                    // 超出sendLimit了？
                    if (s.seqEndExclusive - ackSeq > sendLimit) {
                        break;
                    }
                }

                // 如果比fetchSeq小，则说明它是重传的
                if (s.seqBeginInclusive < fetchSeq) {
                    s.retransmitted++;
                }

                ret.add(s);

                // Track new bytes for bytesInFlight
                if (s.seqEndExclusive > fetchSeq) {
                    newBytes += s.data.length(); // 每次必定只发送/ack/sack完整的segment，所以这里逻辑比较简单
                }
            }

            if (!ret.isEmpty()) {
                bytesInFlight += newBytes;
                long now = System.currentTimeMillis();
                for (var seg : ret) {
                    if (seg.retransmitted == 0) {
                        sendTimes.put(seg.seqBeginInclusive, now);
                    }
                    if (seg.seqEndExclusive > fetchSeq) {
                        fetchSeq = seg.seqEndExclusive;
                    }
                }
            }

            return ret;
        }

        /**
         * Returns how many bytes may be sent right now, considering both
         * the peer's receive window and our congestion window.
         */
        public int getAvailableSendWindow() {
            return Math.max(0, cwnd - bytesInFlight);
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

            updateWindow(window);

            if (state.finSent && seq >= latestSeq + 1) {
                ackSeq = latestSeq + 1;
                fetchSeq = latestSeq + 1;
                finAcked = true;
                bytesInFlight = 0;
                q.clear();
                return;
            }
            if (latestSeq < seq) {
                if (q.isEmpty()) {
                    return;
                }
                // is invalid, but we try our best to recover
                seq = q.peekLast().seqEndExclusive;
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
            var ite = q.iterator();
            boolean sampled = false;
            while (ite.hasNext()) {
                var s = ite.next();
                if (s.seqBeginInclusive >= seq) {
                    break; // entirely after ACK, nothing to do
                }
                if (s.seqEndExclusive <= seq) {
                    // fully acked — remove
                    if (!sampled) {
                        sampleRtt(s.seqBeginInclusive);
                        sampled = true;
                    }
                    sendTimes.remove(s.seqBeginInclusive);
                    currentSize -= s.data.length();
                    ite.remove();
                    if (connectionHandler != null) {
                        connectionHandler.writable(TcpEntry.this);
                    }
                } else {
                    // partially acked — segment is immutable, treat as not acked
                    break;
                }
            }
        }

        private void sampleRtt(long ackedSeq) {
            Long sendTime = sendTimes.get(ackedSeq);
            if (sendTime == null) {
                return;
            }
            long now = System.currentTimeMillis();
            long rttMs = now - sendTime;
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
                double elapsedSec = (System.currentTimeMillis() - lastLossTime) / 1000.0;
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
            // enforce minCwnd floor: never reduce below the negotiated minimum
            if (minCwnd > 0 && cwnd < minCwnd) {
                cwnd = minCwnd;
            }
            lastLossTime = System.currentTimeMillis();
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

        public int getYourCwnd() {
            return yourCwnd;
        }

        public void setYourCwnd(int yourCwnd) {
            this.yourCwnd = yourCwnd;
            // recalculate minCwnd when peer info changes
            if (mss > 0) {
                minCwnd = TcpEntry.this.cwndNegotiator.computeMinCwnd(yourCwnd);
                // immediately enforce floor
                if (cwnd < minCwnd) {
                    cwnd = minCwnd;
                }
            }
        }

        public int getMinCwnd() {
            return minCwnd;
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
    }

    public class ReceivingQueue {
        private final ArrayDeque<Segment> q = new ArrayDeque<>();
        private final TreeMap<Long, Segment> oooBuffer = new TreeMap<>();
        private int currentSize = 0;
        private int oooSize = 0;
        private long expectingSeq;
        private long ackedSeq;
        private int window = RMEM_MAX;
        private int windowScale = 64;

        public ReceivingQueue(long seq) {
            this.expectingSeq = seq;
            this.ackedSeq = seq;
        }

        public void incExpectingSeq() {
            assert ackedSeq == expectingSeq;
            expectingSeq += 1;
            ackedSeq += 1;
        }

        public void setInitialSeq(long seq) {
            if (this.expectingSeq == 0 && this.ackedSeq == 0) {
                this.expectingSeq = seq;
                this.ackedSeq = seq;
            } else {
                Logger.error(LogType.IMPROPER_USE, "calling setInitialSeq while expectingSeq(" + expectingSeq + ") or acked(" + ackedSeq + ") is not 0");
            }
        }

        public boolean hasMoreDataToRead() {
            return !q.isEmpty();
        }

        public void store(Segment segment) {
            if (state.remoteClosed) {
                Logger.error(LogType.IMPROPER_USE, "FIN received but is still storing data");
                return;
            }
            if (currentSize + oooSize >= RMEM_MAX) {
                // memory is full
                return;
            }

            long segBegin = segment.seqBeginInclusive;
            long segEnd = segment.seqEndExclusive;

            // entirely before expectingSeq - already received
            if (segEnd <= expectingSeq) {
                return;
            }

            // starts at or before expectingSeq - can add to ready queue directly
            if (segBegin <= expectingSeq) {
                ByteArray data = segment.data;
                if (segBegin < expectingSeq) {
                    int trim = (int) (expectingSeq - segBegin);
                    data = data.sub(trim, data.length() - trim);
                }
                q.add(new Segment(expectingSeq, data.copy()));
                expectingSeq += data.length();
                currentSize += data.length();

                // drain any OOO segments that are now contiguous
                drainOOOBuffer();

                resetWindow();

                // run callback
                if (connectionHandler != null) {
                    connectionHandler.readable(TcpEntry.this);
                }
                return;
            }

            // out of order (segBegin > expectingSeq) - buffer for later
            bufferOOOSegment(segment);
        }

        private void bufferOOOSegment(Segment segment) {
            long begin = segment.seqBeginInclusive;
            long end = segment.seqEndExclusive;
            ByteArray data = segment.data;

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
                    end = cs.seqEndExclusive;
                }
                // if cs.seqEndExclusive <= end, it's fully contained, already removed
            }

            Segment merged = new Segment(begin, data);
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
                q.add(new Segment(expectingSeq, data.copy()));
                expectingSeq += data.length();
                currentSize += data.length();
            }
        }

        public ByteArray apiRead(int maxLen) {
            if (currentSize < maxLen) {
                maxLen = currentSize;
            }
            ByteArray arr = null;
            int len = 0;
            while (!q.isEmpty() && len < maxLen) {
                Segment s = q.peekFirst();
                ByteArray data = s.data;
                int subLen = data.length();
                if (len + data.length() > maxLen) {
                    subLen = maxLen - len;
                    data = data.sub(0, subLen);
                }
                len += data.length();
                if (arr == null) {
                    arr = data;
                } else {
                    arr = arr.concat(data);
                }
                if (subLen == s.data.length()) {
                    // fully consumed
                    q.pollFirst();
                    currentSize -= s.data.length();
                    ackedSeq = s.seqEndExclusive;
                } else {
                    // partially consumed
                    q.pollFirst();
                    var remaining = new Segment(s.seqBeginInclusive + subLen, s.data.sub(subLen, s.data.length() - subLen));
                    q.addFirst(remaining);
                    currentSize -= subLen;
                    ackedSeq = remaining.seqBeginInclusive;
                    break;
                }
            }
            // restore the receive window after consuming data
            resetWindow();
            return arr;
        }

        public int getCurrentSize() {
            return currentSize;
        }

        public long getExpectingSeq() {
            return expectingSeq;
        }

        public long getAckedSeq() {
            return ackedSeq;
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
         * Each block is a [begin, end) pair describing contiguous received data beyond the
         * cumulative ACK point (ackedSeq).
         */
        public List<SAckTuple> getSAckBlocks() {
            if (oooBuffer.isEmpty()) {
                return Collections.emptyList();
            }
            var blocks = new ArrayList<SAckTuple>();
            for (var entry : oooBuffer.values()) {
                long begin = Math.max(entry.seqBeginInclusive, ackedSeq);
                if (begin < entry.seqEndExclusive) {
                    blocks.add(new SAckTuple(begin, entry.seqEndExclusive));
                }
            }
            return blocks;
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
