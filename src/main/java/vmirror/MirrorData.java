package vmirror;

import vproxy.util.ByteArray;
import vproxy.util.Utils;
import vswitch.util.Consts;
import vswitch.util.MacAddress;

import java.net.InetAddress;
import java.nio.ByteBuffer;

public class MirrorData {
    public final String origin;
    MacAddress macSrc = new MacAddress("00:00:00:00:00:00");
    MacAddress macDst = new MacAddress("ff:ff:ff:ff:ff:ff");
    InetAddress ipSrc;
    InetAddress ipDst;
    String transportLayerProtocol;
    int portSrc;
    int portDst;
    String applicationLayerProtocol;
    String meta;
    byte flags = Consts.TCP_FLAGS_PSH;
    ByteArray data;

    public MirrorData(String origin) {
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

    public MirrorData setIpSrc(InetAddress ipSrc) {
        this.ipSrc = ipSrc;
        return this;
    }

    public MirrorData setIpDst(InetAddress ipDst) {
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

    public MirrorData setSrc(ByteArray src) {
        ByteArray mac = ByteArray.allocate(6);
        ByteArray ip = ByteArray.allocate(16);
        ByteArray port = ByteArray.allocate(2);
        fill(mac, ip, port, src);
        if (ip.get(0) == 0) {
            ip.set(0, (byte) 0xfd);
        }

        return this.setMacSrc(new MacAddress(mac))
            .setIpSrc(Utils.l3addr(ip.toJavaArray()))
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
            .setIpDst(Utils.l3addr(ip.toJavaArray()))
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

    public MirrorData setDataAfter(ByteBuffer buf, int posBefore) {
        int posAfter = buf.position();
        int lim = buf.limit();

        if (posBefore >= posAfter) { // nothing to mirror
            return this;
        }
        byte[] arr = new byte[posAfter - posBefore];
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
}
