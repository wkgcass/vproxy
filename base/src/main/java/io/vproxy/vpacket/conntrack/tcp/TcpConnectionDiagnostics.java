package io.vproxy.vpacket.conntrack.tcp;

import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;

/**
 * Fixed-size, opt-in counters for investigating TCP stream stalls. No instance is
 * allocated unless {@code -Dvproxy_tcp_stack_diag=on} is set.
 */
final class TcpConnectionDiagnostics {
    static boolean enabled() {
        return "on".equals(Utils.getSystemProperty("tcp_stack_diag", "off"));
    }

    static final int TIMER_RETRANSMIT = 0;
    static final int TIMER_PERSIST = 1;
    static final int TIMER_DELAYED_ACK = 2;
    static final int TIMER_TIME_WAIT = 3;

    private long receivedBytes;
    private long sentBytes;
    private long acknowledgedBytes;
    private long retransmittedBytes;
    private long fastRetransmits;
    private long timeoutRetransmits;
    private long sackRetransmits;
    private long persistProbes;
    private long persistProbeBytes;
    private long lastReceiveSequence;
    private long lastSendSequence;
    private long lastAckSequence;
    private int lastRemoteWindow;
    private int lastCongestionWindow;
    private int lastBytesInFlight;
    private int lastSendQueueSize;
    private int lastReceiveQueueSize;
    private int maxSendQueueSize;
    private int maxReceiveQueueSize;
    private long receivedFin;
    private long sentFin;
    private long receivedRst;
    private long sentRst;
    private String closeReason = "destroyed";
    private boolean reported;

    private long retransmitTimerCount;
    private long retransmitTimerLatenessTotal;
    private long retransmitTimerLatenessMax;
    private long persistTimerCount;
    private long persistTimerLatenessTotal;
    private long persistTimerLatenessMax;
    private long delayedAckTimerCount;
    private long delayedAckTimerLatenessTotal;
    private long delayedAckTimerLatenessMax;
    private long timeWaitTimerCount;
    private long timeWaitTimerLatenessTotal;
    private long timeWaitTimerLatenessMax;

    void received(TcpEntry entry, long seq, int bytes) {
        receivedBytes += bytes;
        lastReceiveSequence = seq + bytes;
        snapshotQueues(entry);
    }

    void sent(TcpEntry entry, long seq, int bytes, String retransmissionReason) {
        sentBytes += bytes;
        lastSendSequence = seq + bytes;
        if (retransmissionReason != null) {
            retransmittedBytes += bytes;
            switch (retransmissionReason) {
                case "fast": ++fastRetransmits; break;
                case "sack": ++sackRetransmits; break;
                case "persist": ++persistProbes; persistProbeBytes += bytes; break;
                default: ++timeoutRetransmits;
            }
        }
        snapshotQueues(entry);
    }

    void acknowledged(TcpEntry entry, long ackSeq, long bytes) {
        lastAckSequence = ackSeq;
        if (bytes > 0) acknowledgedBytes += bytes;
        snapshotQueues(entry);
    }

    void persistProbe(TcpEntry entry, int bytes) {
        ++persistProbes;
        sentBytes += bytes;
        retransmittedBytes += bytes;
        persistProbeBytes += bytes;
        snapshotQueues(entry);
    }

    void receivedFin() {
        ++receivedFin;
    }

    void sentFin() {
        ++sentFin;
    }

    void receivedRst() {
        ++receivedRst;
    }

    void sentRst() {
        ++sentRst;
    }

    void closeReason(String reason) {
        closeReason = reason;
    }

    void timer(TcpEntry entry, int timer, long expectedAt, long actualAt) {
        long lateness = Math.max(0, actualAt - expectedAt);
        switch (timer) {
            case TIMER_RETRANSMIT:
                ++retransmitTimerCount;
                retransmitTimerLatenessTotal += lateness;
                retransmitTimerLatenessMax = Math.max(retransmitTimerLatenessMax, lateness);
                break;
            case TIMER_PERSIST:
                ++persistTimerCount;
                persistTimerLatenessTotal += lateness;
                persistTimerLatenessMax = Math.max(persistTimerLatenessMax, lateness);
                break;
            case TIMER_DELAYED_ACK:
                ++delayedAckTimerCount;
                delayedAckTimerLatenessTotal += lateness;
                delayedAckTimerLatenessMax = Math.max(delayedAckTimerLatenessMax, lateness);
                break;
            case TIMER_TIME_WAIT:
                ++timeWaitTimerCount;
                timeWaitTimerLatenessTotal += lateness;
                timeWaitTimerLatenessMax = Math.max(timeWaitTimerLatenessMax, lateness);
                break;
            default:
                throw new IllegalArgumentException("unknown TCP timer type: " + timer);
        }
        snapshotQueues(entry);
    }

    private void snapshotQueues(TcpEntry entry) {
        lastRemoteWindow = entry.sendingQueue.getWindow();
        lastCongestionWindow = entry.sendingQueue.getCwnd();
        lastBytesInFlight = entry.sendingQueue.getBytesInFlight();
        lastSendSequence = entry.sendingQueue.getFetchSeq();
        lastAckSequence = entry.sendingQueue.getAckSeq();
        lastReceiveSequence = entry.receivingQueue.getExpectingSeq();
        lastSendQueueSize = entry.sendingQueue.getCurrentSize();
        lastReceiveQueueSize = entry.receivingQueue.getCurrentSize();
        maxSendQueueSize = Math.max(maxSendQueueSize, lastSendQueueSize);
        maxReceiveQueueSize = Math.max(maxReceiveQueueSize, lastReceiveQueueSize);
    }

    void reportOnce(TcpEntry entry, TcpState oldState) {
        if (reported) return;
        reported = true;
        snapshotQueues(entry);
        String local = String.valueOf(entry.local);
        String remote = String.valueOf(entry.remote);
        Logger.alert("[TCP-DIAG] close=" + closeReason + " state=" + oldState
            + " local=" + local + " remote=" + remote
            + " recvBytes=" + receivedBytes + " sendBytes=" + sentBytes + " ackedBytes=" + acknowledgedBytes
            + " recvNext=" + lastReceiveSequence + " sendNext=" + lastSendSequence + " ack=" + lastAckSequence
            + " rwnd=" + lastRemoteWindow + " cwnd=" + lastCongestionWindow + " inflight=" + lastBytesInFlight
            + " sendQueue=" + lastSendQueueSize + "/" + maxSendQueueSize
            + " recvQueue=" + lastReceiveQueueSize + "/" + maxReceiveQueueSize
            + " retransmittedBytes=" + retransmittedBytes + " fastRetransmits=" + fastRetransmits
            + " rtoRetransmits=" + timeoutRetransmits + " sackRetransmits=" + sackRetransmits
            + " persistProbes=" + persistProbes + "/" + persistProbeBytes + "B FINin=" + receivedFin + " FINout=" + sentFin + " RSTin=" + receivedRst + " RSTout=" + sentRst
            + " rtoTimerLateMs=" + timerStats(retransmitTimerCount, retransmitTimerLatenessTotal, retransmitTimerLatenessMax)
            + " persistTimerLateMs=" + timerStats(persistTimerCount, persistTimerLatenessTotal, persistTimerLatenessMax)
            + " delayedAckTimerLateMs=" + timerStats(delayedAckTimerCount, delayedAckTimerLatenessTotal, delayedAckTimerLatenessMax)
            + " timeWaitTimerLateMs=" + timerStats(timeWaitTimerCount, timeWaitTimerLatenessTotal, timeWaitTimerLatenessMax));
    }

    private static String timerStats(long count, long total, long max) {
        return count + ":avg=" + (count == 0 ? 0 : total / count) + ":max=" + max;
    }

}
