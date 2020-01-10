package vproxy.app;

import vproxy.component.check.HealthCheckConfig;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.ClosedException;
import vproxy.component.exception.NotFoundException;
import vproxy.component.svrgroup.Method;
import vproxy.component.svrgroup.ServerGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerGroupHolder {
    private final Map<String, ServerGroup> map = new HashMap<>();

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    public ServerGroup add(String alias,
                           EventLoopGroup eventLoopGroup,
                           HealthCheckConfig healthCheckConfig,
                           Method method,
                           Map<String, String> annotations) throws AlreadyExistException, ClosedException {
        if (map.containsKey(alias))
            throw new AlreadyExistException("server-group", alias);
        ServerGroup serverGroup = new ServerGroup(alias, eventLoopGroup, healthCheckConfig, method);
        serverGroup.annotations = annotations;
        map.put(alias, serverGroup);
        return serverGroup;
    }

    public ServerGroup get(String alias) throws NotFoundException {
        ServerGroup group = map.get(alias);
        if (group == null)
            throw new NotFoundException("server-group", alias);
        return group;
    }

    public void removeAndClear(String alias) throws NotFoundException {
        ServerGroup g = map.remove(alias);
        if (g == null)
            throw new NotFoundException("server-group", alias);
        g.destroy();
    }

    void clear() {
        map.clear();
    }

    void put(String alias, ServerGroup sg) {
        map.put(alias, sg);
    }
}
