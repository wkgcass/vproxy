package io.vproxy.app.app.cmd.handle.resource;

import io.vproxy.app.app.cmd.Resource;
import io.vproxy.base.util.exception.XException;

public class ResolverHandle {
    private ResolverHandle() {
    }

    public static void checkResolver(Resource resolver) throws Exception {
        if (resolver.parentResource != null)
            throw new Exception(resolver.type.fullname + " is on top level");
        if (resolver.alias == null || !resolver.alias.equals("(default)"))
            throw new XException("can only access " + resolver.type.fullname + " named `(default)`");
    }
}
