package vproxy.app.app;

import vproxy.app.controller.DockerNetworkPluginController;
import vproxy.base.util.exception.AlreadyExistException;
import vproxy.base.util.exception.NotFoundException;
import vproxy.vfd.UDSPath;

import java.io.IOException;

public class DockerNetworkPluginControllerHolder {
    private DockerNetworkPluginController controller = null;

    public DockerNetworkPluginController getController() {
        return controller;
    }

    public DockerNetworkPluginController create(UDSPath path, boolean requireSync) throws AlreadyExistException, IOException {
        if (controller != null)
            throw new AlreadyExistException("docker-network-plugin-controller");
        DockerNetworkPluginController dnpc = new DockerNetworkPluginController(path, requireSync);
        controller = dnpc;
        return dnpc;
    }

    public void stop() throws NotFoundException {
        DockerNetworkPluginController g = controller;
        controller = null;
        if (g == null)
            throw new NotFoundException("docker-network-plugin-controller");
        g.stop();
    }
}
