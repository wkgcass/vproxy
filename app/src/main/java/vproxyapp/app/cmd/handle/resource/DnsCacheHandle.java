package vproxyapp.app.cmd.handle.resource;

import vproxyapp.app.cmd.Command;
import vproxyapp.app.cmd.Resource;
import vproxyapp.app.cmd.ResourceType;
import vproxybase.dns.Cache;
import vproxybase.dns.Resolver;

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
        Resolver resolver = Resolver.getDefault();
        return resolver.cacheCount();
    }

    public static List<Cache> detail() {
        List<Cache> caches = new LinkedList<>();
        Resolver.getDefault().copyCache(caches);
        return caches;
    }

    public static void remove(Command cmd) {
        List<Cache> caches = detail();
        String host = cmd.resource.alias;
        for (Cache c : caches) {
            if (c.host.equals(host)) {
                c.remove();
                // there can be no other cache with the same host
                break;
            }
        }
    }
}
