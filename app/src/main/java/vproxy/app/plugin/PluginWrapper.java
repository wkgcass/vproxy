package vproxy.app.plugin;

import vproxy.base.util.Logger;

import java.net.URL;

public class PluginWrapper {
    public final String alias;
    public final URL[] urls;
    public final Plugin plugin;
    private boolean enabled;

    public PluginWrapper(String alias, URL[] urls, Plugin plugin) {
        this.alias = alias;
        this.urls = urls;
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void enable() {
        if (enabled) {
            return;
        }
        plugin.start();
        Logger.alert("plugin " + alias + " enabled");
        enabled = true;
    }

    public void disable() {
        if (!enabled) {
            return;
        }
        plugin.stop();
        Logger.alert("plugin " + alias + " disabled");
        enabled = false;
    }

    @Override
    public String toString() {
        return alias + " ->" +
            " id " + plugin.id() +
            " class " + plugin.getClass().getName() +
            " " + (enabled ? "enabled" : "disabled");
    }
}
