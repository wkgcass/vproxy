package net.cassite.vproxy.app;

import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.component.svrgroup.ServerGroups;

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
            throw new AlreadyExistException();
        ServerGroups serverGroups = new ServerGroups(alias);
        map.put(alias, serverGroups);
    }

    public ServerGroups get(String alias) throws NotFoundException {
        ServerGroups groups = map.get(alias);
        if (groups == null)
            throw new NotFoundException();
        return groups;
    }

    public void remove(String alias) throws NotFoundException {
        ServerGroups g = map.remove(alias);
        if (g == null)
            throw new NotFoundException();
    }

    void clear() {
        map.clear();
    }

    void put(String alias, ServerGroups sgs) {
        map.put(alias, sgs);
    }
}
