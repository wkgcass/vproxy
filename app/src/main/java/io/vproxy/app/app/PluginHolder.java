package io.vproxy.app.app;

import io.vproxy.app.plugin.Plugin;
import io.vproxy.app.plugin.PluginInitParams;
import io.vproxy.app.plugin.PluginLoader;
import io.vproxy.app.plugin.PluginWrapper;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.NotFoundException;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PluginHolder {
    private final Map<String, PluginWrapper> map = new HashMap<>();

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    @SuppressWarnings("DuplicateThrows")
    public PluginWrapper add(String alias, URL[] urls, String classname, String[] args) throws AlreadyExistException, Exception {
        if (map.containsKey(alias)) {
            throw new AlreadyExistException("plugin", alias);
        }
        Plugin plugin = PluginLoader.load(urls, classname);
        PluginWrapper wrapper = new PluginWrapper(alias, urls, args, plugin);
        try {
            wrapper.plugin.init(createPluginParams(args));
        } catch (Exception e) {
            throw new Exception("init plugin failed, err: " + e.getMessage(), e);
        }
        map.put(wrapper.alias, wrapper);
        Logger.alert("plugin " + wrapper.alias + " loaded");
        return wrapper;
    }

    // plugin.init will not be called in this method
    public PluginWrapper register(String alias, Plugin plugin) throws AlreadyExistException {
        if (map.containsKey(alias)) {
            throw new AlreadyExistException("plugin", alias);
        }
        PluginWrapper wrapper = new PluginWrapper(alias, new URL[0], new String[0], plugin);
        map.put(wrapper.alias, wrapper);
        Logger.alert("plugin " + wrapper.alias + " registered");
        return wrapper;
    }

    private PluginInitParams createPluginParams(String[] args) {
        return new PluginInitParams(args);
    }

    public PluginWrapper get(String alias) throws NotFoundException {
        PluginWrapper plugin = map.get(alias);
        if (plugin == null)
            throw new NotFoundException("plugin", alias);
        return plugin;
    }

    public void unload(String alias) throws NotFoundException {
        PluginWrapper plugin = map.remove(alias);
        if (plugin == null)
            throw new NotFoundException("plugin", alias);
        plugin.disable();
        plugin.plugin.destroy();
        Logger.alert("plugin " + alias + " unloaded");
    }
}
