package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Param;
import vproxy.app.controller.DockerNetworkPluginController;
import vproxy.vfd.UDSPath;

import java.util.ArrayList;
import java.util.List;

public class DockerNetworkPluginControllerHandle {
    private DockerNetworkPluginControllerHandle() {
    }

    public static void add(Command cmd) throws Exception {
        String path = cmd.args.get(Param.path);
        Application.get().dockerNetworkPluginControllerHolder.add(cmd.resource.alias, new UDSPath(path));
    }

    public static List<String> names() {
        return Application.get().dockerNetworkPluginControllerHolder.names();
    }

    public static List<DockerNetworkPluginController> details() throws Exception {
        var names = names();
        List<DockerNetworkPluginController> ret = new ArrayList<>(names.size());
        for (var name : names) {
            ret.add(Application.get().dockerNetworkPluginControllerHolder.get(name));
        }
        return ret;
    }

    public static void removeAndStop(Command cmd) throws Exception {
        Application.get().dockerNetworkPluginControllerHolder.removeAndStop(cmd.resource.alias);
    }
}
