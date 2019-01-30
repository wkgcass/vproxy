package net.cassite.vproxy.app.cmd.handle.resource;

import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.app.cmd.Resource;
import net.cassite.vproxy.app.cmd.ResourceType;
import net.cassite.vproxy.dns.Resolver;

import java.util.LinkedList;
import java.util.List;

public class DnsCacheHandle {
    private DnsCacheHandle() {
    }

    public static void checkDnsCache(Resource parent) throws Exception {
        if (parent == null)
            throw new Exception("cannot find " + ResourceType.dnscache.fullname + " on top level");
        ResolverHandle.checkResolver(parent);
    }

    public static int count() {
        Resolver resolver = (Resolver) Resolver.getDefault();
        return resolver.cacheCount();
    }

    public static List<Resolver.Cache> detail() {
        List<Resolver.Cache> caches = new LinkedList<>();
        Resolver.getDefault().copyCache(caches);
        return caches;
    }

    public static void remove(Command cmd) {
        List<Resolver.Cache> caches = detail();
        String host = cmd.resource.alias;
        for (Resolver.Cache c : caches) {
            if (c.host.equals(host)) {
                c.remove();
                // there can be no other cache with the same host
                break;
            }
        }
    }
}
