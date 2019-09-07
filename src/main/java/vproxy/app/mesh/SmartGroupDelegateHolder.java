package vproxy.app.mesh;

import vproxy.component.auto.SmartGroupDelegate;
import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.NotFoundException;
import vproxy.component.svrgroup.ServerGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SmartGroupDelegateHolder {
    private final Map<String, SmartGroupDelegate> map = new HashMap<>();

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    public void add(String alias, String service, String zone, ServerGroup group) throws Exception {
        if (map.containsKey(alias))
            throw new AlreadyExistException();
        SmartGroupDelegate smartGroupDelegate = new SmartGroupDelegate(alias, service, zone, group, DiscoveryConfigLoader.getInstance().getAutoConfig());
        map.put(alias, smartGroupDelegate);
    }

    public SmartGroupDelegate get(String alias) throws NotFoundException {
        SmartGroupDelegate smartGroupDelegate = map.get(alias);
        if (smartGroupDelegate == null)
            throw new NotFoundException();
        return smartGroupDelegate;
    }

    public void remove(String alias) throws NotFoundException {
        SmartGroupDelegate smartGroupDelegate = map.remove(alias);
        if (smartGroupDelegate == null)
            throw new NotFoundException();
        smartGroupDelegate.destroy();
    }
}
