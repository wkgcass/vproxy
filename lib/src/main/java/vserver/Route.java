package vserver;

import vserver.route.FixedRoute;
import vserver.route.VariableRoute;
import vserver.route.WildcardRoute;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public interface Route {
    static Route create(String path) {
        List<String> paths = Arrays.stream(path.split("/")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        Route next = null;
        for (int i = paths.size() - 1; i >= 0; --i) {
            String p = paths.get(i);
            if (p.equals("*")) {
                next = new WildcardRoute(next);
            } else if (p.startsWith(":")) {
                next = new VariableRoute(next, p.substring(1));
            } else {
                next = new FixedRoute(next, p);
            }
        }
        return next;
    }

    Route next();

    boolean match(String route);

    boolean currentSame(Route r);

    void fill(RoutingContext ctx, String route);
}
