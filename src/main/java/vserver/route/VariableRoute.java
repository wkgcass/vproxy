package vserver.route;

import vserver.Route;
import vserver.RoutingContext;

public class VariableRoute implements Route {
    private final Route next;
    private final String variable;

    public VariableRoute(Route next, String variable) {
        this.next = next;
        this.variable = variable;
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
        ctx.putParam(variable, route);
    }
}
