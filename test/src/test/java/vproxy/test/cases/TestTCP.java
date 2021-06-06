package vproxy.test.cases;

import org.junit.Test;
import vproxy.base.util.ByteArray;
import vproxy.vfd.IPPort;
import vproxy.vpacket.conntrack.tcp.Segment;
import vproxy.vpacket.conntrack.tcp.TcpEntry;
import vproxy.vpacket.conntrack.tcp.TcpState;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class TestTCP {
    private static final byte[] chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".getBytes();

    private ByteArray randomPayload(int httpBodyLen) {
        ByteArray body = ByteArray.allocate(httpBodyLen);
        Random r = new Random();
        for (int i = 0; i < httpBodyLen; ++i) {
            body.set(i, chars[r.nextInt(chars.length)]);
        }
        return ByteArray.from(("" +
            "POST / HTTP/1.1\r\n" +
            "Host: test.com\r\n" +
            "Content-Type: application/json\r\n" +
            "Content-Length: " + body.length() +
            "\r\n" +
            "").getBytes()).concat(body);
    }

    @Test
    public void send() {
        TcpEntry tcpEntry = new TcpEntry(
            null,
            new IPPort("12.34.56.78", 1234),
            new IPPort("98.76.54.32", 5678),
            12345);
        tcpEntry.setState(TcpState.ESTABLISHED);
        tcpEntry.sendingQueue.init(65535, 1360, 1);

        ByteArray bytes = randomPayload(16384);
        ByteBuffer buffer = ByteBuffer.wrap(bytes.toJavaArray());
        long seq = tcpEntry.sendingQueue.getLatestSeq();
        int n = tcpEntry.sendingQueue.apiWrite(buffer);
        assertEquals(bytes.length(), n);

        assertEquals(bytes.length(), tcpEntry.sendingQueue.getCurrentSize());
        assertEquals(seq + bytes.length(), tcpEntry.sendingQueue.getLatestSeq());

        var segments = tcpEntry.sendingQueue.fetch();
        int total = 0;
        long lastEndSeq = -1;
        ByteArray result = null;
        for (var s : segments) {
            total += s.data.length();
            if (lastEndSeq != -1) {
                assertEquals(lastEndSeq, s.seqBeginInclusive);
            }
            lastEndSeq = s.seqEndExclusive;
            assertEquals(s.seqBeginInclusive + s.data.length(), s.seqEndExclusive);
            if (result == null) {
                result = s.data;
            } else {
                result = result.concat(s.data);
            }
        }
        assertEquals(bytes.length(), total);
        assertEquals(bytes, result);
    }

    @Test
    public void sendAndAck() {
        TcpEntry tcpEntry = new TcpEntry(
            null,
            new IPPort("12.34.56.78", 1234),
            new IPPort("98.76.54.32", 5678),
            12345);
        tcpEntry.setState(TcpState.ESTABLISHED);
        tcpEntry.sendingQueue.init(128, 1360, 1);

        ByteArray bytes = randomPayload(32768);
        ByteBuffer buffer = ByteBuffer.wrap(bytes.toJavaArray());
        int n = tcpEntry.sendingQueue.apiWrite(buffer);
        assertEquals(bytes.length(), n);

        var segments = tcpEntry.sendingQueue.fetch();
        assertEquals(1, segments.size());
        var s = segments.get(0);
        assertEquals(128, s.data.length());
        var result = s.data;
        assertEquals(bytes.sub(0, 128), result);

        tcpEntry.sendingQueue.ack(s.seqEndExclusive, 65535);
        tcpEntry.sendingQueue.ack(s.seqEndExclusive, 65535);
        tcpEntry.sendingQueue.ack(s.seqEndExclusive, 65535); // one ack multiple times

        segments = tcpEntry.sendingQueue.fetch();
        for (var rr : segments) {
            result = result.concat(rr.data);
        }
        assertEquals(bytes, result);
    }

    @Test
    public void recv() {
        int seqInit = 12345;
        TcpEntry tcpEntry = new TcpEntry(
            null,
            new IPPort("12.34.56.78", 1234),
            new IPPort("98.76.54.32", 5678),
            seqInit);
        tcpEntry.setState(TcpState.ESTABLISHED);

        ByteArray bytes1 = randomPayload(1280);
        Segment s1 = new Segment(seqInit + 1, bytes1);
        tcpEntry.receivingQueue.store(s1);
        assertEquals(seqInit + 1 + bytes1.length(), tcpEntry.receivingQueue.getExpectingSeq());

        ByteArray bytes2 = randomPayload(1024);
        Segment s2 = new Segment(seqInit + 1 + bytes1.length(), bytes2);
        tcpEntry.receivingQueue.store(s2);
        assertEquals(seqInit + 1 + bytes1.length() + bytes2.length(), tcpEntry.receivingQueue.getExpectingSeq());

        ByteArray ret1 = tcpEntry.receivingQueue.apiRead(128);
        ByteArray ret2 = tcpEntry.receivingQueue.apiRead(1500);
        ByteArray ret3 = tcpEntry.receivingQueue.apiRead(Integer.MAX_VALUE);
        assertEquals(bytes1.concat(bytes2), ret1.concat(ret2).concat(ret3));
    }
}
