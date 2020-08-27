package vproxyapp.app;

import vfd.IPPort;
import vfd.UDSPath;
import vproxyapp.controller.DockerNetworkPluginController;
import vproxyapp.controller.HttpController;
import vproxybase.util.exception.AlreadyExistException;
import vproxybase.util.exception.NotFoundException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DockerNetworkPluginControllerHolder {
    private final Map<String, DockerNetworkPluginController> map = new HashMap<>();

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    public DockerNetworkPluginController add(String alias,
                                             UDSPath path) throws AlreadyExistException, IOException {
        if (map.containsKey(alias))
            throw new AlreadyExistException("docker-network-plugin-controller", alias);
        DockerNetworkPluginController rc = new DockerNetworkPluginController(alias, path);
        map.put(alias, rc);
        return rc;
    }

    public DockerNetworkPluginController get(String alias) throws NotFoundException {
        DockerNetworkPluginController rc = map.get(alias);
        if (rc == null)
            throw new NotFoundException("docker-network-plugin-controller", alias);
        return rc;
    }

    public void removeAndStop(String alias) throws NotFoundException {
        DockerNetworkPluginController g = map.remove(alias);
        if (g == null)
            throw new NotFoundException("docker-network-plugin-controller", alias);
        g.stop();
    }
}
