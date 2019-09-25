package vserver.route;

import vserver.Route;
import vserver.RoutingContext;

public class FixedRoute implements Route {
    private final Route next;
    private final String route;

    public FixedRoute(Route next, String route) {
        this.next = next;
        this.route = route;
    }

    @Override
    public Route next() {
        return next;
    }

    @Override
    public boolean match(String route) {
        return this.route.equals(route);
    }

    @Override
    public void fill(RoutingContext ctx, String route) {
    }
}
