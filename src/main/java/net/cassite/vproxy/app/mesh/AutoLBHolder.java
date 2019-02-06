package net.cassite.vproxy.app.mesh;

import net.cassite.vproxy.component.auto.AutoLB;
import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.NotFoundException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AutoLBHolder {
    private final Map<String, AutoLB> map = new HashMap<>();

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    public void add(String alias, String service, String zone, int port) throws Exception {
        if (map.containsKey(alias))
            throw new AlreadyExistException();
        AutoLB autoLB = new AutoLB(alias, service, zone, port, ServiceMeshMain.getInstance().getAutoConfig());
        map.put(alias, autoLB);
    }

    public AutoLB get(String alias) throws NotFoundException {
        AutoLB autoLB = map.get(alias);
        if (autoLB == null)
            throw new NotFoundException();
        return autoLB;
    }

    public void remove(String alias) throws NotFoundException {
        AutoLB autoLB = map.remove(alias);
        if (autoLB == null)
            throw new NotFoundException();
        autoLB.destroy();
    }
}
