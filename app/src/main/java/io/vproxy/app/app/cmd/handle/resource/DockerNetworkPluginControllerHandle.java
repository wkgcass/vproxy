package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.controller.DockerNetworkPluginController;

import java.util.Collections;
import java.util.List;

public class DockerNetworkPluginControllerHandle {
    private DockerNetworkPluginControllerHandle() {
    }

    public static List<String> names() {
        var controller = Application.get().dockerNetworkPluginControllerHolder.getController();
        return controller == null ? Collections.emptyList() : Collections.singletonList("docker-network-plugin-controller");
    }

    public static List<DockerNetworkPluginController> details() throws Exception {
        var controller = Application.get().dockerNetworkPluginControllerHolder.getController();
        return controller == null ? Collections.emptyList() : Collections.singletonList(controller);
    }
}
