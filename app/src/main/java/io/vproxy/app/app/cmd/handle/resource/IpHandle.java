package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.cmd.Command;
import io.vproxy.app.app.cmd.Param;
import io.vproxy.app.app.cmd.Resource;
import io.vproxy.app.app.cmd.ResourceType;
import io.vproxy.app.app.cmd.handle.param.AnnotationsHandle;
import io.vproxy.app.app.cmd.handle.param.RoutingHandle;
import io.vproxy.base.util.Annotations;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.base.util.exception.XException;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vswitch.IPMac;
import io.vproxy.vswitch.VirtualNetwork;

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
        VirtualNetwork net = VrfHandle.get(parent);
        return net.ips.allIps();
    }

    public static Collection<IPMac> list(Resource parent) throws Exception {
        VirtualNetwork net = VrfHandle.get(parent);
        return net.ips.entries();
    }

    public static void add(Command cmd) throws Exception {
        String ip = cmd.resource.alias;
        String mac = cmd.args.get(Param.mac);

        IP inet = IP.from(ip);
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

        IPMac info = VrfHandle.get(cmd.prepositionResource).addIp(inet, macO, anno);
        if (cmd.args.containsKey(Param.routing)) {
            info.routing = RoutingHandle.get(cmd);
        } else {
            info.routing = true;
        }
    }

    public static void update(Command cmd) throws Exception {
        IP ip = IP.from(cmd.resource.alias);
        VirtualNetwork net = VrfHandle.get(cmd.resource.parentResource);
        var opt = net.ips.entries().stream().filter(ipmac -> ipmac.ip.equals(ip)).findFirst();
        if (opt.isEmpty()) {
            throw new NotFoundException(ResourceType.ip.fullname, cmd.resource.alias);
        }
        IPMac info = opt.get();
        if (cmd.args.containsKey(Param.routing)) {
            info.routing = RoutingHandle.get(cmd);
        }
    }

    public static void remove(Command cmd) throws Exception {
        String ip = cmd.resource.alias;

        byte[] ipBytes = IP.parseIpString(ip);
        if (ipBytes == null) {
            throw new XException("invalid ip address: " + ip);
        }
        IP inet = IP.from(ipBytes);

        VrfHandle.get(cmd.prepositionResource).ips.del(inet);
    }
}
