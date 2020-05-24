package vproxyapp.app.cmd.handle.resource;

import vfd.IPPort;
import vproxy.component.secure.SecurityGroup;
import vproxyapp.app.Application;
import vproxyapp.app.cmd.Command;
import vproxyapp.app.cmd.Flag;
import vproxyapp.app.cmd.Param;
import vproxyapp.app.cmd.Resource;
import vproxyapp.app.cmd.handle.param.AddrHandle;
import vproxyapp.app.cmd.handle.param.TimeoutHandle;
import vproxybase.component.elgroup.EventLoopGroup;
import vproxybase.util.exception.NotFoundException;
import vswitch.Switch;

import java.util.LinkedList;
import java.util.List;

public class SwitchHandle {
    public static final int MAC_TABLE_TIMEOUT = 300 * 1000;
    public static final int ARP_TABLE_TIMEOUT = 4 * 3600 * 1000;

    private SwitchHandle() {
    }

    public static void checkSwitch(Resource sw) throws Exception {
        // only top level switches would be called for this method
        if (sw.parentResource != null)
            throw new Exception(sw.type.fullname + " is on top level");
    }

    @SuppressWarnings("Duplicates")
    public static void checkCreateSwitch(Command cmd) throws Exception {
        if (!cmd.args.containsKey(Param.addr))
            throw new Exception("missing argument " + Param.addr.fullname);

        AddrHandle.check(cmd);

        if (cmd.prepositionResource == null) { // the switch on top level require these arguments
            if (cmd.args.containsKey(Param.mactabletimeout)) {
                TimeoutHandle.check(cmd, Param.mactabletimeout);
            }
            if (cmd.args.containsKey(Param.arptabletimeout)) {
                TimeoutHandle.check(cmd, Param.arptabletimeout);
            }
        }
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

    @SuppressWarnings("Duplicates")
    private static void addTopLevel(Command cmd) throws Exception {
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
        Application.get().switchHolder.add(alias, addr, eventLoopGroup, macTableTimeout, arpTableTimeout, bareVXLanAccess);
    }

    public static void addSubLevel(Command cmd) throws Exception {
        String alias = cmd.resource.alias;
        IPPort addr = AddrHandle.get(cmd);

        Switch sw = get(cmd.prepositionResource);
        boolean addSwitchFlag = true;
        if (cmd.flags.contains(Flag.noswitchflag)) {
            addSwitchFlag = false;
        }
        sw.addRemoteSwitch(alias, addr, addSwitchFlag);
    }

    public static void add(Command cmd) throws Exception {
        if (cmd.prepositionResource == null) {
            addTopLevel(cmd);
        } else {
            addSubLevel(cmd);
        }
    }

    public static void checkUpdateSwitch(Command cmd) throws Exception {
        if (cmd.prepositionResource != null) {
            throw new Exception("you can only update the switch on top level");
        }
        if (cmd.args.containsKey(Param.mactabletimeout)) {
            TimeoutHandle.check(cmd, Param.mactabletimeout);
        }
        if (cmd.args.containsKey(Param.arptabletimeout)) {
            TimeoutHandle.check(cmd, Param.arptabletimeout);
        }
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
    }

    public static void forceRemove(Command cmd) throws Exception {
        if (cmd.prepositionResource == null) { // remove the top level switch
            Application.get().switchHolder.removeAndStop(cmd.resource.alias);
        } else { // remove the remote switch ref inside the switch
            Application.get().switchHolder.get(cmd.prepositionResource.alias).delRemoteSwitch(cmd.resource.alias);
        }
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
                + " bare-vxlan-access " + sw.bareVXLanAccess.alias;
        }
    }
}
