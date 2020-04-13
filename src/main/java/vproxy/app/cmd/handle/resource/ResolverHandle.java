package vproxy.app.cmd.handle.resource;

import vproxy.app.cmd.Resource;
import vproxy.component.exception.XException;

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
