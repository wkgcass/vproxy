package vswitch;

import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.NotFoundException;
import vproxy.util.ConcurrentHashSet;
import vproxy.util.Utils;
import vswitch.util.MacAddress;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SyntheticIpHolder {
    private final ConcurrentHashMap<InetAddress, MacAddress> ipMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<MacAddress, Set<InetAddress>> macMap = new ConcurrentHashMap<>();

    public MacAddress lookup(InetAddress ip) {
        return ipMap.get(ip);
    }

    public Collection<InetAddress> lookupByMac(MacAddress mac) {
        return macMap.get(mac);
    }

    public Collection<InetAddress> allIps() {
        return ipMap.keySet();
    }

    public Collection<Map.Entry<InetAddress, MacAddress>> entries() {
        return ipMap.entrySet();
    }

    public void add(InetAddress ip, MacAddress mac) throws AlreadyExistException {
        MacAddress oldMac = ipMap.putIfAbsent(ip, mac);
        if (oldMac != null) {
            throw new AlreadyExistException("synthetic ip " + Utils.ipStr(ip) + " already exists in the requested switch");
        }
        var set = macMap.computeIfAbsent(mac, m -> new ConcurrentHashSet<>());
        set.add(ip);
    }

    public void del(InetAddress ip) throws NotFoundException {
        MacAddress mac = ipMap.remove(ip);
        if (mac == null) {
            throw new NotFoundException("ip", Utils.ipStr(ip));
        }
        var set = macMap.get(mac);
        assert set != null;
        set.remove(ip);
        if (set.isEmpty()) {
            macMap.remove(mac);
        }
    }
}
