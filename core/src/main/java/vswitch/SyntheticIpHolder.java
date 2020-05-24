package vswitch;

import vfd.IP;
import vfd.IPv4;
import vproxybase.util.exception.AlreadyExistException;
import vproxybase.util.exception.NotFoundException;
import vproxybase.util.exception.XException;
import vproxybase.util.ConcurrentHashSet;
import vproxybase.util.Network;
import vfd.MacAddress;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SyntheticIpHolder {
    private final Network allowedV4Network;
    private final Network allowedV6Network;
    private final ConcurrentHashMap<IP, MacAddress> ipMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<MacAddress, Set<IP>> macMap = new ConcurrentHashMap<>();

    public SyntheticIpHolder(Table table) {
        allowedV4Network = table.v4network;
        allowedV6Network = table.v6network;
    }

    public MacAddress lookup(IP ip) {
        return ipMap.get(ip);
    }

    public Collection<IP> lookupByMac(MacAddress mac) {
        return macMap.get(mac);
    }

    public Collection<IP> allIps() {
        return ipMap.keySet();
    }

    public Collection<Map.Entry<IP, MacAddress>> entries() {
        return ipMap.entrySet();
    }

    public void add(IP ip, MacAddress mac) throws AlreadyExistException, XException {
        if (ip instanceof IPv4) {
            if (!allowedV4Network.contains(ip)) {
                throw new XException("the ip to add (" + ip.formatToIPString() + ") is not in the allowed range " + allowedV4Network);
            }
        } else {
            if (allowedV6Network == null) {
                throw new XException("ipv6 not allowed");
            }
            if (!allowedV6Network.contains(ip)) {
                throw new XException("the ip to add (" + ip.formatToIPString() + ") is not in the allowed range " + allowedV6Network);
            }
        }

        MacAddress oldMac = ipMap.putIfAbsent(ip, mac);
        if (oldMac != null) {
            throw new AlreadyExistException("synthetic ip " + ip.formatToIPString() + " already exists in the requested switch");
        }
        var set = macMap.computeIfAbsent(mac, m -> new ConcurrentHashSet<>());
        set.add(ip);
    }

    public void del(IP ip) throws NotFoundException {
        MacAddress mac = ipMap.remove(ip);
        if (mac == null) {
            throw new NotFoundException("ip", ip.formatToIPString());
        }
        var set = macMap.get(mac);
        assert set != null;
        set.remove(ip);
        if (set.isEmpty()) {
            macMap.remove(mac);
        }
    }
}
