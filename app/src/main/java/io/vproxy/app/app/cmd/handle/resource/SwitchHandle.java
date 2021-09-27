package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.Application;
import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Flag;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.app.app.cmd.Resource;
import io.vproxy.app.app.cmd.handle.param.AddrHandle;
import io.vproxy.app.app.cmd.handle.param.FloodHandle;
import io.vproxy.app.app.cmd.handle.param.MTUHandle;
import io.vproxy.app.app.cmd.handle.param.TimeoutHandle;
import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.component.secure.SecurityGroup;
import io.vproxy.vfd.IPPort;
import io.vproxy.vswitch.Switch;

import java.util.LinkedList;
import java.util.List;

public class SwitchHandle {
    public static final int MAC_TABLE_TIMEOUT = 300 * 1000;
    public static final int ARP_TABLE_TIMEOUT = 4 * 3600 * 1000;

    private SwitchHandle() {
    }

    public static Switch get(Resource sw) throws NotFoundException {
        return Application.get().switchHolder.get(sw.alias);
    }

    public static List<String> names() {
        return Application.get().switchHolder.names();
    }

    public static List<SwitchRef> details() throws Exception {
        List<SwitchRef> result = new LinkedList<>();
        for (String name : names()) {
            result.add(new SwitchRef(
                Application.get().switchHolder.get(name)
            ));
        }
        return result;
    }

    public static void add(Command cmd) throws Exception {
        if (!cmd.args.containsKey(Param.elg)) {
            cmd.args.put(Param.elg, Application.DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME);
        }

        String alias = cmd.resource.alias;
        EventLoopGroup eventLoopGroup = Application.get().eventLoopGroupHolder.get(cmd.args.get(Param.elg));
        IPPort addr = AddrHandle.get(cmd);
        int macTableTimeout;
        if (cmd.args.containsKey(Param.mactabletimeout)) {
            macTableTimeout = TimeoutHandle.get(cmd, Param.mactabletimeout);
        } else {
            macTableTimeout = MAC_TABLE_TIMEOUT;
        }
        int arpTableTimeout;
        if (cmd.args.containsKey(Param.arptabletimeout)) {
            arpTableTimeout = TimeoutHandle.get(cmd, Param.arptabletimeout);
        } else {
            arpTableTimeout = ARP_TABLE_TIMEOUT;
        }
        SecurityGroup bareVXLanAccess;
        if (cmd.args.containsKey(Param.secg)) {
            bareVXLanAccess = SecurityGroupHandle.get(cmd.args.get(Param.secg));
        } else {
            bareVXLanAccess = SecurityGroup.allowAll();
        }
        int mtu;
        if (cmd.args.containsKey(Param.mtu)) {
            mtu = MTUHandle.get(cmd);
        } else {
            mtu = 1500;
        }
        boolean flood;
        if (cmd.args.containsKey(Param.flood)) {
            flood = FloodHandle.get(cmd);
        } else {
            flood = true;
        }
        Application.get().switchHolder.add(alias, addr, eventLoopGroup,
            macTableTimeout, arpTableTimeout, bareVXLanAccess,
            mtu, flood);
    }

    public static void attach(Command cmd) throws Exception {
        String alias = cmd.resource.alias;
        IPPort addr = AddrHandle.get(cmd);

        Switch sw = get(cmd.prepositionResource);
        boolean addSwitchFlag = !cmd.flags.contains(Flag.noswitchflag);
        sw.addRemoteSwitch(alias, addr, addSwitchFlag);
    }

    public static void update(Command cmd) throws Exception {
        Switch sw = get(cmd.resource);

        if (cmd.args.containsKey(Param.mactabletimeout)) {
            int macTableTimeout = TimeoutHandle.get(cmd, Param.mactabletimeout);
            sw.setMacTableTimeout(macTableTimeout);
        }
        if (cmd.args.containsKey(Param.arptabletimeout)) {
            int arpTableTimeout = TimeoutHandle.get(cmd, Param.arptabletimeout);
            sw.setArpTableTimeout(arpTableTimeout);
        }
        if (cmd.args.containsKey(Param.secg)) {
            sw.bareVXLanAccess = SecurityGroupHandle.get(cmd.args.get(Param.secg));
        }
        if (cmd.args.containsKey(Param.mtu)) {
            sw.defaultMtu = MTUHandle.get(cmd);
        }
        if (cmd.args.containsKey(Param.flood)) {
            sw.defaultFloodAllowed = FloodHandle.get(cmd);
        }
    }

    public static void remove(Command cmd) throws Exception {
        // remove the top level switch
        Application.get().switchHolder.removeAndStop(cmd.resource.alias);
    }

    public static class SwitchRef {
        public final Switch sw;

        public SwitchRef(Switch sw) {
            this.sw = sw;
        }

        @Override
        public String toString() {
            return sw.alias + " -> event-loop-group " + sw.eventLoopGroup.alias
                + " bind " + sw.vxlanBindingAddress.formatToIPPortString()
                + " mac-table-timeout " + sw.getMacTableTimeout()
                + " arp-table-timeout " + sw.getArpTableTimeout()
                + " bare-vxlan-access " + sw.bareVXLanAccess.alias
                + " mtu " + sw.defaultMtu
                + " flood " + (sw.defaultFloodAllowed ? "allow" : "deny");
        }
    }
}
