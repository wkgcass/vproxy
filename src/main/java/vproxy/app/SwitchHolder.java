package vproxy.app;

import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.ClosedException;
import vproxy.component.exception.NotFoundException;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.svrgroup.Upstream;
import vproxy.dns.DNSServer;
import vswitch.Switch;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SwitchHolder {
    private final Map<String, Switch> map = new HashMap<>();

    public List<String> names() {
        return new ArrayList<>(map.keySet());
    }

    public void add(String alias,
                    InetSocketAddress vxlanBindingAddress,
                    String password,
                    EventLoopGroup eventLoopGroup,
                    int macTableTimeout,
                    int arpTableTimeout) throws AlreadyExistException, ClosedException, IOException {
        if (map.containsKey(alias))
            throw new AlreadyExistException("switch", alias);

        Switch sw = new Switch(alias, vxlanBindingAddress, password, eventLoopGroup, macTableTimeout, arpTableTimeout);
        try {
            sw.start();
        } catch (IOException e) {
            sw.destroy();
            throw e;
        }
        map.put(alias, sw);
    }

    public Switch get(String alias) throws NotFoundException {
        Switch sw = map.get(alias);
        if (sw == null)
            throw new NotFoundException("switch", alias);
        return sw;
    }

    public void removeAndStop(String alias) throws NotFoundException {
        Switch sw = map.remove(alias);
        if (sw == null)
            throw new NotFoundException("switch", alias);
        sw.destroy();
    }
}
