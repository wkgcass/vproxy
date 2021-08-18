package vproxy.app.plugin;

import vproxy.base.util.Utils;

public class PluginInitParams {
    public final String[] arguments;

    public PluginInitParams(String[] arguments) {
        this.arguments = arguments;
    }

    @Override
    public String toString() {
        return "PluginInitParams(" +
            "arguments=" + Utils.formatArrayToStringCompact(arguments) +
            ")";
    }
}
