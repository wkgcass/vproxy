package vswitch.util;

import vproxy.util.ByteArray;
import vswitch.iface.ClientSideVniGetterSetter;
import vswitch.iface.Iface;
import vswitch.iface.ServerSideVniGetterSetter;

public class SwitchUtils {
    private SwitchUtils() {
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
        if (iface instanceof ClientSideVniGetterSetter) {
            var that = (ClientSideVniGetterSetter) newIface;
            if (that.getClientSideVni() != 0) {
                ((ClientSideVniGetterSetter) iface).setClientSideVni(that.getClientSideVni());
            }
        }
        if (iface instanceof ServerSideVniGetterSetter) {
            var that = (ServerSideVniGetterSetter) newIface;
            var self = (ServerSideVniGetterSetter) iface;
            var newVal = that.getServerSideVni(0);
            if (newVal != 0) {
                self.setServerSideVni(newVal);
            }
        }
    }
}
