package vproxy.app;

import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.NotFoundException;
import vproxy.component.svrgroup.Upstream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpstreamHolder {
    private final Map<String, Upstream> map = new HashMap<>();

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    public void add(String alias) throws AlreadyExistException {
        if (map.containsKey(alias))
            throw new AlreadyExistException("upstream", alias);
        Upstream upstream = new Upstream(alias);
        map.put(alias, upstream);
    }

    public Upstream get(String alias) throws NotFoundException {
        Upstream groups = map.get(alias);
        if (groups == null)
            throw new NotFoundException("upstream", alias);
        return groups;
    }

    public void remove(String alias) throws NotFoundException {
        Upstream g = map.remove(alias);
        if (g == null)
            throw new NotFoundException("upstream", alias);
    }
}
