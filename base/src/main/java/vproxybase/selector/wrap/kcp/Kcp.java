/*
MIT License

Copyright (c) 2017 Zheng Sun

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

package vproxybase.selector.wrap.kcp;

import vproxybase.selector.wrap.kcp.mock.*;
import vproxybase.util.LogType;
import vproxybase.util.Logger;
import vproxybase.util.Utils;
import vproxybase.util.ex.ReItrLinkedList;
import vproxybase.util.ex.ReusableListIterator;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * Java implementation of <a href="https://github.com/skywind3000/kcp">KCP</a>
 *
 * @author <a href="mailto:szhnet@gmail.com">szh</a>
 */
public class Kcp {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(Kcp.class);

    private static final InternalLogger kcpMonitorLog = InternalLoggerFactory.getInstance("io.jpower.kcp.netty" +
        ".kcpMonitor");

    /**
     * no delay min rto
     */
    public static final int IKCP_RTO_NDL = 30;

    /**
     * normal min rto
     */
    public static final int IKCP_RTO_MIN = 100;

    public static final int IKCP_RTO_DEF = 200;

    public static final int IKCP_RTO_MAX = 60000;

    /**
     * cmd: push data
     */
    public static final byte IKCP_CMD_PUSH = 81;

    /**
     * cmd: ack
     */
    public static final byte IKCP_CMD_ACK = 82;

    /**
     * cmd: window probe (ask)
     */
    public static final byte IKCP_CMD_WASK = 83;

    /**
     * cmd: window size (tell)
     */
    public static final byte IKCP_CMD_WINS = 84;

    /**
     * need to send IKCP_CMD_WASK
     */
    public static final int IKCP_ASK_SEND = 1;

    /**
     * need to send IKCP_CMD_WINS
     */
    public static final int IKCP_ASK_TELL = 2;

    public static final int IKCP_WND_SND = 32;

    public static final int IKCP_WND_RCV = 32;

    public static final int IKCP_MTU_DEF = 1400;

    public static final int IKCP_ACK_FAST = 3;

    public static final int IKCP_INTERVAL = 100;

    public static final int IKCP_OVERHEAD = 24;

    public static final int IKCP_DEADLINK = 20;

    public static final int IKCP_THRESH_INIT = 2;

    public static final int IKCP_THRESH_MIN = 2;

    /**
     * 7 secs to probe window size
     */
    public static final int IKCP_PROBE_INIT = 7000;

    /**
     * up to 120 secs to probe window
     */
    public static final int IKCP_PROBE_LIMIT = 120000;

    /**
     * max times to trigger fastack
     */
    public static final int IKCP_FASTACK_LIMIT = 5;

    private int conv;

    private int mtu = IKCP_MTU_DEF;

    private int mss = this.mtu - IKCP_OVERHEAD;

    private int state;

    private long sndUna;

    private long sndNxt;

    private long rcvNxt;

    private long tsRecent;

    private long tsLastack;

    private int ssthresh = IKCP_THRESH_INIT;

    private int rxRttvar;

    private int rxSrtt;

    private int rxRto = IKCP_RTO_DEF;

    private int rxMinrto = IKCP_RTO_MIN;

    private int sndWnd = IKCP_WND_SND;

    private int rcvWnd = IKCP_WND_RCV;

    private int rmtWnd = IKCP_WND_RCV;

    private int cwnd;

    private int probe;

    private long current;

    private int interval = IKCP_INTERVAL;

    private long tsFlush = IKCP_INTERVAL;

    private int xmit;

    private int maxSegXmit;

    private boolean nodelay;

    private boolean updated;

    private long tsProbe;

    private int probeWait;

    private int deadLink = IKCP_DEADLINK;

    private int incr;

    private LinkedList<Segment> sndQueue = new LinkedList<>();

    private ReItrLinkedList<Segment> rcvQueue = new ReItrLinkedList<>();

    private ReItrLinkedList<Segment> sndBuf = new ReItrLinkedList<>();

    private ReItrLinkedList<Segment> rcvBuf = new ReItrLinkedList<>();

    private ReusableListIterator<Segment> rcvQueueItr = rcvQueue.listIterator();

    private ReusableListIterator<Segment> sndBufItr = sndBuf.listIterator();

    private ReusableListIterator<Segment> rcvBufItr = rcvBuf.listIterator();

    private int[] acklist = new int[8];

    private int ackcount;

    private Object user;

    private int fastresend;

    private int fastlimit = IKCP_FASTACK_LIMIT;

    private boolean nocwnd;

    private boolean stream;

    private KcpOutput output;

    private ByteBufAllocator byteBufAllocator = ByteBufAllocator.DEFAULT;

    /**
     * automatically set conv
     */
    private boolean autoSetConv;

    private static long long2Uint(long n) {
        return n & 0x00000000FFFFFFFFL;
    }

    private static long int2Uint(int i) {
        return i & 0xFFFFFFFFL;
    }

    private static int ibound(int lower, int middle, int upper) {
        return Math.min(Math.max(lower, middle), upper);
    }

    private static long ibound(long lower, long middle, long upper) {
        return Math.min(Math.max(lower, middle), upper);
    }

    private static int itimediff(int later, int earlier) {
        return later - earlier;
    }

    private static int itimediff(long later, long earlier) {
        return (int) (later - earlier);
    }

    private static void output(ByteBuf data, Kcp kcp) {
        if (log.isDebugEnabled()) {
            log.debug("{} [RO] {} bytes", kcp, data.readableBytes());
        }
        if (data.readableBytes() == 0) {
            return;
        }
        kcp.output.out(data, kcp);
    }

    private static int encodeSeg(ByteBuf buf, Segment seg) {
        int offset = buf.writerIndex();

        buf.writeIntLE(seg.conv);
        buf.writeByte(seg.cmd);
        buf.writeByte(seg.frg);
        buf.writeShortLE(seg.wnd);
        buf.writeIntLE((int) seg.ts);
        buf.writeIntLE((int) seg.sn);
        buf.writeIntLE((int) seg.una);
        buf.writeIntLE(seg.data.readableBytes());

        return buf.writerIndex() - offset;
    }

    private static class Segment {

        private final Recycler.Handle<Segment> recyclerHandle;

        private int conv;

        private byte cmd;

        private short frg;

        private int wnd;

        private long ts;

        private long sn;

        private long una;

        private long resendts;

        private int rto;

        private int fastack;

        private int xmit;

        private ByteBuf data;

        private static final Recycler<Segment> RECYCLER = new Recycler<Segment>() {

            @Override
            protected Segment newObject(Handle<Segment> handle) {
                return new Segment(handle);
            }

        };

        private Segment(Recycler.Handle<Segment> recyclerHandle) {
            this.recyclerHandle = recyclerHandle;
        }

        void recycle(boolean releaseBuf) {
            conv = 0;
            cmd = 0;
            frg = 0;
            wnd = 0;
            ts = 0;
            sn = 0;
            una = 0;
            resendts = 0;
            rto = 0;
            fastack = 0;
            xmit = 0;
            if (releaseBuf) {
                data.release();
            }
            data = null;

            recyclerHandle.recycle(this);
        }

        static Segment createSegment(ByteBufAllocator byteBufAllocator, int size) {
            Segment seg = RECYCLER.get();
            if (size == 0) {
                seg.data = byteBufAllocator.ioBuffer(0, 0);
            } else {
                seg.data = byteBufAllocator.ioBuffer(size);
            }
            return seg;
        }

        static Segment createSegment(ByteBuf buf) {
            Segment seg = RECYCLER.get();
            seg.data = buf;
            return seg;
        }

    }

    public Kcp(int conv, KcpOutput output) {
        this.conv = conv;
        this.output = output;
    }

    public void release() {
        release(sndBuf);
        release(rcvBuf);
        release(sndQueue);
        release(rcvQueue);
    }

    private void release(List<Segment> segQueue) {
        for (Segment seg : segQueue) {
            seg.recycle(true);
        }
    }

    private ByteBuf createFlushByteBuf() {
        return byteBufAllocator.ioBuffer(this.mtu);
    }

    /**
     * user/upper level recv: returns size, returns below zero for EAGAIN
     *
     * @param buf
     * @return
     */
    public int recv(ByteBuf buf) {
        if (rcvQueue.isEmpty()) {
            return -1;
        }
        int peekSize = peekSize();

        if (peekSize < 0) {
            return -2;
        }

        if (peekSize > buf.maxCapacity()) {
            return -3;
        }

        boolean recover = false;
        if (rcvQueue.size() >= rcvWnd) {
            recover = true;
        }

        // merge fragment
        int len = 0;
        for (Iterator<Segment> itr = rcvQueueItr.rewind(); itr.hasNext(); ) {
            Segment seg = itr.next();
            len += seg.data.readableBytes();
            buf.writeBytes(seg.data);

            int fragment = seg.frg;

            // log
            if (log.isDebugEnabled()) {
                log.debug("{} recv sn={}", this, seg.sn);
            }

            itr.remove();
            seg.recycle(true);

            if (fragment == 0) {
                break;
            }
        }

        assert len == peekSize;

        // move available data from rcv_buf -> rcv_queue
        moveRcvData();

        // fast recover
        if (rcvQueue.size() < rcvWnd && recover) {
            // ready to send back IKCP_CMD_WINS in ikcp_flush
            // tell remote my window size
            probe |= IKCP_ASK_TELL;
        }

        return len;
    }

    public int recv(List<ByteBuf> bufList) {
        if (rcvQueue.isEmpty()) {
            return -1;
        }
        int peekSize = peekSize();

        if (peekSize < 0) {
            return -2;
        }

        boolean recover = false;
        if (rcvQueue.size() >= rcvWnd) {
            recover = true;
        }

        // merge fragment
        int len = 0;
        for (Iterator<Segment> itr = rcvQueueItr.rewind(); itr.hasNext(); ) {
            Segment seg = itr.next();
            len += seg.data.readableBytes();
            bufList.add(seg.data);

            int fragment = seg.frg;

            // log
            if (log.isDebugEnabled()) {
                log.debug("{} recv sn={}", this, seg.sn);
            }

            itr.remove();
            seg.recycle(false);

            if (fragment == 0) {
                break;
            }
        }

        assert len == peekSize;

        // move available data from rcv_buf -> rcv_queue
        moveRcvData();

        // fast recover
        if (rcvQueue.size() < rcvWnd && recover) {
            // ready to send back IKCP_CMD_WINS in ikcp_flush
            // tell remote my window size
            probe |= IKCP_ASK_TELL;
        }

        return len;
    }

    public int peekSize() {
        if (rcvQueue.isEmpty()) {
            return -1;
        }

        Segment seg = rcvQueue.peek();
        if (seg.frg == 0) {
            return seg.data.readableBytes();
        }

        if (rcvQueue.size() < seg.frg + 1) { // Some segments have not arrived yet
            return -1;
        }

        int len = 0;
        for (Iterator<Segment> itr = rcvQueueItr.rewind(); itr.hasNext(); ) {
            Segment s = itr.next();
            len += s.data.readableBytes();
            if (s.frg == 0) {
                break;
            }
        }

        return len;
    }

    public boolean canRecv() {
        if (rcvQueue.isEmpty()) {
            return false;
        }

        Segment seg = rcvQueue.peek();
        if (seg.frg == 0) {
            return true;
        }

        if (rcvQueue.size() < seg.frg + 1) { // Some segments have not arrived yet
            return false;
        }

        return true;
    }

    public int send1(ByteBuf buf) {
        assert mss > 0;

        int len = buf.readableBytes();
        if (len == 0) {
            return -1;
        }

        // append to previous segment in streaming mode (if possible)
        if (stream) {
            if (!sndQueue.isEmpty()) {
                Segment last = sndQueue.peekLast();
                ByteBuf lastData = last.data;
                int lastLen = lastData.readableBytes();
                if (lastLen < mss) {
                    int capacity = mss - lastLen;
                    int extend = len < capacity ? len : capacity;
                    if (lastData.maxWritableBytes() < extend) { // extend
                        ByteBuf newBuf = byteBufAllocator.ioBuffer(lastLen + extend);
                        newBuf.writeBytes(lastData);
                        lastData.release();
                        lastData = last.data = newBuf;
                    }
                    lastData.writeBytes(buf, extend);

                    len = buf.readableBytes();
                    if (len == 0) {
                        return 0;
                    }
                }
            }
        }

        int count = 0;
        if (len <= mss) {
            count = 1;
        } else {
            count = (len + mss - 1) / mss;
        }

        if (count > 255) { // Maybe don't need the conditon in stream mode
            return -2;
        }

        if (count == 0) { // impossible
            count = 1;
        }

        // fragment
        for (int i = 0; i < count; i++) {
            int size = len > mss ? mss : len;
            Segment seg = Segment.createSegment(byteBufAllocator, size);
            seg.data.writeBytes(buf, size);
            seg.frg = (short) (stream ? 0 : count - i - 1);
            sndQueue.add(seg);
            len = buf.readableBytes();
        }

        return 0;
    }

    public int send(ByteBuf buf) {
        assert mss > 0;

        int len = buf.readableBytes();
        if (len == 0) {
            return -1;
        }

        // append to previous segment in streaming mode (if possible)
        if (stream) {
            if (!sndQueue.isEmpty()) {
                Segment last = sndQueue.peekLast();
                ByteBuf lastData = last.data;
                int lastLen = lastData.readableBytes();
                if (lastLen < mss) {
                    int capacity = mss - lastLen;
                    int extend = len < capacity ? len : capacity;
                    if (lastData.maxWritableBytes() < extend) { // extend
                        ByteBuf newBuf = byteBufAllocator.ioBuffer(lastLen + extend);
                        newBuf.writeBytes(lastData);
                        lastData.release();
                        lastData = last.data = newBuf;
                    }
                    lastData.writeBytes(buf, extend);

                    len = buf.readableBytes();
                    if (len == 0) {
                        return 0;
                    }
                }
            }
        }

        int count = 0;
        if (len <= mss) {
            count = 1;
        } else {
            count = (len + mss - 1) / mss;
        }

        if (count > 255) { // Maybe don't need the conditon in stream mode
            return -2;
        }

        if (count == 0) { // impossible
            count = 1;
        }

        // segment
        for (int i = 0; i < count; i++) {
            int size = len > mss ? mss : len;
            Segment seg = Segment.createSegment(buf.readRetainedSlice(size));
            seg.frg = (short) (stream ? 0 : count - i - 1);
            sndQueue.add(seg);
            len = buf.readableBytes();
        }

        return 0;
    }

    private void updateAck(int rtt) {
        if (rxSrtt == 0) {
            rxSrtt = rtt;
            rxRttvar = rtt / 2;
        } else {
            int delta = rtt - rxSrtt;
            if (delta < 0) {
                delta = -delta;
            }
            rxRttvar = (3 * rxRttvar + delta) / 4;
            rxSrtt = (7 * rxSrtt + rtt) / 8;
            if (rxSrtt < 1) {
                rxSrtt = 1;
            }
        }
        int rto = rxSrtt + Math.max(interval, 4 * rxRttvar);
        rxRto = ibound(rxMinrto, rto, IKCP_RTO_MAX);
    }

    private void shrinkBuf() {
        if (sndBuf.size() > 0) {
            Segment seg = sndBuf.peek();
            sndUna = seg.sn;
        } else {
            sndUna = sndNxt;
        }
    }

    private void parseAck(long sn) {
        if (itimediff(sn, sndUna) < 0 || itimediff(sn, sndNxt) >= 0) {
            return;
        }

        for (Iterator<Segment> itr = sndBufItr.rewind(); itr.hasNext(); ) {
            Segment seg = itr.next();
            if (sn == seg.sn) {
                itr.remove();
                seg.recycle(true);
                break;
            }
            if (itimediff(sn, seg.sn) < 0) {
                break;
            }
        }
    }

    private void parseUna(long una) {
        for (Iterator<Segment> itr = sndBufItr.rewind(); itr.hasNext(); ) {
            Segment seg = itr.next();
            if (itimediff(una, seg.sn) > 0) {
                itr.remove();
                seg.recycle(true);
            } else {
                break;
            }
        }
    }

    private void parseFastack(long sn) {
        if (itimediff(sn, sndUna) < 0 || itimediff(sn, sndNxt) >= 0) {
            return;
        }

        for (Iterator<Segment> itr = sndBufItr.rewind(); itr.hasNext(); ) {
            Segment seg = itr.next();
            if (itimediff(sn, seg.sn) < 0) {
                break;
            } else if (sn != seg.sn) {
                seg.fastack++;
            }
        }
    }

    private void ackPush(long sn, long ts) {
        int newSize = 2 * (ackcount + 1);

        if (newSize > acklist.length) {
            int newCapacity = acklist.length << 1; // double capacity

            if (newCapacity < 0) {
                throw new OutOfMemoryError();
            }

            int[] newArray = new int[newCapacity];
            System.arraycopy(acklist, 0, newArray, 0, acklist.length);
            this.acklist = newArray;
        }

        acklist[2 * ackcount] = (int) sn;
        acklist[2 * ackcount + 1] = (int) ts;
        ackcount++;
    }

    private void parseData(Segment newSeg) {
        long sn = newSeg.sn;

        if (itimediff(sn, rcvNxt + rcvWnd) >= 0 || itimediff(sn, rcvNxt) < 0) {
            newSeg.recycle(true);
            return;
        }

        boolean repeat = false;
        boolean findPos = false;
        ListIterator<Segment> listItr = null;
        if (rcvBuf.size() > 0) {
            listItr = rcvBufItr.rewind(rcvBuf.size());
            while (listItr.hasPrevious()) {
                Segment seg = listItr.previous();
                if (seg.sn == sn) {
                    repeat = true;
                    break;
                }
                if (itimediff(sn, seg.sn) > 0) {
                    findPos = true;
                    break;
                }
            }
        }

        if (repeat) {
            newSeg.recycle(true);
        } else if (listItr == null) {
            rcvBuf.add(newSeg);
        } else {
            if (findPos) {
                listItr.next();
            }
            listItr.add(newSeg);
        }

        // move available data from rcv_buf -> rcv_queue
        moveRcvData(); // Invoke the method only if the segment is not repeat?
    }

    private void moveRcvData() {
        for (Iterator<Segment> itr = rcvBufItr.rewind(); itr.hasNext(); ) {
            Segment seg = itr.next();
            if (seg.sn == rcvNxt && rcvQueue.size() < rcvWnd) {
                itr.remove();
                rcvQueue.add(seg);
                rcvNxt++;
            } else {
                break;
            }
        }
    }

    private int input1(ByteBuf data) {
        long oldSndUna = sndUna;
        long maxack = 0;
        boolean flag = false;

        if (log.isDebugEnabled()) {
            log.debug("{} [RI] {} bytes", this, data.readableBytes());
        }

        if (data == null || data.readableBytes() < IKCP_OVERHEAD) {
            return -1;
        }

        while (true) {
            int conv, len, wnd;
            long ts, sn, una;
            byte cmd;
            short frg;
            Segment seg;

            if (data.readableBytes() < IKCP_OVERHEAD) {
                break;
            }

            conv = data.readIntLE();
            if (conv != this.conv && !(this.conv == 0 && autoSetConv)) {
                return -4;
            }

            cmd = data.readByte();
            frg = data.readUnsignedByte();
            wnd = data.readUnsignedShortLE();
            ts = data.readUnsignedIntLE();
            sn = data.readUnsignedIntLE();
            una = data.readUnsignedIntLE();
            len = data.readIntLE();

            if (data.readableBytes() < len || len < 0) {
                return -2;
            }

            if (cmd != IKCP_CMD_PUSH && cmd != IKCP_CMD_ACK && cmd != IKCP_CMD_WASK && cmd != IKCP_CMD_WINS) {
                return -3;
            }

            if (this.conv == 0 && autoSetConv) { // automatically set conv
                this.conv = conv;
            }

            this.rmtWnd = wnd;
            parseUna(una);
            shrinkBuf();

            boolean readed = false;
            long uintCurrent = long2Uint(current);
            switch (cmd) {
                case IKCP_CMD_ACK: {
                    int rtt = itimediff(uintCurrent, ts);
                    if (rtt >= 0) {
                        updateAck(rtt);
                    }
                    parseAck(sn);
                    shrinkBuf();
                    if (!flag) {
                        flag = true;
                        maxack = sn;
                    } else {
                        if (itimediff(sn, maxack) > 0) {
                            maxack = sn;
                        }
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("{} input ack: sn={}, rtt={}, rto={}", this, sn, rtt, rxRto);
                    }
                    break;
                }
                case IKCP_CMD_PUSH: {
                    if (itimediff(sn, rcvNxt + rcvWnd) < 0) {
                        ackPush(sn, ts);
                        if (itimediff(sn, rcvNxt) >= 0) {
                            seg = Segment.createSegment(byteBufAllocator, len);
                            seg.conv = conv;
                            seg.cmd = cmd;
                            seg.frg = frg;
                            seg.wnd = wnd;
                            seg.ts = ts;
                            seg.sn = sn;
                            seg.una = una;

                            if (len > 0) {
                                seg.data.writeBytes(data, len);
                                readed = true;
                            }

                            parseData(seg);
                        }
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("{} input push: sn={}, una={}, ts={}", this, sn, una, ts);
                    }
                    break;
                }
                case IKCP_CMD_WASK: {
                    // ready to send back IKCP_CMD_WINS in ikcp_flush
                    // tell remote my window size
                    probe |= IKCP_ASK_TELL;
                    if (log.isDebugEnabled()) {
                        log.debug("{} input ask", this);
                    }
                    break;
                }
                case IKCP_CMD_WINS: {
                    // do nothing
                    if (log.isDebugEnabled()) {
                        log.debug("{} input tell: {}", this, wnd);
                    }
                    break;
                }
                default:
                    return -3;
            }

            if (!readed) {
                data.skipBytes(len);
            }
        }

        if (flag) {
            parseFastack(maxack);
        }

        if (itimediff(sndUna, oldSndUna) > 0) {
            if (cwnd < rmtWnd) {
                int mss = this.mss;
                if (cwnd < ssthresh) {
                    cwnd++;
                    incr += mss;
                } else {
                    if (incr < mss) {
                        incr = mss;
                    }
                    incr += (mss * mss) / incr + (mss / 16);
                    if ((cwnd + 1) * mss <= incr) {
                        cwnd++;
                    }
                }
                if (cwnd > rmtWnd) {
                    cwnd = rmtWnd;
                    incr = rmtWnd * mss;
                }
            }
        }

        return 0;
    }

    public int input(ByteBuf data) {
        long oldSndUna = sndUna;
        long maxack = 0;
        boolean flag = false;

        if (log.isDebugEnabled()) {
            log.debug("{} [RI] {} bytes", this, data.readableBytes());
        }

        if (data == null || data.readableBytes() < IKCP_OVERHEAD) {
            return -1;
        }

        while (true) {
            int conv, len, wnd;
            long ts, sn, una;
            byte cmd;
            short frg;
            Segment seg;

            if (data.readableBytes() < IKCP_OVERHEAD) {
                break;
            }

            conv = data.readIntLE();
            if (conv != this.conv && !(this.conv == 0 && autoSetConv)) {
                // ==================START
                // NOTE: this code slice is not part of the standard kcp impl
                // NOTE: there's a udp-to-tcp wrapper impl called rsock
                // NOTE: and it might add extra data into the udp packet
                // NOTE: we should remove those packets
                // NOTE: the packet looks like: (BE)
                // f268b10bd0083eed1700d8095cfb738a4008070000004fec855e0000000000
                // f268b10bd0083eed170089aac51cc4d6d8a510000000a5e9895e0000000000
                // f268b10bd0083eed1700b6bc82180c30f1290101000019e719370000000000
                // f268b10bd0083eed1700b6bc82180c30f129f200000083e517370000000000
                // f268b10bd0083eed1700b6bc82180c30f129e400000045e318370000000000
                // f268b10bd0083eed1700b6bc82180c30f129d9000000b6db1a370000000000
                // so the first 10 bytes are the same, and last 5 bytes are 0, total 31 bytes
                Logger.info(LogType.INVALID_EXTERNAL_DATA, "invalid kcp conv, try to recover");

                // length
                if (data.readableBytes() < (31 - 4/*4 bytes already read*/)) {
                    Logger.error(LogType.INVALID_EXTERNAL_DATA, "readable bytes = " + data.readableBytes() + " < " + (31 - 4));
                    return -4;
                }

                byte[] arr = Utils.allocateByteArrayInitZero(31);
                arr[0] = (byte) (conv & 0xff);
                arr[1] = (byte) ((conv >> 8) & 0xff);
                arr[2] = (byte) ((conv >> 16) & 0xff);
                arr[3] = (byte) ((conv >> 24) & 0xff);
                for (int i = 4; i < 31; ++i) {
                    arr[i] = data.readByte();
                }
                Logger.info(LogType.INVALID_EXTERNAL_DATA, "the received packet is:");
                Logger.printBytes(arr);

                // first 4 bytes: (conv is LE, so make this also LE)
                if (conv != ((0xf2) | (0x68 << 8) | (0xb1 << 16) | (0x0b << 24))) {
                    Logger.error(LogType.INVALID_EXTERNAL_DATA, "conv = " + conv + " != 0xf268b10b");
                    return -4;
                }
                // 6 bytes after first 4:
                {
                    byte a = arr[4];
                    byte b = arr[5];
                    byte c = arr[6];
                    byte d = arr[7];
                    byte e = arr[8];
                    byte f = arr[9];
                    if (a != ((byte) 0xd0) || b != ((byte) 0x08) || c != ((byte) 0x3e) || d != ((byte) 0xed) || e != ((byte) 0x17) || f != ((byte) 0x00)) {
                        Logger.error(LogType.INVALID_EXTERNAL_DATA, "6 bytes are " + a + "," + b + "," + c + "," + d + "," + e + "," + f + ", not 0xd0083eed1700");
                        return -4;
                    }
                }
                // last 5 bytes
                {
                    byte a = arr[26];
                    byte b = arr[27];
                    byte c = arr[28];
                    byte d = arr[29];
                    byte e = arr[30];
                    if (a != 0 || b != 0 || c != 0 || d != 0 || e != 0) {
                        Logger.error(LogType.INVALID_EXTERNAL_DATA, "last 5 bytes are " + a + "," + b + "," + c + "," + d + "," + e + " not all 0");
                        return -4;
                    }
                }
                // return -4;
                Logger.info(LogType.INVALID_EXTERNAL_DATA, "recognized rsock magic packet");
                continue;
                // ==================END
            }

            cmd = data.readByte();
            frg = data.readUnsignedByte();
            wnd = data.readUnsignedShortLE();
            ts = data.readUnsignedIntLE();
            sn = data.readUnsignedIntLE();
            una = data.readUnsignedIntLE();
            len = data.readIntLE();

            if (data.readableBytes() < len || len < 0) {
                return -2;
            }

            if (cmd != IKCP_CMD_PUSH && cmd != IKCP_CMD_ACK && cmd != IKCP_CMD_WASK && cmd != IKCP_CMD_WINS) {
                return -3;
            }

            if (this.conv == 0 && autoSetConv) { // automatically set conv
                this.conv = conv;
            }

            this.rmtWnd = wnd;
            parseUna(una);
            shrinkBuf();

            boolean readed = false;
            long uintCurrent = long2Uint(current);
            switch (cmd) {
                case IKCP_CMD_ACK: {
                    int rtt = itimediff(uintCurrent, ts);
                    if (rtt >= 0) {
                        updateAck(rtt);
                    }
                    parseAck(sn);
                    shrinkBuf();
                    if (!flag) {
                        flag = true;
                        maxack = sn;
                    } else {
                        if (itimediff(sn, maxack) > 0) {
                            maxack = sn;
                        }
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("{} input ack: sn={}, rtt={}, rto={}", this, sn, rtt, rxRto);
                    }
                    break;
                }
                case IKCP_CMD_PUSH: {
                    if (itimediff(sn, rcvNxt + rcvWnd) < 0) {
                        ackPush(sn, ts);
                        if (itimediff(sn, rcvNxt) >= 0) {
                            if (len > 0) {
                                seg = Segment.createSegment(data.readRetainedSlice(len));
                                readed = true;
                            } else {
                                seg = Segment.createSegment(byteBufAllocator, 0);
                            }
                            seg.conv = conv;
                            seg.cmd = cmd;
                            seg.frg = frg;
                            seg.wnd = wnd;
                            seg.ts = ts;
                            seg.sn = sn;
                            seg.una = una;

                            parseData(seg);
                        }
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("{} input push: sn={}, una={}, ts={}", this, sn, una, ts);
                    }
                    break;
                }
                case IKCP_CMD_WASK: {
                    // ready to send back IKCP_CMD_WINS in ikcp_flush
                    // tell remote my window size
                    probe |= IKCP_ASK_TELL;
                    if (log.isDebugEnabled()) {
                        log.debug("{} input ask", this);
                    }
                    break;
                }
                case IKCP_CMD_WINS: {
                    // do nothing
                    if (log.isDebugEnabled()) {
                        log.debug("{} input tell: {}", this, wnd);
                    }
                    break;
                }
                default:
                    return -3;
            }

            if (!readed) {
                data.skipBytes(len);
            }
        }

        if (flag) {
            parseFastack(maxack);
        }

        if (itimediff(sndUna, oldSndUna) > 0) {
            if (cwnd < rmtWnd) {
                int mss = this.mss;
                if (cwnd < ssthresh) {
                    cwnd++;
                    incr += mss;
                } else {
                    if (incr < mss) {
                        incr = mss;
                    }
                    incr += (mss * mss) / incr + (mss / 16);
                    if ((cwnd + 1) * mss <= incr) {
                        cwnd++;
                    }
                }
                if (cwnd > rmtWnd) {
                    cwnd = rmtWnd;
                    incr = rmtWnd * mss;
                }
            }
        }

        return 0;
    }

    private int wndUnused() {
        if (rcvQueue.size() < rcvWnd) {
            return rcvWnd - rcvQueue.size();
        }
        return 0;
    }

    /**
     * ikcp_flush
     */
    private void flush() {
        long current = this.current;
        long uintCurrent = long2Uint(current);

        // 'ikcp_update' haven't been called.
        if (!updated) {
            return;
        }

        Segment seg = Segment.createSegment(byteBufAllocator, 0);
        seg.conv = conv;
        seg.cmd = IKCP_CMD_ACK;
        seg.frg = 0;
        seg.wnd = wndUnused();
        seg.una = rcvNxt;
        seg.sn = 0;
        seg.ts = 0;

        ByteBuf buffer = createFlushByteBuf();

        // flush acknowledges
        int count = ackcount;
        for (int i = 0; i < count; i++) {
            if (buffer.readableBytes() + IKCP_OVERHEAD > mtu) {
                output(buffer, this);
                buffer = createFlushByteBuf();
            }
            seg.sn = int2Uint(acklist[i * 2]);
            seg.ts = int2Uint(acklist[i * 2 + 1]);
            encodeSeg(buffer, seg);
            if (log.isDebugEnabled()) {
                log.debug("{} flush ack: sn={}, ts={}", this, seg.sn, seg.ts);
            }
        }

        ackcount = 0;

        // probe window size (if remote window size equals zero)
        if (rmtWnd == 0) {
            if (probeWait == 0) {
                probeWait = IKCP_PROBE_INIT;
                tsProbe = current + probeWait;
            } else {
                if (itimediff(current, tsProbe) >= 0) {
                    if (probeWait < IKCP_PROBE_INIT) {
                        probeWait = IKCP_PROBE_INIT;
                    }
                    probeWait += probeWait / 2;
                    if (probeWait > IKCP_PROBE_LIMIT) {
                        probeWait = IKCP_PROBE_LIMIT;
                    }
                    tsProbe = current + probeWait;
                    probe |= IKCP_ASK_SEND;
                }
            }
        } else {
            tsProbe = 0;
            probeWait = 0;
        }

        // flush window probing commands
        if ((probe & IKCP_ASK_SEND) != 0) {
            seg.cmd = IKCP_CMD_WASK;
            if (buffer.readableBytes() + IKCP_OVERHEAD > mtu) {
                output(buffer, this);
                buffer = createFlushByteBuf();
            }
            encodeSeg(buffer, seg);
            if (log.isDebugEnabled()) {
                log.debug("{} flush ask", this);
            }
        }

        // flush window probing commands
        if ((probe & IKCP_ASK_TELL) != 0) {
            seg.cmd = IKCP_CMD_WINS;
            if (buffer.readableBytes() + IKCP_OVERHEAD > mtu) {
                output(buffer, this);
                buffer = createFlushByteBuf();
            }
            encodeSeg(buffer, seg);
            if (log.isDebugEnabled()) {
                log.debug("{} flush tell: wnd={}", this, seg.wnd);
            }
        }

        probe = 0;

        // calculate window size
        int cwnd0 = Math.min(sndWnd, rmtWnd);
        if (!nocwnd) {
            cwnd0 = Math.min(this.cwnd, cwnd0);
        }

        // move data from snd_queue to snd_buf
        while (itimediff(sndNxt, sndUna + cwnd0) < 0) {
            Segment newSeg = sndQueue.poll();
            if (newSeg == null) {
                break;
            }

            sndBuf.add(newSeg);

            newSeg.conv = conv;
            newSeg.cmd = IKCP_CMD_PUSH;
            newSeg.wnd = seg.wnd;
            newSeg.ts = uintCurrent;
            newSeg.sn = sndNxt++;
            newSeg.una = rcvNxt;
            newSeg.resendts = current;
            newSeg.rto = rxRto;
            newSeg.fastack = 0;
            newSeg.xmit = 0;
        }

        // calculate resent
        int resent = fastresend > 0 ? fastresend : Integer.MAX_VALUE;
        int rtomin = nodelay ? 0 : (rxRto >> 3);

        // flush data segments
        int change = 0;
        boolean lost = false;
        for (Iterator<Segment> itr = sndBufItr.rewind(); itr.hasNext(); ) {
            Segment segment = itr.next();
            boolean needsend = false;
            if (segment.xmit == 0) {
                needsend = true;
                incrXmit(segment);
                segment.rto = rxRto;
                segment.resendts = current + segment.rto + rtomin;
                if (log.isDebugEnabled()) {
                    log.debug("{} flush data: sn={}, resendts={}", this, segment.sn, (segment.resendts - current));
                }
            } else if (itimediff(current, segment.resendts) >= 0) {
                needsend = true;
                incrXmit(segment);
                xmit++;
                segment.fastack = 0;
                if (!nodelay) {
                    segment.rto += rxRto;
                } else {
                    segment.rto += rxRto / 2;
                }
                segment.resendts = current + segment.rto;
                lost = true;
                if (log.isDebugEnabled()) {
                    log.debug("{} resend. sn={}, xmit={}, resendts={}", this, segment.sn, segment.xmit, (segment
                        .resendts - current));
                }
            } else if (segment.fastack >= resent) {
                if (segment.xmit <= fastlimit || fastlimit <= 0) {
                    needsend = true;
                    incrXmit(segment);
                    segment.fastack = 0;
                    segment.resendts = current + segment.rto;
                    change++;
                    if (log.isDebugEnabled()) {
                        log.debug("{} fastresend. sn={}, xmit={}, resendts={} ", this, segment.sn, segment.xmit, (segment
                            .resendts - current));
                    }
                }
            }

            if (needsend) {
                segment.ts = uintCurrent;
                segment.wnd = seg.wnd;
                segment.una = rcvNxt;

                ByteBuf segData = segment.data;
                int segLen = segData.readableBytes();
                int need = IKCP_OVERHEAD + segLen;

                if (buffer.readableBytes() + need > mtu) {
                    output(buffer, this);
                    buffer = createFlushByteBuf();
                }

                encodeSeg(buffer, segment);

                if (segLen > 0) {
                    // don't increases data's readerIndex, because the data may be resend.
                    buffer.writeBytes(segData, segData.readerIndex(), segLen);
                }

                if (segment.xmit >= deadLink) {
                    state = -1;
                }
            }
        }

        // flash remain segments
        if (buffer.readableBytes() > 0) {
            output(buffer, this);
        } else {
            buffer.release();
        }

        seg.recycle(true);

        // update ssthresh
        if (change > 0) {
            int inflight = (int) (sndNxt - sndUna);
            ssthresh = inflight / 2;
            if (ssthresh < IKCP_THRESH_MIN) {
                ssthresh = IKCP_THRESH_MIN;
            }
            cwnd = ssthresh + resent;
            incr = cwnd * mss;
        }

        if (lost) {
            ssthresh = cwnd0 / 2;
            if (ssthresh < IKCP_THRESH_MIN) {
                ssthresh = IKCP_THRESH_MIN;
            }
            cwnd = 1;
            incr = mss;
        }

        if (cwnd < 1) {
            cwnd = 1;
            incr = mss;
        }
    }

    /**
     * update getState (call it repeatedly, every 10ms-100ms), or you can ask
     * ikcp_check when to call it again (without ikcp_input/_send calling).
     * 'current' - current timestamp in millisec.
     *
     * @param current
     */
    public void update(long current) {
        this.current = current;

        if (!updated) {
            updated = true;
            tsFlush = this.current;
        }

        int slap = itimediff(this.current, tsFlush);

        if (slap >= 10000 || slap < -10000) {
            tsFlush = this.current;
            slap = 0;
        }

        /*if (slap >= 0) {
            tsFlush += setInterval;
            if (itimediff(this.current, tsFlush) >= 0) {
                tsFlush = this.current + setInterval;
            }
            flush();
        }*/

        if (slap >= 0) {
            tsFlush += interval;
            if (itimediff(this.current, tsFlush) >= 0) {
                tsFlush = this.current + interval;
            }
        } else {
            tsFlush = this.current + interval;
        }
        flush();
    }

    /**
     * Determine when should you invoke ikcp_update:
     * returns when you should invoke ikcp_update in millisec, if there
     * is no ikcp_input/_send calling. you can call ikcp_update in that
     * time, instead of call update repeatly.
     * Important to reduce unnacessary ikcp_update invoking. use it to
     * schedule ikcp_update (eg. implementing an epoll-like mechanism,
     * or optimize ikcp_update when handling massive kcp connections)
     *
     * @param current
     * @return
     */
    public long check(long current) {
        if (!updated) {
            return current;
        }

        long tsFlush = this.tsFlush;
        int slap = itimediff(current, tsFlush);
        if (slap >= 10000 || slap < -10000) {
            tsFlush = current;
            slap = 0;
        }

        if (slap >= 0) {
            return current;
        }

        int tmFlush = itimediff(tsFlush, current);
        int tmPacket = Integer.MAX_VALUE;

        for (Iterator<Segment> itr = sndBufItr.rewind(); itr.hasNext(); ) {
            Segment seg = itr.next();
            int diff = itimediff(seg.resendts, current);
            if (diff <= 0) {
                return current;
            }
            if (diff < tmPacket) {
                tmPacket = diff;
            }
        }

        int minimal = tmPacket < tmFlush ? tmPacket : tmFlush;
        if (minimal >= interval) {
            minimal = interval;
        }

        return current + minimal;
    }

    public boolean checkFlush() {
        if (ackcount > 0) {
            return true;
        }
        if (probe != 0) {
            return true;
        }
        if (sndBuf.size() > 0) {
            return true;
        }
        if (sndQueue.size() > 0) {
            return true;
        }
        return false;
    }

    private void incrXmit(Segment seg) {
        if (++seg.xmit > maxSegXmit) {
            maxSegXmit = seg.xmit;
        }
    }

    public void logMonitor() {
        if (kcpMonitorLog.isDebugEnabled()) {
            kcpMonitorLog.debug("{} srtt={}, rttvar={}, rto={}, sndNxt={}, sndUna={}, rcvNxt={}, cwnd={}, xmit={}, " +
                    "maxSegXmit={}",
                this, rxSrtt, rxRttvar, rxRto, sndNxt, sndUna, rcvNxt, cwnd, xmit, maxSegXmit);
        }
    }

    public int getMtu() {
        return mtu;
    }

    public int setMtu(int mtu) {
        if (mtu < IKCP_OVERHEAD || mtu < 50) {
            return -1;
        }

        this.mtu = mtu;
        this.mss = mtu - IKCP_OVERHEAD;
        return 0;
    }

    public int getInterval() {
        return interval;
    }

    public int setInterval(int interval) {
        if (interval > 5000) {
            interval = 5000;
        } else if (interval < 10) {
            interval = 10;
        }
        this.interval = interval;

        return 0;
    }

    public int nodelay(boolean nodelay, int interval, int resend, boolean nc) {
        this.nodelay = nodelay;
        if (nodelay) {
            this.rxMinrto = IKCP_RTO_NDL;
        } else {
            this.rxMinrto = IKCP_RTO_MIN;
        }

        if (interval >= 0) {
            if (interval > 5000) {
                interval = 5000;
            } else if (interval < 10) {
                interval = 10;
            }
            this.interval = interval;
        }

        if (resend >= 0) {
            fastresend = resend;
        }

        this.nocwnd = nc;

        return 0;
    }

    public int wndsize(int sndWnd, int rcvWnd) {
        if (sndWnd > 0) {
            this.sndWnd = sndWnd;
        }
        if (rcvWnd > 0) {
            this.rcvWnd = rcvWnd;
        }

        return 0;
    }

    public int waitSnd() {
        return this.sndBuf.size() + this.sndQueue.size();
    }

    public int getConv() {
        return conv;
    }

    public void setConv(int conv) {
        this.conv = conv;
    }

    public Object getUser() {
        return user;
    }

    public void setUser(Object user) {
        this.user = user;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public boolean isNodelay() {
        return nodelay;
    }

    public void setNodelay(boolean nodelay) {
        this.nodelay = nodelay;
        if (nodelay) {
            this.rxMinrto = IKCP_RTO_NDL;
        } else {
            this.rxMinrto = IKCP_RTO_MIN;
        }
    }

    public int getFastresend() {
        return fastresend;
    }

    public void setFastresend(int fastresend) {
        this.fastresend = fastresend;
    }

    public int getFastlimit() {
        return fastlimit;
    }

    public void setFastlimit(int fastlimit) {
        this.fastlimit = fastlimit;
    }

    public boolean isNocwnd() {
        return nocwnd;
    }

    public void setNocwnd(boolean nocwnd) {
        this.nocwnd = nocwnd;
    }

    public int getRxMinrto() {
        return rxMinrto;
    }

    public void setRxMinrto(int rxMinrto) {
        this.rxMinrto = rxMinrto;
    }

    public int getRcvWnd() {
        return rcvWnd;
    }

    public void setRcvWnd(int rcvWnd) {
        this.rcvWnd = rcvWnd;
    }

    public int getSndWnd() {
        return sndWnd;
    }

    public void setSndWnd(int sndWnd) {
        this.sndWnd = sndWnd;
    }

    public boolean isStream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }

    public int getDeadLink() {
        return deadLink;
    }

    public void setDeadLink(int deadLink) {
        this.deadLink = deadLink;
    }

    public void setByteBufAllocator(ByteBufAllocator byteBufAllocator) {
        this.byteBufAllocator = byteBufAllocator;
    }

    public boolean isAutoSetConv() {
        return autoSetConv;
    }

    public void setAutoSetConv(boolean autoSetConv) {
        this.autoSetConv = autoSetConv;
    }

    @Override
    public String toString() {
        return "Kcp(" +
            "conv=" + conv +
            ')';
    }

}
