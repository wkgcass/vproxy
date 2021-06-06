package vproxy.vserver.route;

import vproxy.vserver.RoutingContext;
import vproxy.vserver.SubPath;

public class VariableSubPath implements SubPath {
    private final SubPath next;
    private final String variable;

    public VariableSubPath(SubPath next, String variable) {
        this.next = next;
        this.variable = variable;
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
        return r instanceof VariableSubPath && ((VariableSubPath) r).variable.equals(variable);
    }

    @Override
    public void fill(RoutingContext ctx, String route) {
        ctx.putParam(variable, route);
    }
}
