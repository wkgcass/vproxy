package io.vproxy.vproxyx.websocks.uot;

import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Network;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.component.secure.SecurityGroup;
import io.vproxy.vfd.*;
import io.vproxy.vswitch.RouteTable;
import io.vproxy.vswitch.Switch;
import io.vproxy.vswitch.iface.XDPIface;
import io.vproxy.xdp.*;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.List;
import java.util.function.Function;

public class UdpOverTcpSetup {
    private UdpOverTcpSetup() {
    }

    // port: not used if client == true
    // port: the kcp port if client == false
    public static FDs setup(boolean client, int port, String nicname, EventLoopGroup elg) throws Exception {
        var nic = selectNic(nicname);
        var localMac = new MacAddress(nic.getHardwareAddress());
        var ips = nic.getInterfaceAddresses();
        var localIp4 = getIPv4(ips);
        var localIp6 = getIPv6(ips);

        Switch sw = new Switch("uot",
            new IPPort("255.255.255.255:0"),
            elg, 300_000, 4 * 3600 * 1000,
            SecurityGroup.denyAll());
        sw.defaultIfaceParams.setBaseMTU(-1);
        sw.defaultIfaceParams.setFloodAllowed(false);
        sw.start();

        IP ip4 = IP.from(localIp4.getAddress().getAddress());
        Network v4net = Network.eraseToNetwork(ip4, localIp4.getNetworkPrefixLength());
        IP ip6 = null;
        Network v6net = null;
        if (localIp6 != null) {
            ip6 = IP.from(localIp6.getAddress().getAddress());
            v6net = Network.eraseToNetwork(ip6, localIp6.getNetworkPrefixLength());
        }

        var network = sw.addNetwork(1, v4net, v6net, null);
        network.addIp(ip4, localMac, null);
        Logger.alert("handling " + ip4.formatToIPString() + " :: " + localMac + " on " + nicname);
        if (ip6 != null) {
            network.addIp(ip6, localMac, null);
            Logger.alert("handling " + ip6.formatToIPString() + " :: " + localMac + " on " + nicname);
        }

        Tuple<MacAddress, IP> v4gw = getIPv4GW(v4net);
        Tuple<MacAddress, IP> v6gw = null;
        if (v6net != null) {
            v6gw = getIPv6GW(v6net);
        }

        network.routeTable.addRule(new RouteTable.RouteRule("gw", Network.from("0.0.0.0/0"), v4gw.right));
        Logger.alert("adding default route via " + v4gw.right.formatToIPString());
        if (v6gw != null) {
            network.routeTable.addRule(new RouteTable.RouteRule("gw6", Network.from("::/0"), v6gw.right));
            Logger.alert("adding default route via " + v6gw.right.formatToIPString());
        }

        network.arpTable.record(v4gw.left, v4gw.right, true);
        Logger.alert("adding persistent arp entry for v4 gateway " + v4gw.left + " :: " + v4gw.right);
        if (v6gw != null) {
            network.arpTable.record(v6gw.left, v6gw.right, true);
            Logger.alert("adding persistent arp entry for v6 gateway " + v6gw.left + " :: " + v6gw.right);
        }

        BPFObject obj = BPFObject.loadAndAttachToNic(Prebuilt.HANDLE_DST_PORT_EBPF_OBJECT, nicname, null, BPFMode.SKB, true);
        BPFMap xskMap = obj.getMap(Prebuilt.DEFAULT_XSKS_MAP_NAME);
        BPFMap portsArray = obj.getMap(Prebuilt.HANDLE_DST_PORT_MAP_NAME);

        if (client) {
            for (int p = 30720; p <= 32767; ++p) {
                portsArray.put(p, (byte) 1);
            }
        } else {
            portsArray.put(port, (byte) 1);
        }

        UMem umem = sw.addUMem("umem0", 256, 128, 128, 2048);
        XDPIface iface = sw.addXDP(nicname, 1, umem,
            new XDPIface.XDPParams(0, 128, 128, BPFMode.SKB,
                false, 0, false, false,
                new XDPIface.BPFInfo(obj, xskMap)));

        UdpOverTcpPacketFilter filter = new UdpOverTcpPacketFilter(client);
        iface.addIngressFilter(filter);
        iface.addEgressFilter(filter);

        network.macTable.record(v4gw.left, iface, true);
        Logger.alert("adding persistent mac entry for v4 gateway " + v4gw.left);
        if (v6gw != null) {
            network.macTable.record(v6gw.left, iface, true);
            Logger.alert("adding persistent mac entry for v6 gateway " + v6gw.left);
        }

        return network.fds();
    }

    public static Tuple<IPv4, IPv6> chooseIPs(String nicname) throws Exception {
        var nic = selectNic(nicname);
        var v4 = getIPv4(nic.getInterfaceAddresses());
        var v6 = getIPv6(nic.getInterfaceAddresses());
        IPv4 ipv4 = IP.fromIPv4(v4.getAddress().getAddress());
        IPv6 ipv6 = null;
        if (v6 != null) {
            ipv6 = IP.fromIPv6(v6.getAddress().getAddress());
        }
        return new Tuple<>(ipv4, ipv6);
    }

    private static NetworkInterface selectNic(String nicname) throws Exception {
        var interfaces = NetworkInterface.getNetworkInterfaces();
        NetworkInterface nic = null;
        while (interfaces.hasMoreElements()) {
            var n = interfaces.nextElement();
            if (n.getName().equals(nicname)) {
                nic = n;
                break;
            }
        }
        if (nic == null) {
            throw new Exception(nicname + " not found");
        }
        return nic;
    }

    private static InterfaceAddress getIPv4(List<InterfaceAddress> ips) throws Exception {
        for (var ip : ips) {
            if (!(ip.getAddress() instanceof Inet4Address)) {
                continue;
            }
            if (ip.getAddress().isLoopbackAddress()) {
                continue;
            }
            return ip;
        }
        throw new Exception("cannot find proper local ipv4 to use");
    }

    private static InterfaceAddress getIPv6(List<InterfaceAddress> ips) {
        for (var ip : ips) {
            if (!(ip.getAddress() instanceof Inet6Address)) {
                continue;
            }
            if (ip.getAddress().isLoopbackAddress() || ip.getAddress().isLinkLocalAddress()) {
                continue;
            }
            return ip;
        }
        return null;
    }

    private static Tuple<MacAddress, IP> getIPvXGW(Network net, String routeCmd, Function<String, String> pingCmd, Function<String, String> neighCmd) throws Exception {
        var ipRouteGetResult = Utils.execute(routeCmd, true);
        if (ipRouteGetResult.exitCode != 0) {
            throw new Exception("unable to find default route (" + routeCmd + "): " + ipRouteGetResult.stderr.trim());
        }
        String[] entries = ipRouteGetResult.stdout.trim().split(" ");
        String gw = null;
        for (int i = 0; i < entries.length; ++i) {
            if (entries[i].equals("via")) {
                if (entries.length <= i + 1) {
                    throw new Exception("invalid output of ip route get command: " + ipRouteGetResult.stdout);
                }
                gw = entries[i + 1];
                break;
            }
        }
        if (gw == null) {
            throw new Exception("gateway not found: " + ipRouteGetResult.stdout);
        }
        IP ip = IP.from(gw);
        if (!net.contains(ip)) {
            throw new Exception("gateway " + gw + " is not contained in network " + net);
        }

        Utils.execute(pingCmd.apply(gw), true); // ignore error in this step

        var ipNeighShowResult = Utils.execute(neighCmd.apply(gw), true);
        if (ipNeighShowResult.exitCode != 0) {
            throw new Exception("unable to find mac of " + gw + ": " + ipNeighShowResult.stderr.trim());
        }
        entries = ipNeighShowResult.stdout.split(" ");
        String mac = null;
        for (int i = 0; i < entries.length; ++i) {
            if (entries[i].equals("lladdr")) {
                if (entries.length <= i + 1) {
                    throw new Exception("invalid output of ip neigh show command: " + ipNeighShowResult.stdout);
                }
                mac = entries[i + 1];
                break;
            }
        }
        if (mac == null) {
            throw new Exception("gateway mac not found: " + ipNeighShowResult.stdout);
        }
        return new Tuple<>(new MacAddress(mac), ip);
    }

    private static Tuple<MacAddress, IP> getIPv4GW(Network v4net) throws Exception {
        return getIPvXGW(v4net, "ip route get 1.1.1.1",
            ip -> "ping -c 1 -W 1 " + ip,
            ip -> "ip neigh show " + ip);
    }

    private static Tuple<MacAddress, IP> getIPv6GW(Network v6net) throws Exception {
        return getIPvXGW(v6net, "ip -6 route get 2606:4700:4700::1111",
            ip -> "ping6 -c 1 -W 1 " + ip,
            ip -> "ip -6 neigh show " + ip);
    }
}
