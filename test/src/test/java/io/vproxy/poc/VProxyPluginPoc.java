package vproxy.poc;

import vproxy.app.plugin.Plugin;
import vproxy.app.plugin.PluginInitParams;
import vproxy.base.util.Logger;

public class VProxyPluginPoc implements Plugin {
    @Override
    public String id() {
        return "vproxy-plugin-poc";
    }

    @Override
    public void init(PluginInitParams params) throws Exception {
        Logger.alert("plugin init(" + params + ")");
    }

    @Override
    public void start() {
        Logger.alert("plugin start()");
    }

    @Override
    public void stop() {
        Logger.alert("plugin stop()");
    }

    @Override
    public void destroy() {
        Logger.alert("plugin destroy()");
    }
}
