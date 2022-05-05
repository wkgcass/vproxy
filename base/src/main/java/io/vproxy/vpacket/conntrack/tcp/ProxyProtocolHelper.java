package io.vproxy.vpacket.conntrack.tcp;

import io.vproxy.base.util.ByteArray;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.IPv4;

// https://www.haproxy.org/download/1.8/doc/proxy-protocol.txt
public class ProxyProtocolHelper {
    private static final ByteArray prefix =
        ByteArray.fromHexString("0D0A0D0A000D0A515549540A").concat(
            ByteArray.fromHexString("21") // cmd: ver2 and proxy
        ).persist();

    public final IP srcIp;
    public final IP dstIp;
    public final int srcPort;
    public final int dstPort;

    public boolean isSent = false;

    public ProxyProtocolHelper(IP srcIp, IP dstIp, int srcPort, int dstPort) {
        this.srcIp = srcIp;
        this.dstIp = dstIp;
        this.srcPort = srcPort;
        this.dstPort = dstPort;
    }

    public int getV2HeaderLength() {
        if (srcIp instanceof IPv4) {
            return 28;
        } else {
            return 52;
        }
    }

    public ByteArray buildV2Header() {
        ByteArray ret;
        if (srcIp instanceof IPv4) {
            ret = ByteArray.allocate(1 + 2 + (4 + 2) * 2);
            ret.set(0, (byte) 0x11);
            ret.int16(1, 12);
            var srcIpBytes = srcIp.getAddress();
            for (int i = 0; i < srcIpBytes.length; ++i) {
                ret.set(3 + i, srcIpBytes[i]);
            }
            var dstIpBytes = dstIp.getAddress();
            for (int i = 0; i < dstIpBytes.length; ++i) {
                ret.set(7 + i, dstIpBytes[i]);
            }
            ret.int16(11, srcPort);
            ret.int16(13, dstPort);
        } else {
            ret = ByteArray.allocate(1 + 2 + (16 + 2) * 2);
            ret.set(0, (byte) 0x21);
            ret.int16(1, 36);
            var srcIpBytes = srcIp.getAddress();
            for (int i = 0; i < srcIpBytes.length; ++i) {
                ret.set(3 + i, srcIpBytes[i]);
            }
            var dstIpBytes = dstIp.getAddress();
            for (int i = 0; i < dstIpBytes.length; ++i) {
                ret.set(19 + i, dstIpBytes[i]);
            }
            ret.int16(35, srcPort);
            ret.int16(37, dstPort);
        }
        return prefix.concat(ret);
    }

    @Override
    public String toString() {
        return "ProxyProtocolHelper{" +
            ", src=" + new IPPort(srcIp, srcPort).formatToIPPortString() +
            ", dst=" + new IPPort(dstIp, dstPort).formatToIPPortString() +
            ", isSent=" + isSent +
            '}';
    }
}
