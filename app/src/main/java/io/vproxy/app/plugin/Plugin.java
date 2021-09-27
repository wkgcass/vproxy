package io.vproxy.app.plugin;

public interface Plugin {
    String id();

    void init(PluginInitParams params) throws Exception;

    void start();

    void stop();

    void destroy();
}
