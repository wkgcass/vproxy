package vproxy.vpacket.conntrack.tcp;

import vproxy.base.selector.TimerEvent;
import vproxy.base.util.ByteArray;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.base.util.Utils;
import vproxy.vfd.IPPort;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class TcpEntry {
    public static final int WMEM_MAX = 212992;
    public static final int RMEM_MAX = 212992;
    public static final int SND_DEFAULT_MSS = 1360;
    public static final int RCV_MSS = 1360;
    public static final int TCP_SEQ_INIT_MIN = Integer.MAX_VALUE / 3;
    public static final int TCP_SEQ_RAND = Integer.MAX_VALUE / 2;
    public static final int RTO_MIN = 200;
    public static final int RTO_MAX = 120_000;
    public static final int DELAYED_ACK_TIMEOUT = 20;
    public static final int MAX_REMOTE_WINDOW_MSS_DUP = 45; // * mss
    public static final int MAX_RETRANSMISSION_AFTER_CLOSING = 7;

    public final IPPort source;
    public final IPPort destination;
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
    // }

    public TcpEntry(TcpListenEntry listenEntry, IPPort source, IPPort destination, long seq) {
        this.parent = listenEntry;
        this.source = source;
        this.destination = destination;
        this.state = TcpState.CLOSED;
        this.sendingQueue = new SendingQueue(new Random().nextInt(TCP_SEQ_RAND) + TCP_SEQ_INIT_MIN);
        this.receivingQueue = new ReceivingQueue(seq + 1 /* the sequence is syn_packet.seq + 1 */);
    }

    public TcpListenEntry getParent() {
        return parent;
    }

    public void setConnectionHandler(ConnectionHandler connectionHandler) {
        if (this.connectionHandler != null) {
            throw new IllegalStateException("cannot set connectionHandler because it already exists");
        }
        this.connectionHandler = connectionHandler;
        this.parent = null;
    }

    public void destroy() {
        state = TcpState.CLOSED;
        retransmissionTimer.cancel();
        if (connectionHandler != null) {
            connectionHandler.destroy(this);
        }
    }

    public TcpState getState() {
        return state;
    }

    public void setState(TcpState state) {
        var old = this.state;
        this.state = state;
        if (!old.remoteClosed && state.remoteClosed) {
            if (connectionHandler != null) {
                connectionHandler.readable(this);
            }
        }
    }

    public boolean requireClosing() {
        return needClosing;
    }

    public void doClose() {
        this.needClosing = true;
    }

    public class SendingQueue {
        private final LinkedList<Segment> q = new LinkedList<>();
        private int currentSize = 0;
        private long latestSeq;
        private long ackSeq;
        private long fetchSeq;
        private int window = 0;
        private int mss = 0;
        private int windowScale = 1;
        private boolean finAcked = false;

        public SendingQueue(int seq) {
            this.latestSeq = seq;
            this.ackSeq = seq;
            this.fetchSeq = seq;
        }

        public void init(int window, int mss, int windowScale) {
            this.window = Math.min(MAX_REMOTE_WINDOW_MSS_DUP * mss, window);
            this.mss = mss;
            this.windowScale = windowScale;
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
            long endSeq = ackSeq + mss;
            if (mss > window) {
                endSeq = ackSeq + window;
            }
            Segment s = fetch0(ackSeq, endSeq);
            if (s == null) {
                return Collections.emptyList();
            }
            List<Segment> ret = new LinkedList<>();
            ret.add(s);
            int total = s.data.length();
            while (true) {
                int len = mss;
                if (total + len > window) {
                    len = window - total;
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
            return ret;
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

            this.window = Math.min(MAX_REMOTE_WINDOW_MSS_DUP * mss, window * windowScale);

            if (state.finSent && seq == latestSeq + 1) {
                ackSeq = latestSeq + 1;
                fetchSeq = latestSeq + 1;
                finAcked = true;
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
            if (ackSeq < seq) {
                ackSeq = seq;
            }
            var ite = q.iterator();
            while (ite.hasNext()) {
                var s = ite.next();
                if (s.seqEndExclusive >= seq) {
                    break;
                }
                currentSize -= s.data.length();
                ite.remove();
                if (connectionHandler != null) {
                    connectionHandler.writable(TcpEntry.this);
                }
            }
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

        public boolean needToSendFin() {
            return state.finSent && !finAcked;
        }

        public boolean ackOfFinReceived() {
            return finAcked;
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
}
