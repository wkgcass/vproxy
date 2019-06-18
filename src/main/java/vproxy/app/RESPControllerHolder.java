package vproxy.app;

import vproxy.component.app.RESPController;
import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.NotFoundException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RESPControllerHolder {
    private final Map<String, RESPController> map = new HashMap<>();

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    public RESPController add(String alias,
                              InetSocketAddress address,
                              byte[] password) throws AlreadyExistException, IOException {
        if (map.containsKey(alias))
            throw new AlreadyExistException();
        RESPController rc = new RESPController(alias, address, password);
        map.put(alias, rc);
        return rc;
    }

    public RESPController get(String alias) throws NotFoundException {
        RESPController rc = map.get(alias);
        if (rc == null)
            throw new NotFoundException();
        return rc;
    }

    public void removeAndStop(String alias) throws NotFoundException {
        RESPController g = map.remove(alias);
        if (g == null)
            throw new NotFoundException();
        g.stop();
    }
}
