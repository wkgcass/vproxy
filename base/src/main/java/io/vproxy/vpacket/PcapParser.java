package io.vproxy.vpacket;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;

import java.io.*;

public class PcapParser {
    private final File file;
    private final InputStream data;
    private byte[] buf = new byte[4096];
    private int state = 0;
    // 0 -> waiting for global header
    // 1 -> waiting for packet header and packet

    private PcapGlobalHeader globalHeader;

    public PcapParser(String filename) throws FileNotFoundException {
        this(new File(filename));
    }

    public PcapParser(File file) throws FileNotFoundException {
        this.file = file;
        data = new FileInputStream(file);
    }

    public PcapParser(InputStream data) {
        this.file = null;
        this.data = data;
    }

    // return null when eof, throw when error
    @SuppressWarnings("DuplicateThrows")
    public PcapPacket next() throws IllegalArgumentException, RuntimeException {
        while (true) {
            if (state == 0) {
                readGlobalHeader();
            } else {
                return readPacketHeaderAndPacket();
            }
        }
    }

    private void readGlobalHeader() {
        int len = 4 + 2 + 2 + 4 + 4 + 4 + 4;
        var buf = next(len);
        if (buf == null) {
            return;
        }
        if (buf.length() != len) {
            Logger.error(LogType.INVALID_EXTERNAL_DATA, "unable to read global header: cannot read " + len + " bytes from the stream");
            throw new IllegalArgumentException();
        }
        globalHeader = new PcapGlobalHeader(
            buf.int32(0),
            buf.uint16ReverseNetworkByteOrder(4),
            buf.uint16ReverseNetworkByteOrder(6),
            buf.int32ReverseNetworkByteOrder(8),
            buf.int32ReverseNetworkByteOrder(12),
            buf.int32ReverseNetworkByteOrder(16),
            buf.int32ReverseNetworkByteOrder(20)
        );
        if (globalHeader.snaplen > this.buf.length) {
            this.buf = new byte[globalHeader.snaplen];
        }
        state = 1;
    }

    private PcapPacket readPacketHeaderAndPacket() {
        var headerBuf = next(16);
        if (headerBuf == null) {
            return null;
        }
        if (headerBuf.length() != 16) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "incomplete packet header, probably tcpdump is interrupted");
            return null;
        }
        var pcapPacket = new PcapPacket(
            headerBuf.int32ReverseNetworkByteOrder(0),
            headerBuf.int32ReverseNetworkByteOrder(4),
            headerBuf.int32ReverseNetworkByteOrder(8),
            headerBuf.int32ReverseNetworkByteOrder(12)
        );
        var len = pcapPacket.getInclLen();
        var buf = next(len);
        if (buf == null) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "no packet data, probably tcpdump is interrupted");
            return null;
        }
        if (buf.length() < len) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "no packet data, probably tcpdump is interrupted, expecting " + len + ", got " + buf.length());
            return null;
        }
        var e = new EthernetPacket();
        try {
            e.from(new PacketDataBuffer(buf));
        } catch (Throwable t) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "invalid packet", t);
            return pcapPacket;
        }
        pcapPacket.setPacket(e);
        return pcapPacket;
    }

    private ByteArray next(int len) {
        if (len == 0) {
            throw new IndexOutOfBoundsException("unexpected len = 0");
        }
        if (len > buf.length) {
            throw new IndexOutOfBoundsException("unexpected len = " + len + " > buf.length = " + buf.length);
        }
        int n = 0;
        do {
            int r;
            try {
                r = data.read(buf, n, len - n);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (r == -1) {
                if (n == 0) {
                    return null; // eof
                } else {
                    break; // need to return the already read bytes
                }
            }
            n += r;
        } while (n < len);
        return ByteArray.from(buf).sub(0, n);
    }

    public PcapGlobalHeader getGlobalHeader() {
        if (globalHeader == null) {
            readGlobalHeader();
        }
        return globalHeader;
    }
}
