package io.vproxy.app.app;

import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.util.Annotations;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.msquic.MsQuicInitializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventLoopGroupHolder {
    final Map<String, EventLoopGroup> map = new HashMap<>();

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    public void add(String alias) throws Exception {
        add(alias, new Annotations());
    }

    public void add(String alias, Annotations annotations) throws Exception {
        if (map.containsKey(alias))
            throw new AlreadyExistException("event-loop-group", alias);
        if (annotations.EventLoopGroup_UseMsQuic) {
            if (!MsQuicInitializer.isSupported()) {
                throw new Exception("msquic is not supported");
            }
        }
        EventLoopGroup group = new EventLoopGroup(alias, annotations);
        map.put(alias, group);
    }

    public EventLoopGroup get(String alias) throws NotFoundException {
        EventLoopGroup group = map.get(alias);
        if (group == null)
            throw new NotFoundException("event-loop-group", alias);
        return group;
    }

    public void removeAndClose(String alias) throws NotFoundException {
        EventLoopGroup g = map.remove(alias);
        if (g == null)
            throw new NotFoundException("event-loop-group", alias);
        g.close();
    }
}
