package vproxy.vserver;

import vproxy.vserver.route.FixedSubPath;
import vproxy.vserver.route.VariableSubPath;
import vproxy.vserver.route.WildcardSubPath;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public interface SubPath {
    static SubPath create(String path) {
        List<String> paths = Arrays.stream(path.split("/")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        SubPath next = null;
        for (int i = paths.size() - 1; i >= 0; --i) {
            String p = paths.get(i);
            if (p.equals("*")) {
                next = new WildcardSubPath(next);
            } else if (p.startsWith(":")) {
                next = new VariableSubPath(next, p.substring(1));
            } else {
                next = new FixedSubPath(next, p);
            }
        }
        return next;
    }

    SubPath next();

    boolean match(String route);

    boolean currentSame(SubPath r);

    void fill(RoutingContext ctx, String route);
}
