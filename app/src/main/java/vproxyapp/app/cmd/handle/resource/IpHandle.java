package vproxyapp.app.cmd.handle.resource;

import vfd.IP;
import vfd.MacAddress;
import vproxyapp.app.cmd.Command;
import vproxyapp.app.cmd.Param;
import vproxyapp.app.cmd.Resource;
import vproxyapp.app.cmd.ResourceType;
import vproxyapp.app.cmd.handle.param.AnnotationsHandle;
import vproxybase.util.Annotations;
import vproxybase.util.exception.XException;
import vswitch.IPMac;
import vswitch.Table;

import java.util.Collection;
import java.util.Map;

public class IpHandle {
    private IpHandle() {
    }

    public static void checkIpParent(Resource parent) throws Exception {
        if (parent == null)
            throw new Exception("cannot find " + ResourceType.ip.fullname + " on top level");
        if (parent.type != ResourceType.vpc) {
            if (parent.type == ResourceType.sw) {
                throw new Exception(parent.type.fullname + " does not directly contain " + ResourceType.ip.fullname + ", you have to specify vpc first");
            }
            throw new Exception(parent.type.fullname + " does not contain " + ResourceType.ip.fullname);
        }

        VpcHandle.checkVpc(parent);
    }

    public static Collection<IP> names(Resource parent) throws Exception {
        Table tbl = VpcHandle.get(parent);
        return tbl.ips.allIps();
    }

    public static Collection<IPMac> list(Resource parent) throws Exception {
        Table tbl = VpcHandle.get(parent);
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

        byte[] ipBytes = IP.parseIpString(ip);
        if (ipBytes == null) {
            throw new XException("invalid ip address: " + ip);
        }
        IP inet = IP.from(ipBytes);
        MacAddress macO;
        try {
            macO = new MacAddress(mac);
        } catch (IllegalArgumentException e) {
            throw new XException("invalid mac address: " + mac);
        }

        Annotations anno = null;
        if (cmd.args.containsKey(Param.anno)) {
            anno = AnnotationsHandle.get(cmd);
        }

        VpcHandle.get(cmd.prepositionResource).addIp(inet, macO, anno);
    }

    public static void forceRemove(Command cmd) throws Exception {
        String ip = cmd.resource.alias;

        byte[] ipBytes = IP.parseIpString(ip);
        if (ipBytes == null) {
            throw new XException("invalid ip address: " + ip);
        }
        IP inet = IP.from(ipBytes);

        VpcHandle.get(cmd.prepositionResource).ips.del(inet);
    }
}
