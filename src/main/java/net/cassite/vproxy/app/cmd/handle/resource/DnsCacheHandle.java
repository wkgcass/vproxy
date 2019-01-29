package net.cassite.vproxy.app.cmd.handle.resource;

import net.cassite.vproxy.app.cmd.Resource;

public class DnsCacheHandle {
    private DnsCacheHandle() {
    }

    public static void checkDnsCache(Resource dnscache) throws Exception {
        if (dnscache.parentResource == null)
            throw new Exception("cannot find " + dnscache.type.fullname + " on top level");
        ResolverHandle.checkResolver(dnscache.parentResource);
    }
}
