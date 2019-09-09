package vproxy.app;

import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.NotFoundException;
import vproxy.component.svrgroup.ServerGroups;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerGroupsHolder {
    private final Map<String, ServerGroups> map = new HashMap<>();

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    public void add(String alias) throws AlreadyExistException {
        if (map.containsKey(alias))
            throw new AlreadyExistException("server-groups", alias);
        ServerGroups serverGroups = new ServerGroups(alias);
        map.put(alias, serverGroups);
    }

    public ServerGroups get(String alias) throws NotFoundException {
        ServerGroups groups = map.get(alias);
        if (groups == null)
            throw new NotFoundException("server-groups", alias);
        return groups;
    }

    public void remove(String alias) throws NotFoundException {
        ServerGroups g = map.remove(alias);
        if (g == null)
            throw new NotFoundException("server-groups", alias);
    }

    void clear() {
        map.clear();
    }

    void put(String alias, ServerGroups sgs) {
        map.put(alias, sgs);
    }
}
