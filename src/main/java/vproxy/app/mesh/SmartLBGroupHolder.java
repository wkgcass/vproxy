package vproxy.app.mesh;

import vproxy.component.app.TcpLB;
import vproxy.component.auto.SmartLBGroup;
import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.NotFoundException;
import vproxy.component.svrgroup.ServerGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SmartLBGroupHolder {
    private final Map<String, SmartLBGroup> map = new HashMap<>();

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    public void add(String alias, String service, String zone, TcpLB lb, ServerGroup group) throws Exception {
        if (map.containsKey(alias))
            throw new AlreadyExistException();
        SmartLBGroup smartLBGroup = new SmartLBGroup(alias, service, zone, lb, group, ServiceMeshMain.getInstance().getAutoConfig());
        map.put(alias, smartLBGroup);
    }

    public SmartLBGroup get(String alias) throws NotFoundException {
        SmartLBGroup smartLBGroup = map.get(alias);
        if (smartLBGroup == null)
            throw new NotFoundException();
        return smartLBGroup;
    }

    public void remove(String alias) throws NotFoundException {
        SmartLBGroup smartLBGroup = map.remove(alias);
        if (smartLBGroup == null)
            throw new NotFoundException();
        smartLBGroup.destroy();
    }
}
