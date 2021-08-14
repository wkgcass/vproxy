package vproxy.app.app.cmd.handle.resource;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.Flag;
import vproxy.app.app.cmd.Param;
import vproxy.app.app.cmd.handle.param.URLHandle;
import vproxy.app.plugin.PluginWrapper;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class PluginHandle {
    private PluginHandle() {
    }

    public static void add(Command cmd) throws Exception {
        URL[] urls = URLHandle.get(cmd);
        String cls = cmd.args.get(Param.cls);
        Application.get().pluginHolder.add(cmd.resource.alias, urls, cls);
    }

    public static List<String> names() {
        return Application.get().pluginHolder.names();
    }

    public static List<PluginWrapper> details() throws Exception {
        var names = names();
        List<PluginWrapper> ret = new ArrayList<>(names.size());
        for (var name : names) {
            ret.add(Application.get().pluginHolder.get(name));
        }
        return ret;
    }

    public static void update(Command cmd) throws Exception {
        if (cmd.flags.contains(Flag.enable)) {
            Application.get().pluginHolder.get(cmd.resource.alias).enable();
        }
        if (cmd.flags.contains(Flag.disable)) {
            Application.get().pluginHolder.get(cmd.resource.alias).disable();
        }
    }

    public static void unload(Command cmd) throws Exception {
        Application.get().pluginHolder.unload(cmd.resource.alias);
    }
}
