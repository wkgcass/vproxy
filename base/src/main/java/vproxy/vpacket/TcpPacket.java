package vproxy.vpacket;

import vproxy.base.util.ByteArray;
import vproxy.base.util.Consts;
import vproxy.base.util.Utils;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class TcpPacket extends AbstractPacket {
    private int srcPort;
    private int dstPort;
    private long seqNum;
    private long ackNum;
    private int dataOffset;
    private int flags;
    private int window;
    private int checksum;
    private int urgentPointer;
    private List<TcpOption> options = new LinkedList<>();
    private ByteArray data;

    public int getSrcPort() {
        return srcPort;
    }

    public void setSrcPort(int srcPort) {
        clearRawPacket();
        this.srcPort = srcPort;
    }

    public int getDstPort() {
        return dstPort;
    }

    public void setDstPort(int dstPort) {
        clearRawPacket();
        this.dstPort = dstPort;
    }

    public long getSeqNum() {
        return seqNum;
    }

    public void setSeqNum(long seqNum) {
        clearRawPacket();
        this.seqNum = seqNum;
    }

    public long getAckNum() {
        return ackNum;
    }

    public void setAckNum(long ackNum) {
        clearRawPacket();
        this.ackNum = ackNum;
    }

    public int getDataOffset() {
        return dataOffset;
    }

    public void setDataOffset(int dataOffset) {
        clearRawPacket();
        this.dataOffset = dataOffset;
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        clearRawPacket();
        this.flags = flags;
    }

    public int getWindow() {
        return window;
    }

    public void setWindow(int window) {
        clearRawPacket();
        this.window = window;
    }

    public int getChecksum() {
        return checksum;
    }

    public void setChecksum(int checksum) {
        clearRawPacket();
        this.checksum = checksum;
    }

    public int getUrgentPointer() {
        return urgentPointer;
    }

    public void setUrgentPointer(int urgentPointer) {
        clearRawPacket();
        this.urgentPointer = urgentPointer;
    }

    public List<TcpOption> getOptions() {
        return options;
    }

    public void setOptions(List<TcpOption> options) {
        clearRawPacket();
        this.options = options;
    }

    public ByteArray getData() {
        return data;
    }

    public void setData(ByteArray data) {
        clearRawPacket();
        this.data = data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TcpPacket tcpPacket = (TcpPacket) o;
        return srcPort == tcpPacket.srcPort &&
            dstPort == tcpPacket.dstPort &&
            seqNum == tcpPacket.seqNum &&
            ackNum == tcpPacket.ackNum &&
            dataOffset == tcpPacket.dataOffset &&
            flags == tcpPacket.flags &&
            window == tcpPacket.window &&
            checksum == tcpPacket.checksum &&
            urgentPointer == tcpPacket.urgentPointer &&
            Objects.equals(options, tcpPacket.options) &&
            Objects.equals(data, tcpPacket.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(srcPort, dstPort, seqNum, ackNum, dataOffset, flags, window, checksum, urgentPointer, options, data);
    }

    @Override
    public String toString() {
        return "TcpPacket{" +
            "srcPort=" + srcPort +
            ", dstPort=" + dstPort +
            ", seqNum=" + seqNum +
            ", ackNum=" + ackNum +
            ", dataOffset=" + dataOffset +
            ", flags=" + flags +
            ", window=" + window +
            ", checksum=" + checksum +
            ", urgentPointer=" + urgentPointer +
            ", options=" + options +
            ", data=" + data +
            '}';
    }

    @Override
    public String from(ByteArray bytes) {
        if (bytes.length() < 20) {
            return "input packet length too short for a tcp packet";
        }

        srcPort = bytes.uint16(0);
        dstPort = bytes.uint16(2);
        seqNum = bytes.uint32(4);
        ackNum = bytes.uint32(8);
        var dataOffsetReservedFlags = bytes.uint16(12);
        dataOffset = ((dataOffsetReservedFlags >> 12) & 0xf) * 4;
        flags = (dataOffsetReservedFlags & 0b0011_1111);
        window = bytes.uint16(14);
        checksum = bytes.uint16(16);
        urgentPointer = bytes.uint16(18);

        if (dataOffset > bytes.length()) {
            return "dataOffset too big";
        }

        if (bytes.length() > dataOffset) {
            data = bytes.sub(dataOffset, bytes.length() - dataOffset);
        } else {
            data = ByteArray.allocate(0);
        }

        if (dataOffset > 20) {
            // parse tcp options
            int off = 20;
            while (off < dataOffset) {
                byte kind = bytes.get(off);
                if (TcpOption.CASE_1_OPTION_KINDS[kind]) {
                    var opt = new TcpOption();
                    var err = opt.from(ByteArray.from(new byte[]{kind}));
                    if (err != null) {
                        return err;
                    }
                    opt.recordParent(this);
                    options.add(opt);
                    off += 1;
                    if (opt.kind == Consts.TCP_OPTION_END) {
                        break;
                    }
                } else {
                    if (off + 1 >= dataOffset) {
                        return "invalid tcp option, reaches dataOffset";
                    }
                    int len = bytes.uint8(off + 1);
                    if (off + len > dataOffset) {
                        return "invalid tcp option, length is too long";
                    }
                    var opt = new TcpOption();
                    var err = opt.from(bytes.sub(off, len));
                    if (err != null) {
                        return err;
                    }
                    opt.recordParent(this);
                    options.add(opt);
                    off += len;
                }
            }
        }

        raw = bytes;
        return null;
    }

    public boolean isSyn() {
        return (flags & Consts.TCP_FLAGS_SYN) == Consts.TCP_FLAGS_SYN;
    }

    public boolean isAck() {
        return (flags & Consts.TCP_FLAGS_ACK) == Consts.TCP_FLAGS_ACK;
    }

    public boolean isRst() {
        return (flags & Consts.TCP_FLAGS_RST) == Consts.TCP_FLAGS_RST;
    }

    public boolean isPsh() {
        return (flags & Consts.TCP_FLAGS_PSH) == Consts.TCP_FLAGS_PSH || data.length() > 0;
    }

    public boolean isFin() {
        return (flags & Consts.TCP_FLAGS_FIN) == Consts.TCP_FLAGS_FIN;
    }

    @Override
    protected ByteArray buildPacket() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void updateChecksum() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String description() {
        return "tcp"
            + ",flags=" + Integer.toBinaryString(flags)
            + ",tp_src=" + (srcPort == 0 ? "not-parsed-yet" : srcPort)
            + ",tp_dst=" + (dstPort == 0 ? "not-parsed-yet" : dstPort)
            + ",data=" + (data == null ? 0 : data.length());
    }

    private ByteArray buildCommonPart() {
        ByteArray base = ByteArray.allocate(20);
        base.int16(0, srcPort);
        base.int16(2, dstPort);
        base.int32(4, (int) seqNum);
        base.int32(8, (int) ackNum);
        // build dataOffset + reserved + flags later
        // we need to calculate the dataOffset after tcp options are generated
        base.int16(14, window);
        // build checksum in later functions
        base.int16(18, urgentPointer);

        // build options
        ByteArray optBytes = null;
        if (options != null && !options.isEmpty()) {
            if (options.get(options.size() - 1).kind != Consts.TCP_OPTION_END) {
                var end = new TcpOption();
                end.kind = Consts.TCP_OPTION_END;
                options.add(end);
            }
            for (var o : options) {
                var b = o.getRawPacket();
                if (optBytes == null) {
                    optBytes = b;
                } else {
                    optBytes = optBytes.concat(b);
                }
            }
        }
        if (optBytes != null) {
            base = base.concat(optBytes);
        }

        // build data offset and padding
        int padding = 0;
        int off = 20;
        if (optBytes != null) {
            int len = optBytes.length();
            int mod = len % 4;
            if (mod != 0) {
                padding = 4 - mod;
            }
            off += len + padding;
        }
        dataOffset = off;
        int dataOffsetReservedFlags = ((dataOffset / 4) << 12) | flags;
        base.int16(12, dataOffsetReservedFlags);

        // add padding
        if (padding != 0) {
            base = base.concat(ByteArray.allocate(padding));
        }

        if (data == null || data.length() == 0) {
            return base;
        } else {
            return base.concat(data);
        }
    }

    public ByteArray buildIPv4TcpPacket(Ipv4Packet ipv4) {
        var common = buildCommonPart();

        var pseudo = Utils.buildPseudoIPv4Header(ipv4, Consts.IP_PROTOCOL_TCP, common.length());
        var toCalculate = pseudo.concat(common);
        checksum = Utils.calculateChecksum(toCalculate, toCalculate.length());

        // build checksum
        common.int16(16, checksum);

        // done
        this.raw = common;
        return common;
    }

    protected void updateChecksumWithIPv4(Ipv4Packet ipv4) {
        raw.int16(16, 0);
        var pseudo = Utils.buildPseudoIPv4Header(ipv4, Consts.IP_PROTOCOL_TCP, raw.length());
        var toCalculate = pseudo.concat(raw);
        var cksum = Utils.calculateChecksum(toCalculate, toCalculate.length());

        checksum = cksum;
        raw.int16(16, cksum);
    }

    public ByteArray buildIPv6TcpPacket(Ipv6Packet ipv6) {
        var common = buildCommonPart();

        var pseudo = Utils.buildPseudoIPv6Header(ipv6, Consts.IP_PROTOCOL_TCP, common.length());
        var toCalculate = pseudo.concat(common);
        checksum = Utils.calculateChecksum(toCalculate, toCalculate.length());

        // build checksum
        common.int16(16, checksum);

        // done
        this.raw = common;
        return common;
    }

    protected void updateChecksumWithIPv6(Ipv6Packet ipv6) {
        raw.int16(16, 0);
        var pseudo = Utils.buildPseudoIPv6Header(ipv6, Consts.IP_PROTOCOL_TCP, raw.length());
        var toCalculate = pseudo.concat(raw);
        var cksum = Utils.calculateChecksum(toCalculate, toCalculate.length());

        checksum = cksum;
        raw.int16(16, cksum);
    }

    public static class TcpOption extends AbstractPacket {
        // Case 1:  A single octet of option-kind.
        public static final boolean[] CASE_1_OPTION_KINDS = new boolean[256];

        static {
            for (byte b : List.of(Consts.TCP_OPTION_END, Consts.TCP_OPTION_NOP)) {
                CASE_1_OPTION_KINDS[b] = true;
            }
        }

        private byte kind;
        private int length;
        private ByteArray data;

        public byte getKind() {
            return kind;
        }

        public void setKind(byte kind) {
            clearRawPacket();
            this.kind = kind;
        }

        public int getLength() {
            return length;
        }

        public void setLength(int length) {
            clearRawPacket();
            this.length = length;
        }

        public ByteArray getData() {
            return data;
        }

        public void setData(ByteArray data) {
            if (CASE_1_OPTION_KINDS[kind] || data.length() != length - 2) {
                clearRawPacket();
            } else if (raw != null) {
                clearChecksum();
                for (int i = 0; i < data.length(); ++i) {
                    raw.set(2 + i, data.get(i));
                }
            }
            this.data = data;
        }

        @Override
        public String toString() {
            return "TcpOption{" +
                "kind=" + kind +
                ", length=" + length +
                ", data=" + data +
                '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TcpOption tcpOption = (TcpOption) o;
            return kind == tcpOption.kind &&
                length == tcpOption.length &&
                Objects.equals(data, tcpOption.data);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, length, data);
        }

        @Override
        public String from(ByteArray bytes) {
            kind = bytes.get(0);
            if (CASE_1_OPTION_KINDS[kind]) {
                return null;
            }
            length = bytes.uint8(1);
            if (bytes.length() > 2) {
                data = bytes.sub(2, bytes.length() - 2);
            } else {
                data = ByteArray.allocate(0);
            }
            var err = check();
            if (err != null) {
                return err;
            }

            raw = bytes;
            return null;
        }

        private String check() {
            switch (kind) {
                case Consts.TCP_OPTION_WINDOW_SCALE:
                    if (length != 3) {
                        return "invalid tcp option length for kind=window_scale";
                    }
                    break;
                case Consts.TCP_OPTION_MSS:
                    if (length != 4) {
                        return "invalid tcp option length for kind=mss";
                    }
                    break;
            }
            return null;
        }

        @Override
        protected ByteArray buildPacket() {
            if (CASE_1_OPTION_KINDS[kind]) {
                return ByteArray.from(new byte[]{kind});
            }
            if (data.length() == 0) {
                length = 2;
            } else {
                length = 2 + data.length();
            }
            var o = ByteArray.allocate(2);
            o.set(0, kind);
            o.set(1, (byte) length);
            if (data == null) {
                return o;
            } else {
                return o.concat(data);
            }
        }

        @Override
        protected void updateChecksum() {
            // do nothing
        }

        @Override
        public String description() {
            throw new UnsupportedOperationException();
        }
    }
}
