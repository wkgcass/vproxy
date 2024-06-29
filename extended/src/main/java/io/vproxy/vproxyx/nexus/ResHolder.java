package io.vproxy.vproxyx.nexus;

import io.vproxy.vproxyx.nexus.entity.ProxyInstance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class ResHolder {
    private final LinkedHashMap<String, ProxyInstance> proxies = new LinkedHashMap<>();

    public List<ProxyInstance> getProxies() {
        return new ArrayList<>(proxies.values());
    }

    public boolean containsProxy(String id) {
        return proxies.containsKey(id);
    }

    public ProxyInstance getProxy(String id) {
        return proxies.get(id);
    }

    public void putProxy(ProxyInstance proxyInst) {
        proxies.put(proxyInst.id, proxyInst);
    }

    public ProxyInstance removeProxy(String id) {
        var proxy = proxies.remove(id);
        if (proxy == null) {
            return null;
        }
        proxy.close();
        return proxy;
    }
}
