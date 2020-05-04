package vswitch.util;

import vproxy.util.ByteArray;
import vswitch.iface.RemoteSideVniGetterSetter;
import vswitch.iface.Iface;
import vswitch.iface.LocalSideVniGetterSetter;
import vswitch.packet.Ipv6Packet;

public class SwitchUtils {
    private SwitchUtils() {
    }

    public static ByteArray buildPseudoIPv6Header(Ipv6Packet ipv6, int upperType, int upperLength) {
        ByteArray pseudoHeaderTail = ByteArray.allocate(8);
        ByteArray pseudoHeader = ByteArray.from(ipv6.getSrc().getAddress())
            .concat(ByteArray.from(ipv6.getDst().getAddress()))
            .concat(pseudoHeaderTail);
        pseudoHeaderTail.int32(0, upperLength);
        pseudoHeaderTail.set(7, (byte) upperType);
        return pseudoHeader;
    }

    public static int calculateChecksum(ByteArray array, int limit) {
        int sum = 0;
        for (int i = 0; i < limit / 2; ++i) {
            sum += array.uint16(i * 2);
            while (sum > 0xffff) {
                sum = (sum & 0xffff) + 1;
            }
        }
        if (limit % 2 != 0) {
            sum += (array.uint8(limit - 1) << 8);
            while (sum > 0xffff) {
                sum = (sum & 0xffff) + 1;
            }
        }
        return 0xffff - sum;
    }

    public static void updateBothSideVni(Iface iface, Iface newIface) {
        assert iface.equals(newIface);
        if (iface instanceof RemoteSideVniGetterSetter) {
            var that = (RemoteSideVniGetterSetter) newIface;
            if (that.getRemoteSideVni() != 0) {
                ((RemoteSideVniGetterSetter) iface).setRemoteSideVni(that.getRemoteSideVni());
            }
        }
        if (iface instanceof LocalSideVniGetterSetter) {
            var that = (LocalSideVniGetterSetter) newIface;
            var self = (LocalSideVniGetterSetter) iface;
            var newVal = that.getLocalSideVni(0);
            if (newVal != 0) {
                self.setLocalSideVni(newVal);
            }
        }
    }
}
