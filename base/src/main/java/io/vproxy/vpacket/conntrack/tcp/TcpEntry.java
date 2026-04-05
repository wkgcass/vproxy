package io.vproxy.vpacket.conntrack.tcp;

import io.vproxy.base.selector.TimerEvent;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.misc.WithUserData;
import io.vproxy.vfd.IPPort;

import java.nio.ByteBuffer;
import java.util.*;

public class TcpEntry implements WithUserData {
    public static final int WMEM_MAX = 212992;
    public static final int RMEM_MAX = 212992;
    public static final int SND_DEFAULT_MSS = 1360;
    public static final int RCV_MSS = 1360;
    public static final int TCP_SEQ_INIT_MIN = Integer.MAX_VALUE / 3;
    public static final int TCP_SEQ_RAND = Integer.MAX_VALUE / 2;
    public static final int RTO_MIN = 200;
    public static final int RTO_MAX = 120_000;
    public static final int DELAYED_ACK_TIMEOUT = 20;
    public static final int MAX_REMOTE_WINDOW = 16 * 1024 * 1024; // 16MB, safety cap for peer's advertised window
    public static final int MAX_RETRANSMISSION_AFTER_CLOSING = 7;
    public static final int TIME_WAIT_TIMEOUT_MS = 60_000; // 2MSL

    // 高延迟/高丢包网络优化参数
    public static final int HIGH_LATENCY_INITIAL_CWND_MSS = 20; // 初始cwnd为20*MSS
    public static final double HIGH_LATENCY_LOSS_BETA = 0.90; // 丢包时保留90%的cwnd（借鉴KCP的保守策略）
    public static final int HIGH_LATENCY_MIN_CWND_MSS = 4; // 最小cwnd为4*MSS
    public static final double HIGH_LATENCY_RTT_VARIANCE = 0.3; // RTT方差权重，适应高延迟波动

    // 快速重传参数（借鉴KCP）
    public static final int DEFAULT_FAST_RESEND = 3; // 收到3个重复ACK立即重传
    public static final int DEFAULT_FAST_LIMIT = 5; // 最多快速重传5次
    public static final int DEFAULT_DEAD_LINK = 20; // 死链检测阈值

    public final IPPort remote;
    public final IPPort local;
    private TcpState state;
    private boolean needClosing = false;

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

    public boolean requireClosing() {
        return needClosing;
    }

    public void doClose() {
        this.needClosing = true;
    }

    /**
     * 配置高延迟/高丢包网络优化模式（类似KCP的nodelay）
     * @param enable 是否启用高延迟优化模式
     * @param fastresend 快速重传阈值（收到多少个重复ACK触发），0表示禁用
     * @param fastlimit 最多快速重传次数，0表示不限制
     * @param deadLink 死链检测阈值（单个segment最大重传次数）
     */
    public void setHighLatencyMode(boolean enable, int fastresend, int fastlimit, int deadLink) {
        if (sendingQueue != null) {
            if (enable) {
                sendingQueue.setFastResend(fastresend, fastlimit);
                sendingQueue.setDeadLink(deadLink);
            } else {
                // 恢复默认值
                sendingQueue.setFastResend(DEFAULT_FAST_RESEND, DEFAULT_FAST_LIMIT);
                sendingQueue.setDeadLink(DEFAULT_DEAD_LINK);
            }
        }
    }

    public class SendingQueue {
        private static final double CUBIC_C = 0.4;
        private static final double CUBIC_BETA = 0.7;
        private static final double RTT_ALPHA = 0.125;
        private static final double RTT_BETA = 0.25;

        private final LinkedList<Segment> q = new LinkedList<>();
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

        // RTT estimation (RFC 6298)
        private long srttUs = -1;   // smoothed RTT in microseconds (-1 = no sample yet)
        private long rttVarUs = -1; // RTT variance in microseconds
        private long rto = RTO_MIN; // computed RTO in ms

        // 带宽控制配置（可通过外部设置）
        private int maxBandwidthOverhead = 0; // 0 = unlimited, >0 = 最大额外带宽消耗(bytes/sec)

        // 快速重传状态（借鉴KCP）
        private int fastresend = DEFAULT_FAST_RESEND;
        private int fastlimit = DEFAULT_FAST_LIMIT;
        private int deadLink = DEFAULT_DEAD_LINK;
        private int totalXmit = 0; // 总重传次数

        // per-segment send timestamps for RTT sampling (seq -> sendTimeMs)
        private final java.util.Map<Long, Long> sendTimes = new java.util.HashMap<>();

        public SendingQueue(int seq) {
            this.latestSeq = seq;
            this.ackSeq = seq;
            this.fetchSeq = seq;
        }

        public void init(int window, int mss, int windowScale) {
            this.window = Math.min(MAX_REMOTE_WINDOW, window * windowScale);
            this.mss = mss;
            this.windowScale = windowScale;
            // 高延迟网络：使用更大的初始cwnd快速填满BDP
            this.cwnd = Math.min(this.window, HIGH_LATENCY_INITIAL_CWND_MSS * mss);
            this.ssthresh = Integer.MAX_VALUE;
            this.lastLossTime = System.currentTimeMillis();
            this.lastLossCwnd = cwnd;
        }

        public void setCwnd(int cwnd) {
            this.cwnd = cwnd;
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
            int sendLimit = getAvailableSendWindow();
            if (sendLimit <= 0) {
                return Collections.emptyList();
            }

            // 首先检查快速重传（借鉴KCP）
            List<Segment> fastRetransmit = checkFastRetransmit();
            if (!fastRetransmit.isEmpty()) {
                // 执行快速重传，限制总字节数不超过可用窗口
                // 注意：不累加 bytesInFlight，这些 segment 首次发送时已计入
                int totalBytes = 0;
                List<Segment> allowed = new LinkedList<>();
                for (var seg : fastRetransmit) {
                    if (totalBytes + seg.data.length() > sendLimit) {
                        break;
                    }
                    totalBytes += seg.data.length();
                    allowed.add(seg);
                }
                return allowed;
            }


            long endSeq = ackSeq + Math.min(mss, sendLimit);
            Segment s = fetch0(ackSeq, endSeq);
            if (s == null) {
                return Collections.emptyList();
            }
            List<Segment> ret = new LinkedList<>();
            ret.add(s);
            int total = s.data.length();
            while (true) {
                int len = mss;
                if (total + len > sendLimit) {
                    len = sendLimit - total;
                }
                if (len <= 0) {
                    break;
                }
                s = fetch0(ackSeq + total, ackSeq + total + len);
                if (s == null) {
                    break;
                }
                total += s.data.length();
                ret.add(s);
            }
            // track in-flight bytes and record send times
            int totalBytes = 0;
            long now = System.currentTimeMillis();
            for (var seg : ret) {
                totalBytes += seg.data.length();
                sendTimes.put(seg.seqBeginInclusive, now);
            }
            bytesInFlight += totalBytes;
            return ret;
        }

        /**
         * Returns how many bytes may be sent right now, considering both
         * the peer's receive window and our congestion window.
         */
        public int getAvailableSendWindow() {
            return Math.min(window, cwnd - bytesInFlight);
        }

        private Segment fetch0(long begin, long endExclusive) {
            if (q.size() == 0) {
                return null;
            }
            if (q.peekFirst().seqBeginInclusive > begin) {
                // cannot retrieve data at the specified seq id
                return null;
            }
            var ite = q.iterator();
            ByteArray arr = null;
            while (ite.hasNext()) {
                Segment s = ite.next();
                if (s.seqBeginInclusive >= endExclusive) {
                    break;
                }
                if (s.seqEndExclusive <= begin) {
                    continue;
                }
                ByteArray data = s.data;
                if (s.seqBeginInclusive < begin) {
                    int from = (int) (begin - s.seqBeginInclusive);
                    int to = (int) (Math.min(endExclusive, s.seqEndExclusive) - s.seqBeginInclusive - (begin - s.seqBeginInclusive));
                    data = data.sub(from, to);
                } else if (s.seqEndExclusive > endExclusive) {
                    data = data.sub(0, (int) (endExclusive - s.seqBeginInclusive));
                }
                if (arr == null) {
                    arr = data;
                } else {
                    arr = arr.concat(data);
                }
            }
            if (arr == null) { // nothing found
                return null;
            }
            var seg = new Segment(begin, arr);
            fetchSeq = seg.seqEndExclusive;
            return seg;
        }

        public void ack(long seq, int window) {
            if (finAcked) { // nothing to do because the output is completely shutdown
                return;
            }

            this.window = Math.min(MAX_REMOTE_WINDOW, window * windowScale);

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

            // 快速ACK检测（借鉴KCP的parseFastack）
            // 收到重复ACK(seq <= ackSeq)时，标记ackSeq位置之前的所有segment
            // 这些segment的数据对端已部分或完全收到但仍在等待后续数据
            // 包括: seqBeginInclusive < ackSeq(部分ACK) 和 seqBeginInclusive == ackSeq(完全对齐)
            if (seq <= ackSeq && !q.isEmpty()) {
                for (var s : q) {
                    if (s.seqBeginInclusive <= ackSeq) {
                        s.fastack++;
                    } else {
                        break; // queue is ordered, no need to continue
                    }
                }
            }

            // compute newly acked bytes for congestion control
            long newlyAcked = seq - ackSeq;
            if (newlyAcked > 0) {
                bytesInFlight = Math.max(0, bytesInFlight - (int) newlyAcked);
                // CUBIC update: grow cwnd for each newly acked segment
                cubicOnAck((int) newlyAcked);
                // 重置快速重传计数器
                resetFastack(seq);
            }

            if (ackSeq < seq) {
                ackSeq = seq;
            }
            var ite = q.iterator();
            boolean sampled = false;
            while (ite.hasNext()) {
                var s = ite.next();
                if (s.seqEndExclusive >= seq) {
                    break;
                }
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
                rttVarUs = (long) ((1 - HIGH_LATENCY_RTT_VARIANCE) * rttVarUs + HIGH_LATENCY_RTT_VARIANCE * diff);
                srttUs = (long) ((1 - RTT_ALPHA) * srttUs + RTT_ALPHA * rttUs);
            }
            rto = Math.max(RTO_MIN, Math.min(RTO_MAX, (srttUs + 4 * rttVarUs) / 1000));
        }

        private void cubicOnAck(int newlyAcked) {
            if (cwnd < ssthresh) {
                // slow start: increase by MSS per ACK (exponential)
                cwnd += mss;
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
            // cap cwnd to peer's receive window
            cwnd = Math.min(cwnd, window);
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
            // 高延迟网络：保留90%的cwnd而非50%，减少恢复时间
            int newCwnd = (int) (cwnd * HIGH_LATENCY_LOSS_BETA);
            ssthresh = Math.max(newCwnd, HIGH_LATENCY_MIN_CWND_MSS * mss);
            cwnd = ssthresh;
            lastLossTime = System.currentTimeMillis();
        }

        /**
         * Called when retransmitting — reset bytesInFlight so fetch() can send again.
         */
        public void onRetransmit() {
            bytesInFlight = 0;
        }

        public int getCurrentSize() {
            return currentSize;
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
            return cwnd;
        }

        public boolean needToSendFin() {
            return state.finSent && !finAcked;
        }

        public boolean ackOfFinReceived() {
            return finAcked;
        }

        /**
         * 设置最大额外带宽消耗限制
         * @param maxBytesPerSec 0表示不限制，>0表示每秒最大额外开销(bytes)
         */
        public void setMaxBandwidthOverhead(int maxBytesPerSec) {
            this.maxBandwidthOverhead = maxBytesPerSec;
        }

        public int getMaxBandwidthOverhead() {
            return maxBandwidthOverhead;
        }

        /**
         * 检查当前是否可以发送数据（考虑带宽限制）
         * @return true if can send, false if bandwidth limited
         */
        public boolean canSendWithBandwidthLimit() {
            if (maxBandwidthOverhead <= 0) {
                return true; // unlimited
            }
            if (srttUs < 0) {
                return true; // no RTT sample yet, allow sending
            }
            // 简化实现：基于当前cwnd估算带宽，实际应用中需要更精确的测量
            long estimatedBandwidth = (long) cwnd * 1000 / Math.max(1, srttUs / 1000);
            return estimatedBandwidth <= maxBandwidthOverhead;
        }

        /**
         * 设置快速重传参数（借鉴KCP）
         * @param fastresend 收到多少个重复ACK触发快速重传，0表示禁用
         * @param fastlimit 最多快速重传次数，0表示不限制
         */
        public void setFastResend(int fastresend, int fastlimit) {
            this.fastresend = fastresend;
            this.fastlimit = fastlimit;
        }

        public int getFastresend() {
            return fastresend;
        }

        public int getFastlimit() {
            return fastlimit;
        }

        /**
         * 设置死链检测阈值（借鉴KCP）
         * @param deadLink 单个segment最大重传次数，超过则认为连接断开
         */
        public void setDeadLink(int deadLink) {
            this.deadLink = deadLink;
        }

        public int getDeadLink() {
            return deadLink;
        }

        /**
         * 检查并执行快速重传（借鉴KCP）
         * @return 需要快速重传的segment列表，如果没有则返回空列表
         */
        public List<Segment> checkFastRetransmit() {
            if (fastresend <= 0) {
                return Collections.emptyList();
            }

            List<Segment> retransmitList = new LinkedList<>();
            var ite = q.iterator();
            while (ite.hasNext()) {
                var s = ite.next();
                if (s.fastack >= fastresend) {
                    if (s.xmit < fastlimit || fastlimit <= 0) {
                        retransmitList.add(s);
                        s.xmit++;
                        s.fastack = 0;
                        totalXmit++;

                        // 死链检测
                        if (s.xmit >= deadLink) {
                            // 标记连接需要关闭
                            doClose();
                        }
                    }
                }
            }
            return retransmitList;
        }

        /**
         * 获取总重传次数（用于监控）
         */
        public int getTotalXmit() {
            return totalXmit;
        }

        /**
         * 重置快速重传计数器（在收到新ACK时调用）
         */
        public void resetFastack(long ackSeq) {
            var ite = q.iterator();
            while (ite.hasNext()) {
                var s = ite.next();
                if (s.seqBeginInclusive >= ackSeq) {
                    s.fastack = 0;
                }
            }
        }
    }

    public class ReceivingQueue {
        private final LinkedList<Segment> q = new LinkedList<>();
        private int currentSize = 0;
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

            if (currentSize > RMEM_MAX) {
                // memory is full
                return;
            }
            if (segment.seqBeginInclusive > expectingSeq) {
                // missing packets
                // we do not handle mis-ordered packets for now
                return;
            }
            if (segment.seqEndExclusive <= expectingSeq) {
                // already fully received
                return;
            }
            var data = segment.data;
            if (segment.seqBeginInclusive < expectingSeq) {
                int incr = (int) (expectingSeq - segment.seqBeginInclusive);
                data = data.sub(incr, data.length() - incr);
            }
            q.add(new Segment(expectingSeq, data.copy()));
            expectingSeq += data.length();
            currentSize += data.length();
            window -= data.length();
            if (window < 0) {
                window = 0;
            }

            // run callback
            if (connectionHandler != null) {
                connectionHandler.readable(TcpEntry.this);
            }
        }

        public ByteArray apiRead(int maxLen) {
            if (currentSize < maxLen) {
                maxLen = currentSize;
            }
            ByteArray arr = null;
            int len = 0;
            var ite = q.iterator();
            while (ite.hasNext()) {
                var s = ite.next();
                var data = s.data;
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
                ite.remove();
                currentSize -= s.data.length();
                ackedSeq = s.seqEndExclusive;
                if (subLen != s.data.length()) {
                    var newSegment = new Segment(s.seqBeginInclusive + subLen, s.data.sub(subLen, s.data.length() - subLen));
                    q.addFirst(newSegment);
                    currentSize += newSegment.data.length();
                    ackedSeq = newSegment.seqBeginInclusive;
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
            this.window = RMEM_MAX - currentSize;
        }

        public int getWindowScale() {
            return windowScale;
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
