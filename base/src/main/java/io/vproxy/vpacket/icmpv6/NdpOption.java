package io.vproxy.vpacket.icmpv6;

import io.vproxy.base.util.ByteArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An option carried by an ICMPv6 neighbor discovery message (RFC 4861).
 * Wire format: type(1) + length(1, in units of 8 bytes including these two) + data(length * 8 - 2).
 * Option types are defined in {@link io.vproxy.base.util.Consts} as ICMPv6_OPTION_TYPE_xxx.
 */
public class NdpOption {
    private int type;
    private ByteArray data;

    public NdpOption() {
    }

    public NdpOption(int type, ByteArray data) {
        this.type = type;
        this.data = data;
    }

    /**
     * Parses all options starting from {@code off}.
     * Stops at the end of the buffer or at the first malformed option.
     */
    public static List<NdpOption> parseAll(ByteArray bytes, int off) {
        var list = new ArrayList<NdpOption>();
        while (off + 2 <= bytes.length()) {
            int type = bytes.uint8(off);
            int len = bytes.uint8(off + 1) * 8;
            if (len == 0 || off + len > bytes.length()) {
                break; // malformed option, ignore the rest
            }
            list.add(new NdpOption(type, bytes.sub(off + 2, len - 2)));
            off += len;
        }
        return list;
    }

    public ByteArray toByteArray() {
        int unitLen = (data.length() + 2 + 7) / 8;
        int padLen = unitLen * 8 - 2 - data.length();
        return ByteArray.allocate(2).set(0, (byte) type).set(1, (byte) unitLen)
            .concat(data)
            .concat(ByteArray.allocate(padLen));
    }

    @Override
    public String toString() {
        return "NdpOption{" +
            "type=" + type +
            ", data=" + data +
            '}';
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public ByteArray getData() {
        return data;
    }

    public void setData(ByteArray data) {
        this.data = data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NdpOption that = (NdpOption) o;
        return type == that.type && Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, data);
    }
}
