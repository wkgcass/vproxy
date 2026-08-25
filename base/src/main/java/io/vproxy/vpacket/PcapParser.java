package io.vproxy.vpacket;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PcapParser {
    private final InputStream data;
    private boolean copyPacket = true;
    // when true, frames whose header-declared length exceeds the captured bytes (snaplen-truncated
    // payloads) are padded with PacketDataBuffer.TRUNCATED_PAD_SEQ and parsed anyway, instead of
    // being downgraded to PacketBytes. opt-in for analysers that only need header fields.
    private boolean allowTruncatedPacket = false;
    private byte[] buf = new byte[4096];
    private int state = 0;
    // 0 -> waiting for global header
    // 1 -> waiting for packet header and packet

    private PcapGlobalHeader globalHeader;

    public PcapParser(String filename) throws FileNotFoundException {
        this(Path.of(filename));
    }

    public PcapParser(Path filepath) throws FileNotFoundException {
        this(filepath.toFile());
    }

    public PcapParser(File file) throws FileNotFoundException {
        data = new FileInputStream(file);
    }

    public PcapParser(InputStream data) {
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

    public List<PcapPacket> parseAll() throws RuntimeException {
        var result = new ArrayList<PcapPacket>();
        while (true) {
            var pkt = next();
            if (pkt == null) {
                break;
            }
            result.add(pkt);
        }
        return result;
    }

    public boolean isCopyPacket() {
        return copyPacket;
    }

    public void setCopyPacket(boolean copyPacket) {
        this.copyPacket = copyPacket;
    }

    public boolean isAllowTruncatedPacket() {
        return allowTruncatedPacket;
    }

    public void setAllowTruncatedPacket(boolean allowTruncatedPacket) {
        this.allowTruncatedPacket = allowTruncatedPacket;
    }

    private void readGlobalHeader() {
        int len = 4 + 2 + 2 + 4 + 4 + 4 + 4;
        var buf = next(len);
        if (buf == null) {
            Logger.error(LogType.INVALID_EXTERNAL_DATA, "unable to read global header: EOF");
            throw new IllegalArgumentException();
        }
        if (buf.length() != len) {
            Logger.error(LogType.INVALID_EXTERNAL_DATA, "unable to read global header: cannot read " + len + " bytes from the stream");
            throw new IllegalArgumentException();
        }
        globalHeader = new PcapGlobalHeader(
            buf.int32ReverseNetworkByteOrder(0),
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
        var len = pcapPacket.getCapLen();
        var buf = next(len);
        if (buf == null) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "no packet data, probably tcpdump is interrupted");
            return null;
        }
        if (buf.length() < len) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "no packet data, probably tcpdump is interrupted, expecting " + len + ", got " + buf.length());
            return null;
        }
        if (copyPacket) {
            buf = buf.copy();
        }
        var raw = new PacketDataBuffer(buf);
        raw.setAllowTruncatedPacket(allowTruncatedPacket);
        String err = null;
        if (globalHeader.dataLinkType == PcapGlobalHeader.LINKTYPE_ETHERNET) {
            // for null type, try to use ethernet anyway
            var e = new EthernetPacket();
            err = e.from(raw);
            pcapPacket.setPacket(e);
        } else if (globalHeader.dataLinkType == PcapGlobalHeader.LINKTYPE_LINUX_SLL) {
            var l = new LinuxCookedPacket();
            err = l.from(raw);
            pcapPacket.setPacket(l);
        } else if (globalHeader.dataLinkType == PcapGlobalHeader.LINKTYPE_LINUX_SLL2) {
            var l = new LinuxCookedV2Packet();
            err = l.from(raw);
            pcapPacket.setPacket(l);
        } else if (globalHeader.dataLinkType == PcapGlobalHeader.LINKTYPE_NULL) {
            var bsd = new BSDLoopbackEncapsulation();
            err = bsd.from(raw);
            pcapPacket.setPacket(bsd);
        } else {
            pcapPacket.setPacket(new PacketBytes(buf));
        }

        if (err != null) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "invalid packet: " + err);
            pcapPacket.setPacket(new PacketBytes(buf));
            return pcapPacket;
        }
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
