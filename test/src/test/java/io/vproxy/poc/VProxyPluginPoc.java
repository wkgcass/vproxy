package io.vproxy.poc;

import io.vproxy.app.plugin.Plugin;
import io.vproxy.app.plugin.PluginInitParams;
import io.vproxy.base.util.Logger;

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
