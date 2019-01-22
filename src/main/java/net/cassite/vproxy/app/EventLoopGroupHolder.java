package net.cassite.vproxy.app;

import net.cassite.vproxy.component.elgroup.EventLoopGroup;
import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.NotFoundException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventLoopGroupHolder {
    private final Map<String, EventLoopGroup> map = new HashMap<>();

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    public void add(String alias) throws AlreadyExistException {
        if (map.containsKey(alias))
            throw new AlreadyExistException();
        EventLoopGroup group = new EventLoopGroup(alias);
        map.put(alias, group);
    }

    public EventLoopGroup get(String alias) throws NotFoundException {
        EventLoopGroup group = map.get(alias);
        if (group == null)
            throw new NotFoundException();
        return group;
    }

    public void removeAndClose(String alias) throws NotFoundException {
        EventLoopGroup g = map.remove(alias);
        if (g == null)
            throw new NotFoundException();
        g.close();
    }
}
