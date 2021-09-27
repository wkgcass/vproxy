package vproxy.vmirror;

import vproxy.base.util.ByteArray;
import vproxy.base.util.ByteBufferEx;
import vproxy.base.util.Consts;
import vproxy.base.util.Utils;
import vproxy.vfd.IP;
import vproxy.vfd.IPPort;
import vproxy.vfd.MacAddress;

import java.nio.ByteBuffer;

public class MirrorData {
    public final MirrorContext ctx;
    public final String origin;
    MacAddress macSrc = new MacAddress("00:00:00:00:00:00");
    MacAddress macDst = new MacAddress("ff:ff:ff:ff:ff:ff");
    IP ipSrc;
    IP ipDst;
    String transportLayerProtocol;
    int portSrc;
    int portDst;
    String applicationLayerProtocol;
    String meta;
    byte flags = Consts.TCP_FLAGS_PSH;
    ByteArray data;

    public MirrorData(MirrorContext ctx, String origin) {
        this.ctx = ctx;
        this.origin = origin;
    }

    public MirrorData setMacSrc(MacAddress macSrc) {
        this.macSrc = macSrc;
        return this;
    }

    public MirrorData setMacDst(MacAddress macDst) {
        this.macDst = macDst;
        return this;
    }

    public MirrorData setIpSrc(IP ipSrc) {
        this.ipSrc = ipSrc;
        return this;
    }

    public MirrorData setIpDst(IP ipDst) {
        this.ipDst = ipDst;
        return this;
    }

    public MirrorData setPortSrc(int portSrc) {
        if (transportLayerProtocol == null) {
            transportLayerProtocol = "tcp";
        }
        this.portSrc = portSrc;
        return this;
    }

    public MirrorData setPortDst(int portDst) {
        if (transportLayerProtocol == null) {
            transportLayerProtocol = "tcp";
        }
        this.portDst = portDst;
        return this;
    }

    public MirrorData setSrc(IPPort src) {
        return setIpSrc(src.getAddress()).setPortSrc(src.getPort());
    }

    public MirrorData setDst(IPPort dst) {
        return setIpDst(dst.getAddress()).setPortDst(dst.getPort());
    }

    public MirrorData setSrc(ByteArray src) {
        ByteArray mac = ByteArray.allocate(6);
        ByteArray ip = ByteArray.allocate(16);
        ByteArray port = ByteArray.allocate(2);
        fill(mac, ip, port, src);
        if (ip.get(0) == 0) {
            ip.set(0, (byte) 0xfd);
        }

        return this.setMacSrc(new MacAddress(mac))
            .setIpSrc(IP.from(ip.toJavaArray()))
            .setPortSrc(port.uint16(0));
    }

    public MirrorData setDst(ByteArray dst) {
        ByteArray mac = ByteArray.allocate(6);
        ByteArray ip = ByteArray.allocate(16);
        ByteArray port = ByteArray.allocate(2);

        for (int i = 0; i < mac.length(); ++i) {
            mac.set(0, (byte) 0xff);
        }

        fill(mac, ip, port, dst);
        if (ip.get(0) == 0) {
            ip.set(0, (byte) 0xfd);
        }

        return this.setMacDst(new MacAddress(mac))
            .setIpDst(IP.from(ip.toJavaArray()))
            .setPortDst(port.uint16(0));
    }

    private ByteArray buildByteArrayFromObjectRef(Object ref) {
        String name = ref.getClass().getSimpleName();
        int hashCode = ref.hashCode();
        return ByteArray.from((name + "@" + Integer.toHexString(hashCode)).getBytes());
    }

    public MirrorData setSrcRef(Object ref) {
        return setSrc(buildByteArrayFromObjectRef(ref));
    }

    public MirrorData setDstRef(Object ref) {
        return setDst(buildByteArrayFromObjectRef(ref));
    }

    public MirrorData setTransportLayerProtocol(String transportLayerProtocol) {
        this.transportLayerProtocol = transportLayerProtocol;
        return this;
    }

    public MirrorData setApplicationLayerProtocol(String applicationLayerProtocol) {
        this.applicationLayerProtocol = applicationLayerProtocol;
        return this;
    }

    public MirrorData setMeta(String meta) {
        this.meta = meta;
        return this;
    }

    public MirrorData setFlags(byte flags) {
        this.flags = flags;
        return this;
    }

    public MirrorData setData(ByteArray data) {
        this.data = data;
        return this;
    }

    public MirrorData setData(byte[] arr) {
        return setData(ByteArray.from(arr));
    }

    public MirrorData setDataAfter(ByteBufferEx buf, int posBefore) {
        return setDataAfter(buf.realBuffer(), posBefore);
    }

    public MirrorData setDataAfter(ByteBuffer buf, int posBefore) {
        int posAfter = buf.position();
        int lim = buf.limit();

        if (posBefore >= posAfter) { // nothing to mirror
            return this;
        }
        byte[] arr = Utils.allocateByteArray(posAfter - posBefore);
        buf.limit(posAfter).position(posBefore);
        buf.get(arr);
        buf.limit(lim).position(posAfter);
        return setData(arr);
    }

    private static void fill(ByteArray a6, ByteArray b16, ByteArray c2, ByteArray array) {
        int total = array.length();
        if (total > 6 + 16 + 2) {
            for (int i = 0; i < 2; ++i) {
                c2.set(2 - 1 - i, array.get(total - 1 - i));
            }
            for (int i = 0; i < 16; ++i) {
                b16.set(16 - 1 - i, array.get(total - 1 - 2 - i));
            }
            for (int i = 0; i < 6; ++i) {
                a6.set(6 - 1 - i, array.get(total - 1 - 2 - 16 - i));
            }
        } else if (total > 16 + 2) {
            for (int i = 0; i < 2; ++i) {
                c2.set(2 - 1 - i, array.get(total - 1 - i));
            }
            for (int i = 0; i < 16; ++i) {
                b16.set(16 - 1 - i, array.get(total - 1 - 2 - i));
            }
            for (int i = 0; i < total - 2 - 16; ++i) {
                a6.set(6 - 1 - i, array.get(total - 1 - 2 - 16 - i));
            }
        } else if (total > 2) {
            for (int i = 0; i < 2; ++i) {
                c2.set(2 - 1 - i, array.get(total - 1 - i));
            }
            for (int i = 0; i < total - 2; ++i) {
                b16.set(16 - 1 - i, array.get(total - 1 - 2 - i));
            }
        } else {
            for (int i = 0; i < total; ++i) {
                c2.set(2 - 1 - i, array.get(total - 1 - i));
            }
        }
    }

    public void mirror() {
        Mirror.mirror(this);
    }
}
