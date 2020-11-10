package vserver.route;

import vserver.SubPath;
import vserver.RoutingContext;

public class WildcardSubPath implements SubPath {
    private final SubPath next;

    public WildcardSubPath(SubPath next) {
        this.next = next;
    }

    @Override
    public SubPath next() {
        return next;
    }

    @Override
    public boolean match(String route) {
        return true;
    }

    @Override
    public boolean currentSame(SubPath r) {
        return r instanceof WildcardSubPath;
    }

    @Override
    public void fill(RoutingContext ctx, String route) {
    }
}
