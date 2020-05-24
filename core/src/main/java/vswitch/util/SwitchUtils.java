package vswitch.util;

import vswitch.iface.Iface;
import vswitch.iface.LocalSideVniGetterSetter;
import vswitch.iface.RemoteSideVniGetterSetter;

public class SwitchUtils {
    private SwitchUtils() {
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
