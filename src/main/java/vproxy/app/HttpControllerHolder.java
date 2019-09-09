package vproxy.app;

import vproxy.component.app.HttpController;
import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.NotFoundException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpControllerHolder {
    private final Map<String, HttpController> map = new HashMap<>();

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    public HttpController add(String alias,
                              InetSocketAddress address) throws AlreadyExistException, IOException {
        if (map.containsKey(alias))
            throw new AlreadyExistException("http-controller", alias);
        HttpController rc = new HttpController(alias, address);
        map.put(alias, rc);
        return rc;
    }

    public HttpController get(String alias) throws NotFoundException {
        HttpController rc = map.get(alias);
        if (rc == null)
            throw new NotFoundException("http-controller", alias);
        return rc;
    }

    public void removeAndStop(String alias) throws NotFoundException {
        HttpController g = map.remove(alias);
        if (g == null)
            throw new NotFoundException("http-controller", alias);
        g.stop();
    }
}
