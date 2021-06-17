package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.app.app.cmd.Resource;
import vproxy.app.app.cmd.ResourceType;
import vproxy.app.app.cmd.handle.param.FloodHandle;
import vproxy.app.app.cmd.handle.param.MTUHandle;
import vproxy.base.util.Consts;
import vproxy.base.util.exception.NotFoundException;
import vproxy.vfd.IPPort;
import vproxy.vswitch.Switch;
import vproxy.vswitch.iface.*;

import java.util.List;

public class IfaceHandle {
    private IfaceHandle() {
    }

    public static void checkIfaceParent(Resource parent) throws Exception {
        if (parent == null)
            throw new Exception("cannot find " + ResourceType.iface.fullname + " on top level");
        if (parent.type != ResourceType.sw)
            throw new Exception(parent.type.fullname + " does not contain " + ResourceType.iface.fullname);
        SwitchHandle.checkSwitch(parent);
    }

    public static int count(Resource parent) throws Exception {
        return list(parent).size();
    }

    public static List<Iface> list(Resource parent) throws Exception {
        Switch sw = Application.get().switchHolder.get(parent.alias);
        return sw.getIfaces();
    }

    public static void checkUpdateIface(Command cmd) throws Exception {
        if (cmd.args.containsKey(Param.mtu)) {
            MTUHandle.check(cmd);
        }
        if (cmd.args.containsKey(Param.flood)) {
            FloodHandle.check(cmd);
        }
    }

    public static void update(Command cmd) throws Exception {
        List<Iface> ifaces = list(cmd.resource.parentResource);
        String name = cmd.resource.alias;

        boolean isBareVXLanIface = false;
        boolean isRemoteSwitchIface = false;
        boolean isTapIface = false;
        boolean isUserClientIface = false;
        boolean isUserIface = false;

        if (name.startsWith("ucli:")) {
            isUserClientIface = true;
            name = name.substring("ucli:".length());
        } else if (name.startsWith("user:")) {
            isUserIface = true;
            name = name.substring("user:".length());
        } else if (name.startsWith("tap:")) {
            isTapIface = true;
            name = name.substring("tap:".length());
        } else if (name.startsWith("remote:")) {
            isRemoteSwitchIface = true;
            name = name.substring("remote:".length());
        } else if (IPPort.validL4AddrStr(name)) {
            isBareVXLanIface = true;
        }

        Iface target = null;
        for (Iface iface : ifaces) {
            if (iface instanceof BareVXLanIface) {
                if (!isBareVXLanIface) {
                    continue;
                }
                if (((BareVXLanIface) iface).udpSockAddress.equals(new IPPort(name))) {
                    target = iface;
                    break;
                }
            } else if (iface instanceof RemoteSwitchIface) {
                if (!isRemoteSwitchIface) {
                    continue;
                }
                if (((RemoteSwitchIface) iface).alias.equals(name)) {
                    target = iface;
                    break;
                }
            } else if (iface instanceof TapIface) {
                if (!isTapIface) {
                    continue;
                }
                if (((TapIface) iface).tap.getTap().dev.equals(name)) {
                    target = iface;
                    break;
                }
            } else if (iface instanceof UserClientIface) {
                if (!isUserClientIface) {
                    continue;
                }
                if (((UserClientIface) iface).user.user.replace(Consts.USER_PADDING, "").equals(name)) {
                    target = iface;
                    break;
                }
            } else if (iface instanceof UserIface) {
                if (!isUserIface) {
                    continue;
                }
                if (((UserIface) iface).user.replace(Consts.USER_PADDING, "").equals(name)) {
                    target = iface;
                    break;
                }
            }
        }
        if (target == null) {
            throw new NotFoundException(ResourceType.iface.fullname, cmd.resource.alias);
        }
        if (cmd.args.containsKey(Param.mtu)) {
            target.setBaseMTU(MTUHandle.get(cmd));
        }
        if (cmd.args.containsKey(Param.flood)) {
            target.setFloodAllowed(FloodHandle.get(cmd));
        }
    }
}
