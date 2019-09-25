package vserver.route;

import vserver.Route;
import vserver.RoutingContext;

public class WildcardRoute implements Route {
    private final Route next;

    public WildcardRoute(Route next) {
        this.next = next;
    }

    @Override
    public Route next() {
        return next;
    }

    @Override
    public boolean match(String route) {
        return true;
    }

    @Override
    public void fill(RoutingContext ctx, String route) {

    }
}
