package io.vproxy.app.app;

import io.vproxy.base.component.check.HealthCheckConfig;
import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.component.svrgroup.Method;
import io.vproxy.base.component.svrgroup.ServerGroup;
import io.vproxy.base.util.Annotations;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.ClosedException;
import io.vproxy.base.util.exception.NotFoundException;

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
                           Annotations annotations) throws AlreadyExistException, ClosedException {
        if (map.containsKey(alias))
            throw new AlreadyExistException("server-group", alias);
        ServerGroup serverGroup = new ServerGroup(alias, eventLoopGroup, healthCheckConfig, method);
        serverGroup.setAnnotations(annotations);
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
}
