package io.vproxy.vswitch;

import io.vproxy.base.util.Annotations;
import io.vproxy.base.util.Network;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.base.util.exception.XException;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.IPv6;
import io.vproxy.vfd.MacAddress;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SyntheticIpHolder {
    private final Network allowedV4Network;
    private final Network allowedV6Network;
    private final Map<IP, IPMac> ipMap = new HashMap<>();
    private final Map<MacAddress, Set<IPMac>> macMap = new HashMap<>();

    public SyntheticIpHolder(VirtualNetwork network) {
        allowedV4Network = network.v4network;
        allowedV6Network = network.v6network;
    }

    public MacAddress lookup(IP ip) {
        var info = ipMap.get(ip);
        if (info == null) {
            return null;
        }
        return info.mac;
    }

    public IPMac lookupIPMac(IP ip) {
        return ipMap.get(ip);
    }

    public Collection<IP> lookupByMac(MacAddress mac) {
        var set = macMap.get(mac);
        if (set == null) {
            return null;
        }
        return set.stream().map(x -> x.ip).collect(Collectors.toSet());
    }

    public Collection<IPMac> lookupIPMacByMac(MacAddress mac) {
        return macMap.get(mac);
    }

    public Collection<IP> allIps() {
        return ipMap.keySet();
    }

    public Collection<IP> allRoutableIps() {
        Set<IP> ret = new HashSet<>();
        for (var ipmac : ipMap.values()) {
            if (ipmac.routing) {
                ret.add(ipmac.ip);
            }
        }
        return ret;
    }

    public Collection<IPMac> entries() {
        return ipMap.values();
    }

    public IPMac findAnyIPForRouting(boolean ipv6) {
        if (ipv6) {
            return findAnyIPv6ForRouting();
        } else {
            return findAnyIPv4ForRouting();
        }
    }

    public IPMac findAnyIPv4ForRouting() {
        var opt = ipMap.values().stream().filter(ipmac -> ipmac.routing && ipmac.ip instanceof IPv4).findAny();
        return opt.orElse(null);
    }

    public IPMac findAnyIPv6ForRouting() {
        var opt = ipMap.values().stream().filter(ipmac -> ipmac.routing && ipmac.ip instanceof IPv6).findAny();
        return opt.orElse(null);
    }

    public Collection<MacAddress> allMac() {
        return macMap.keySet();
    }

    public IPMac add(IP ip, MacAddress mac, Annotations annotations) throws AlreadyExistException, XException {
        return __add(ip, mac, false, annotations);
    }

    public IPMac __add(IP ip, MacAddress mac, boolean force, Annotations annotations) throws AlreadyExistException, XException {
        if (ip instanceof IPv4) {
            if (!allowedV4Network.contains(ip) && !force) {
                throw new XException("the ip to add (" + ip.formatToIPString() + ") is not in the allowed range " + allowedV4Network);
            }
        } else {
            if (allowedV6Network == null) {
                throw new XException("ipv6 not allowed");
            }
            if (!allowedV6Network.contains(ip) && !force) {
                throw new XException("the ip to add (" + ip.formatToIPString() + ") is not in the allowed range " + allowedV6Network);
            }
        }

        IPMac info = new IPMac(ip, mac, annotations);
        IPMac oldInfo = ipMap.putIfAbsent(ip, info);
        if (oldInfo != null) {
            throw new AlreadyExistException("synthetic ip " + ip.formatToIPString() + " already exists in the requested switch");
        }
        var set = macMap.computeIfAbsent(mac, m -> new HashSet<>());
        set.add(info);
        return info;
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

    public boolean deleteWithCondition(IP ip, Predicate<IPMac> cond) throws NotFoundException {
        var info = ipMap.get(ip);
        if (info == null) {
            throw new NotFoundException("ip", ip.formatToIPString());
        }
        if (cond.test(info)) {
            try {
                del(ip);
            } catch (NotFoundException ignore) {
            }
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "SyntheticIpHolder{" + ipMap.values() + '}';
    }
}
