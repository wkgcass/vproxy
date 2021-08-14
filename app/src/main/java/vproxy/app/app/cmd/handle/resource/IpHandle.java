package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.app.app.cmd.Resource;
import vproxy.app.app.cmd.handle.param.AnnotationsHandle;
import vproxy.base.util.Annotations;
import vproxy.base.util.exception.XException;
import vproxy.vfd.IP;
import vproxy.vfd.MacAddress;
import vproxy.vswitch.IPMac;
import vproxy.vswitch.VirtualNetwork;

import java.util.Collection;

public class IpHandle {
    private IpHandle() {
    }

    public static void checkIpName(Resource resource) throws XException {
        var bytes = IP.parseIpString(resource.alias);
        if (bytes == null) {
            throw new XException("input is not a valid ip string: " + resource.alias);
        }
    }

    public static Collection<IP> names(Resource parent) throws Exception {
        VirtualNetwork net = VpcHandle.get(parent);
        return net.ips.allIps();
    }

    public static Collection<IPMac> list(Resource parent) throws Exception {
        VirtualNetwork net = VpcHandle.get(parent);
        return net.ips.entries();
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

    public static void remove(Command cmd) throws Exception {
        String ip = cmd.resource.alias;

        byte[] ipBytes = IP.parseIpString(ip);
        if (ipBytes == null) {
            throw new XException("invalid ip address: " + ip);
        }
        IP inet = IP.from(ipBytes);

        VpcHandle.get(cmd.prepositionResource).ips.del(inet);
    }
}
