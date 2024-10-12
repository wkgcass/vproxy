package io.vproxy.app.app;

import io.vproxy.app.controller.HttpController;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.vfd.IPPort;

import java.io.IOException;
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
                              IPPort address,
                              Boolean cors,
                              String secret) throws AlreadyExistException, IOException {
        if (map.containsKey(alias))
            throw new AlreadyExistException("http-controller", alias);
        HttpController rc = new HttpController(alias, address, cors, secret);
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
