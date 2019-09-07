package vproxy.app.mesh;

import vproxy.component.auto.SmartServiceDelegate;
import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.NotFoundException;
import vproxy.util.IPType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SmartServiceDelegateHolder {
    private final Map<String, SmartServiceDelegate> map = new HashMap<>();

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    public void add(String alias, String service, String zone, String nic, IPType ipType, int exposedPort) throws Exception {
        if (map.containsKey(alias))
            throw new AlreadyExistException();
        SmartServiceDelegate smartServiceDelegate = new SmartServiceDelegate(
            alias, service, zone, nic, ipType, exposedPort, DiscoveryConfigLoader.getInstance().getAutoConfig()
        );
        map.put(alias, smartServiceDelegate);
    }

    public SmartServiceDelegate get(String alias) throws NotFoundException {
        SmartServiceDelegate smartServiceDelegate = map.get(alias);
        if (smartServiceDelegate == null)
            throw new NotFoundException();
        return smartServiceDelegate;
    }

    public void remove(String alias) throws NotFoundException {
        SmartServiceDelegate smartServiceDelegate = map.remove(alias);
        if (smartServiceDelegate == null)
            throw new NotFoundException();
        smartServiceDelegate.destroy();
    }
}
