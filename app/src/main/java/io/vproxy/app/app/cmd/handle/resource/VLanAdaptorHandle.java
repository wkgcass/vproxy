package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.Application;
import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.app.app.cmd.handle.param.VniHandle;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.exception.XException;
import io.vproxy.vswitch.Switch;

public class VLanAdaptorHandle {
    private VLanAdaptorHandle() {
    }

    public static void add(Command cmd) throws Exception {
        String name = cmd.resource.alias;
        if (!name.contains("@")) {
            throw new XException("invalid format for adding vlan: expecting {vlan-id}@{parent-iface-name}: missing '@'");
        }
        String vlanStr = name.substring(0, name.indexOf("@"));
        String parent = name.substring(name.indexOf("@") + 1);

        if (!Utils.isInteger(vlanStr)) {
            throw new XException(vlanStr + " is not a valid vlan id: not a number");
        }
        int vlan = Integer.parseInt(vlanStr);
        if (vlan < 0 || vlan > 4095) {
            throw new XException(vlan + " out of range: expecting [0, 4095]");
        }

        int vni = vlan;
        if (cmd.args.containsKey(Param.vni)) {
            vni = VniHandle.get(cmd);
        }

        Switch sw = Application.get().switchHolder.get(cmd.prepositionResource.alias);

        sw.addVLanAdaptor(parent, vlan, vni);
    }
}
