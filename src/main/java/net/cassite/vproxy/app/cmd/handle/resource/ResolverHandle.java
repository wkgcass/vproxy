package net.cassite.vproxy.app.cmd.handle.resource;

import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.app.cmd.Resource;
import net.cassite.vproxy.dns.Resolver;

import java.util.LinkedList;
import java.util.List;

public class ResolverHandle {
    private ResolverHandle() {
    }

    public static void checkResolver(Resource resolver) throws Exception {
        if (resolver.parentResource != null)
            throw new Exception(resolver.type.fullname + " is on top level");
        if (resolver.alias == null || !resolver.alias.equals("(default)"))
            throw new Exception("can only access " + resolver.type.fullname + " named `(default)`");
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
}
