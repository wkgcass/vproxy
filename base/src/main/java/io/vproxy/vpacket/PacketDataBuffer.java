package io.vproxy.vpacket;

import io.vproxy.base.util.ByteArray;

public class PacketDataBuffer {
    public ByteArray fullbuf;
    public int pktOff; // packet offset
    public int pad; // padding length after the end of the packet
    public ByteArray pktBuf; // sub buffer of buf

    // when true, a packet whose header-declared length (e.g. ipv4 totalLength, ipv6
    // payloadLength, tcp dataOffset, udp length) exceeds the captured bytes is NOT rejected:
    // the missing trailing bytes are synthesised from TRUNCATED_PAD_SEQ and parsing continues
    // (a warning is logged). this lets analysers read the header fields (5-tuple, seq/ack,
    // flags, and the on-wire payload length) off capture-snaplen-truncated frames whose
    // headers are intact but whose payload tail was cut. the synthesised bytes are fake; do
    // not trust payload content parsed under this flag.
    private boolean allowTruncatedPacket;

    /**
     * repeating byte sequence used to fill bytes that were declared by a header but missing
     * from a truncated capture: 0xFF 0xFE 0xFD 0xFC 0xFB 0xFA, then repeat.
     */
    public static final byte[] TRUNCATED_PAD_SEQ = new byte[]{
        (byte) 0xFF, (byte) 0xFE, (byte) 0xFD, (byte) 0xFC, (byte) 0xFB, (byte) 0xFA
    };

    // pre-rendered repeating pattern block (a whole number of cycles). ip/udp/tcp length fields
    // are 16-bit, so any single pad is <= 65535 < PAD_BLOCK_LEN and is filled with one arraycopy.
    private static final int PAD_BLOCK_LEN = 6 * 10923; // 65538, smallest multiple of 6 > 65535
    private static final byte[] PADDING_BLOCK = buildPaddingBlock();

    private static byte[] buildPaddingBlock() {
        byte[] b = new byte[PAD_BLOCK_LEN];
        for (int i = 0; i < PAD_BLOCK_LEN; i++) {
            b[i] = TRUNCATED_PAD_SEQ[i % TRUNCATED_PAD_SEQ.length];
        }
        return b;
    }

    public PacketDataBuffer(ByteArray fullbuf, int pktOff, int pad) {
        this.fullbuf = fullbuf;
        this.pktOff = pktOff;
        this.pad = pad;
        if (pktOff == 0 && pad == 0) {
            this.pktBuf = fullbuf;
        } else {
            this.pktBuf = fullbuf.sub(pktOff, fullbuf.length() - pktOff - pad);
        }
    }

    public PacketDataBuffer(ByteArray pktBuf) {
        if (pktBuf == null) {
            return;
        }
        this.fullbuf = pktBuf;
        this.pktOff = 0;
        this.pad = 0;
        this.pktBuf = pktBuf;
    }

    public void clearBuffers() {
        this.fullbuf = null;
        this.pktOff = 0;
        this.pad = 0;
        this.pktBuf = null;
    }

    public boolean allowTruncatedPacket() {
        return allowTruncatedPacket;
    }

    public void setAllowTruncatedPacket(boolean allowTruncatedPacket) {
        this.allowTruncatedPacket = allowTruncatedPacket;
    }

    public PacketDataBuffer sub(int off) {
        var n = new PacketDataBuffer(fullbuf, this.pktOff + off, pad);
        n.allowTruncatedPacket = this.allowTruncatedPacket;
        return n;
    }

    public PacketDataBuffer sub(int off, int len) {
        var n = new PacketDataBuffer(fullbuf, this.pktOff + off, fullbuf.length() - (this.pktOff + off) - len);
        n.allowTruncatedPacket = this.allowTruncatedPacket;
        return n;
    }

    /**
     * Return a buffer whose {@link #pktBuf} is at least {@code need} bytes long. If the current
     * pktBuf is already long enough, returns {@code this}. Otherwise builds a fresh standalone
     * buffer: a copy of the current pktBuf followed by {@link #TRUNCATED_PAD_SEQ} repeated until
     * length {@code need}. The {@link #allowTruncatedPacket()} flag is propagated to the result.
     * Used by packet parsers to recover from capture-snaplen truncation instead of rejecting the
     * frame.
     */
    public PacketDataBuffer padTo(int need) {
        ByteArray bytes = this.pktBuf;
        int have = bytes == null ? 0 : bytes.length();
        if (have >= need) {
            return this;
        }
        ByteArray padded = padToLength(bytes, need);
        var n = new PacketDataBuffer(padded);
        n.allowTruncatedPacket = this.allowTruncatedPacket;
        return n;
    }

    /**
     * Build a byte array of length {@code need}: the content of {@code bytes} (its first
     * {@code min(bytes.length(), need)} bytes) followed by {@link #TRUNCATED_PAD_SEQ} repeated
     * up to length {@code need}. Returns {@code bytes} unchanged if it is already at least
     * {@code need} long. The repeating padding is copied from a pre-rendered block, so a single
     * pad is O(1) arraycopies regardless of how many bytes are missing.
     */
    public static ByteArray padToLength(ByteArray bytes, int need) {
        int have = bytes == null ? 0 : bytes.length();
        if (have >= need) {
            return bytes;
        }
        byte[] arr = new byte[need];
        if (have > 0) {
            byte[] src = bytes.toJavaArray();
            System.arraycopy(src, 0, arr, 0, have);
        }
        // fill arr[have..need) with the repeating pattern, starting 0xFF at the first missing byte
        int padLen = need - have;
        int off = 0;
        while (off < padLen) {
            int n = Math.min(PAD_BLOCK_LEN, padLen - off);
            System.arraycopy(PADDING_BLOCK, 0, arr, have + off, n);
            off += n;
        }
        return ByteArray.from(arr);
    }
}
