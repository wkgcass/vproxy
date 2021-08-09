package vproxy.app.app;

import vproxy.app.plugin.Plugin;
import vproxy.app.plugin.PluginInitParams;
import vproxy.app.plugin.PluginLoader;
import vproxy.app.plugin.PluginWrapper;
import vproxy.base.util.exception.AlreadyExistException;
import vproxy.base.util.exception.NotFoundException;

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
    public PluginWrapper add(String alias, URL[] urls, String classname) throws AlreadyExistException, Exception {
        if (map.containsKey(alias)) {
            throw new AlreadyExistException("plugin", alias);
        }
        Plugin plugin = PluginLoader.load(urls, classname);
        PluginWrapper wrapper = new PluginWrapper(alias, plugin);
        try {
            wrapper.plugin.init(createPluginParams());
        } catch (Exception e) {
            throw new Exception("init plugin failed, err: " + e.getMessage(), e);
        }
        map.put(wrapper.alias, wrapper);
        return wrapper;
    }

    private PluginInitParams createPluginParams() {
        return new PluginInitParams();
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
    }
}
