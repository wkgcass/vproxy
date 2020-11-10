package vserver.route;

import vserver.SubPath;
import vserver.RoutingContext;

public class FixedSubPath implements SubPath {
    private final SubPath next;
    private final String route;

    public FixedSubPath(SubPath next, String route) {
        this.next = next;
        this.route = route;
    }

    @Override
    public SubPath next() {
        return next;
    }

    @Override
    public boolean match(String route) {
        return this.route.equals(route);
    }

    @Override
    public boolean currentSame(SubPath r) {
        return r instanceof FixedSubPath && ((FixedSubPath) r).route.equals(route);
    }

    @Override
    public void fill(RoutingContext ctx, String route) {
    }
}
