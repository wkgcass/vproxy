package vproxy.app.mesh;

import vproxy.component.auto.SmartNodeDelegate;
import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.NotFoundException;
import vproxy.util.IPType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SmartNodeDelegateHolder {
    private final Map<String, SmartNodeDelegate> map = new HashMap<>();

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    public void add(String alias, String service, String zone, String nic, IPType ipType, int exposedPort, int weight) throws Exception {
        if (map.containsKey(alias))
            throw new AlreadyExistException("smart-node-delegate", alias);
        SmartNodeDelegate smartNodeDelegate = new SmartNodeDelegate(
            alias, service, zone, nic, ipType, exposedPort, weight, DiscoveryConfigLoader.getInstance().getAutoConfig()
        );
        map.put(alias, smartNodeDelegate);
    }

    public SmartNodeDelegate get(String alias) throws NotFoundException {
        SmartNodeDelegate smartNodeDelegate = map.get(alias);
        if (smartNodeDelegate == null)
            throw new NotFoundException("smart-node-delegate", alias);
        return smartNodeDelegate;
    }

    public void remove(String alias) throws NotFoundException {
        SmartNodeDelegate smartNodeDelegate = map.remove(alias);
        if (smartNodeDelegate == null)
            throw new NotFoundException("smart-node-delegate", alias);
        smartNodeDelegate.destroy();
    }
}
