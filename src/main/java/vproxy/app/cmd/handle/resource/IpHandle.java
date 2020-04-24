package vproxy.app.cmd.handle.resource;

import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;
import vproxy.app.cmd.Resource;
import vproxy.app.cmd.ResourceType;
import vproxy.component.exception.XException;
import vproxy.util.Utils;
import vswitch.Table;
import vswitch.util.MacAddress;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Map;

public class IpHandle {
    private IpHandle() {
    }

    public static void checkIp(Resource parent) throws Exception {
        if (parent == null)
            throw new Exception("cannot find " + ResourceType.ip.fullname + " on top level");
        if (parent.type != ResourceType.vni) {
            if (parent.type == ResourceType.sw) {
                throw new Exception(parent.type.fullname + " does not directly contain " + ResourceType.ip.fullname + ", you have to specify vni first");
            }
            throw new Exception(parent.type.fullname + " does not contain " + ResourceType.ip.fullname);
        }

        VniHandle.checkVni(parent.parentResource);
        VniHandle.checkVniName(parent);
    }

    public static Collection<InetAddress> names(Resource parent) throws Exception {
        Table tbl = VniHandle.get(parent);
        return tbl.ips.allIps();
    }

    public static Collection<Map.Entry<InetAddress, MacAddress>> list(Resource parent) throws Exception {
        Table tbl = VniHandle.get(parent);
        return tbl.ips.entries();
    }

    public static void checkCreateIp(Command cmd) throws Exception {
        String mac = cmd.args.get(Param.mac);
        if (mac == null) {
            throw new Exception("missing " + Param.mac.fullname);
        }
    }

    public static void add(Command cmd) throws Exception {
        String ip = cmd.resource.alias;
        String mac = cmd.args.get(Param.mac);

        byte[] ipBytes = Utils.parseIpString(ip);
        if (ipBytes == null) {
            throw new XException("invalid ip address: " + ip);
        }
        InetAddress inet = Utils.l3addr(ipBytes);
        MacAddress macO;
        try {
            macO = new MacAddress(mac);
        } catch (IllegalArgumentException e) {
            throw new XException("invalid mac address: " + mac);
        }

        VniHandle.get(cmd.prepositionResource).ips.add(inet, macO);
    }

    public static void forceRemove(Command cmd) throws Exception {
        String ip = cmd.resource.alias;

        byte[] ipBytes = Utils.parseIpString(ip);
        if (ipBytes == null) {
            throw new XException("invalid ip address: " + ip);
        }
        InetAddress inet = Utils.l3addr(ipBytes);

        VniHandle.get(cmd.prepositionResource).ips.del(inet);
    }
}
