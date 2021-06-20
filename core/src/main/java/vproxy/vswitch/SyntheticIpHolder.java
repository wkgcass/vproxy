package vproxy.vswitch;

import vproxy.base.util.Annotations;
import vproxy.base.util.ConcurrentHashSet;
import vproxy.base.util.Network;
import vproxy.base.util.exception.AlreadyExistException;
import vproxy.base.util.exception.NotFoundException;
import vproxy.base.util.exception.XException;
import vproxy.vfd.IP;
import vproxy.vfd.IPv4;
import vproxy.vfd.MacAddress;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SyntheticIpHolder {
    private final Network allowedV4Network;
    private final Network allowedV6Network;
    private final ConcurrentHashMap<IP, IPMac> ipMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<MacAddress, Set<IPMac>> macMap = new ConcurrentHashMap<>();

    public SyntheticIpHolder(Table table) {
        allowedV4Network = table.v4network;
        allowedV6Network = table.v6network;
    }

    public MacAddress lookup(IP ip) {
        var info = ipMap.get(ip);
        if (info == null) {
            return null;
        }
        return info.mac;
    }

    public Collection<IP> lookupByMac(MacAddress mac) {
        var set = macMap.get(mac);
        if (set == null) {
            return null;
        }
        return set.stream().map(x -> x.ip).collect(Collectors.toSet());
    }

    public Collection<IP> allIps() {
        return ipMap.keySet();
    }

    public Collection<IPMac> entries() {
        return ipMap.values();
    }

    public IPMac findAny() {
        var opt = ipMap.values().stream().findAny();
        return opt.orElse(null);
    }

    public Collection<MacAddress> allMac() {
        return macMap.keySet();
    }

    public void add(IP ip, MacAddress mac, Annotations annotations) throws AlreadyExistException, XException {
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

        IPMac info = new IPMac(ip, mac, annotations);
        IPMac oldInfo = ipMap.putIfAbsent(ip, info);
        if (oldInfo != null) {
            throw new AlreadyExistException("synthetic ip " + ip.formatToIPString() + " already exists in the requested switch");
        }
        var set = macMap.computeIfAbsent(mac, m -> new ConcurrentHashSet<>());
        set.add(info);
    }

    public void del(IP ip) throws NotFoundException {
        IPMac info = ipMap.remove(ip);
        if (info == null) {
            throw new NotFoundException("ip", ip.formatToIPString());
        }
        var set = macMap.get(info.mac);
        assert set != null;
        set.remove(info);
        if (set.isEmpty()) {
            macMap.remove(info.mac);
        }
    }
}
