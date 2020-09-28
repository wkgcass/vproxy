package vproxyapp.app.cmd.handle.resource;

import vfd.IPPort;
import vproxyapp.app.cmd.Command;
import vproxyapp.app.cmd.Resource;
import vproxyapp.app.cmd.ResourceType;
import vproxyapp.app.cmd.handle.param.AddrHandle;
import vproxybase.util.exception.XException;
import vswitch.ProxyHolder;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ProxyHandle {
    private ProxyHandle() {
    }

    public static void checkSwitchProxy(Resource parent) throws Exception {
        if (parent == null) {
            throw new Exception("cannot find " + ResourceType.proxy.fullname + " on top level");
        }
        if (parent.type != ResourceType.vpc) {
            throw new Exception(parent.type.fullname + " does not contain " + ResourceType.proxy.fullname);
        }
        VpcHandle.checkVpc(parent.parentResource);
    }

    public static void checkCreateSwitchProxy(Command cmd) throws Exception {
        var name = cmd.resource.alias;
        if (!IPPort.validL4AddrStr(name)) {
            throw new XException("the resource alias is not ip:port format");
        }
        checkSwitchProxy(cmd.prepositionResource);
        AddrHandle.check(cmd);
    }

    public static void checkRemoveSwitchProxy(Command cmd) throws Exception {
        var name = cmd.resource.alias;
        if (!IPPort.validL4AddrStr(name)) {
            throw new XException("the resource alias is not ip:port format");
        }
        checkSwitchProxy(cmd.prepositionResource);
    }

    public static List<String> names(Resource parent) throws Exception {
        var table = VpcHandle.get(parent);
        return table.proxies.listRecords().stream().map(r -> r.listen.formatToIPPortString()).collect(Collectors.toList());
    }

    public static Collection<ProxyHolder.ProxyRecord> list(Resource parent) throws Exception {
        var table = VpcHandle.get(parent);
        return table.proxies.listRecords();
    }

    public static ProxyHolder.ProxyRecord get(Resource resource) throws Exception {
        var table = VpcHandle.get(resource.parentResource);
        var addr = new IPPort(resource.alias);
        return table.proxies.lookup(addr);
    }

    public static void add(Command cmd) throws Exception {
        var ipport = cmd.resource.alias;
        var listen = new IPPort(ipport);
        var target = AddrHandle.get(cmd);
        var table = VpcHandle.get(cmd.prepositionResource);

        table.proxies.add(listen, target);
    }

    public static void forceRemove(Command cmd) throws Exception {
        var ipport = cmd.resource.alias;
        var listen = new IPPort(ipport);
        var table = VpcHandle.get(cmd.prepositionResource);

        table.proxies.remove(listen);
    }
}
