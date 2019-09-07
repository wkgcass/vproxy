package vproxy.app.cmd.handle.resource;

import vproxy.app.Application;
import vproxy.app.Config;
import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;
import vproxy.app.cmd.Resource;
import vproxy.app.cmd.ResourceType;
import vproxy.app.cmd.handle.param.ServiceHandle;
import vproxy.app.cmd.handle.param.ZoneHandle;
import vproxy.app.mesh.SmartServiceDelegateHolder;
import vproxy.component.auto.SmartServiceDelegate;
import vproxy.component.exception.NotFoundException;
import vproxy.component.exception.XException;
import vproxy.util.IPType;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

public class SmartServiceDelegateHandle {
    private SmartServiceDelegateHandle() {
    }

    public static void check(Resource parent) throws XException {
        if (parent != null)
            throw new XException(ResourceType.sgd.fullname + " is on top level");
    }

    private static void checkNicAndIPType(String nicName, String ipTypeName) throws XException {
        IPType ipType;
        try {
            ipType = IPType.valueOf(ipTypeName);
        } catch (IllegalArgumentException e) {
            throw new XException("invalid format for " + Param.iptype.fullname);
        }
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                if (nic.getName().equals(nicName)) {
                    Enumeration<InetAddress> addrs = nic.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress addr = addrs.nextElement();
                        if (ipType == IPType.v4) {
                            if (addr instanceof Inet4Address) {
                                return;
                            }
                        } else {
                            assert ipType == IPType.v6;
                            if (addr instanceof Inet6Address) {
                                return;
                            }
                        }
                    }
                    throw new XException("ip" + ipTypeName + " not found in nic " + nicName);
                }
            }
        } catch (XException e) {
            throw e;
        } catch (Exception e) {
            throw new XException("get nic failed");
        }
    }

    public static void checkCreate(Command cmd) throws XException {
        if (!Config.discoveryConfigProvided) {
            throw new XException("discovery config not provided, so the smart-service-delegate cannot be created");
        }
        if (!cmd.args.containsKey(Param.service))
            throw new XException("missing argument " + Param.service.fullname);
        if (!cmd.args.containsKey(Param.zone))
            throw new XException("missing argument " + Param.zone.fullname);
        if (!cmd.args.containsKey(Param.iptype)) {
            cmd.args.put(Param.iptype, IPType.v4.name());
        }
        if (!cmd.args.containsKey(Param.port))
            throw new XException("missing argument " + Param.port.fullname);

        checkNicAndIPType(cmd.args.get(Param.nic), cmd.args.get(Param.iptype));
        String portStr = cmd.args.get(Param.port);
        try {
            Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw new XException("invalid format for " + Param.port.fullname);
        }
    }

    public static List<String> names() {
        return Application.get().smartServiceDelegateHolder.names();
    }

    public static List<SmartServiceDelegate> detail() {
        SmartServiceDelegateHolder holder = Application.get().smartServiceDelegateHolder;
        List<String> names = holder.names();
        List<SmartServiceDelegate> smartServiceDelegates = new LinkedList<>();
        for (String name : names) {
            try {
                SmartServiceDelegate s = holder.get(name);
                smartServiceDelegates.add(s);
            } catch (NotFoundException ignore) {
            }
        }
        return smartServiceDelegates;
    }

    public static void remove(Command cmd) throws NotFoundException {
        String alias = cmd.resource.alias;
        Application.get().smartServiceDelegateHolder.remove(alias);
    }

    public static void add(Command cmd) throws Exception {
        String alias = cmd.resource.alias;
        String service = ServiceHandle.get(cmd);
        String zone = ZoneHandle.get(cmd);
        String nic = cmd.args.get(Param.nic);
        IPType ipType = IPType.valueOf(cmd.args.get(Param.iptype));
        int port = Integer.parseInt(cmd.args.get(Param.port));

        Application.get().smartServiceDelegateHolder.add(alias, service, zone, nic, ipType, port);
    }
}
