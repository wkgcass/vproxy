package io.vproxy.vpacket.tuples;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.Utils;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.MacAddress;

import java.util.Arrays;

public class PacketFullTuple {
    private final byte[] data;
    private final int hashCode;

    @SuppressWarnings("UnusedAssignment")
    public PacketFullTuple(int devin, MacAddress dlSrc, MacAddress dlDst, IP nwSrc, IP nwDst, int ipProto, int tpSrc, int tpDst) {
        byte[] data;
        if (nwSrc instanceof IPv4) {
            data = Utils.allocateByteArray(4 + 6 + 6 + 2 + 4 + 4 + 2 + 2 + 2);
        } else {
            data = Utils.allocateByteArray(4 + 6 + 6 + 2 + 16 + 16 + 2 + 2 + 2);
        }
        int offset = 0;
        data[offset++] = (byte) ((devin >> 24) & 0xff);
        data[offset++] = (byte) ((devin >> 16) & 0xff);
        data[offset++] = (byte) ((devin >> 8) & 0xff);
        data[offset++] = (byte) (devin & 0xff);
        System.arraycopy(dlSrc.bytes.toJavaArray(), 0, data, offset, 6);
        offset += 6;
        System.arraycopy(dlDst.bytes.toJavaArray(), 0, data, offset, 6);
        offset += 6;
        int etherType = nwSrc instanceof IPv4 ? Consts.ETHER_TYPE_IPv4 : Consts.ETHER_TYPE_IPv6;
        data[offset++] = (byte) ((etherType >> 8) & 0xff);
        data[offset++] = (byte) (etherType & 0xff);
        if (nwSrc instanceof IPv4) {
            System.arraycopy(nwSrc.bytes.toJavaArray(), 0, data, offset, 4);
            offset += 4;
            System.arraycopy(nwDst.bytes.toJavaArray(), 0, data, offset, 4);
            offset += 4;
        } else {
            System.arraycopy(nwSrc.bytes.toJavaArray(), 0, data, offset, 16);
            offset += 16;
            System.arraycopy(nwDst.bytes.toJavaArray(), 0, data, offset, 16);
            offset += 16;
        }
        data[offset++] = (byte) ((ipProto >> 8) & 0xff);
        data[offset++] = (byte) (ipProto & 0xff);
        data[offset++] = (byte) ((tpSrc >> 8) & 0xff);
        data[offset++] = (byte) (tpSrc & 0xff);
        data[offset++] = (byte) ((tpDst >> 8) & 0xff);
        data[offset++] = (byte) (tpDst & 0xff);

        this.data = data;
        this.hashCode = Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "PacketFullTuple:" + ByteArray.from(data).toHexString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PacketFullTuple that = (PacketFullTuple) o;
        return Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
