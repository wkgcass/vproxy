package vproxyx.websocks.udpovertcp;

import vproxy.base.component.elgroup.EventLoopGroup;
import vproxy.base.util.ByteArray;
import vproxy.base.util.Logger;
import vproxy.base.util.Network;
import vproxy.base.util.Utils;
import vproxy.base.util.coll.Tuple;
import vproxy.component.secure.SecurityGroup;
import vproxy.vfd.*;
import vproxy.vswitch.RouteTable;
import vproxy.vswitch.Switch;
import vproxy.vswitch.dispatcher.BPFMapKeySelectors;
import vproxy.vswitch.iface.XDPIface;
import vproxy.xdp.BPFMap;
import vproxy.xdp.BPFMode;
import vproxy.xdp.BPFObject;
import vproxy.xdp.UMem;

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

        Switch sw = new Switch("udp-over-tcp",
            new IPPort("255.255.255.255:0"),
            elg, 300_000, 4 * 3600 * 1000,
            SecurityGroup.denyAll(), -1, false);
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

        network.routeTable.addRule(new RouteTable.RouteRule("gw", new Network("0.0.0.0/0"), v4gw.right));
        Logger.alert("adding default route via " + v4gw.right.formatToIPString());
        if (v6gw != null) {
            network.routeTable.addRule(new RouteTable.RouteRule("gw6", new Network("::/0"), v6gw.right));
            Logger.alert("adding default route via " + v6gw.right.formatToIPString());
        }

        network.arpTable.record(v4gw.left, v4gw.right, true);
        Logger.alert("adding persistent arp entry for v4 gateway " + v4gw.left + " :: " + v4gw.right);
        if (v6gw != null) {
            network.arpTable.record(v6gw.left, v6gw.right, true);
            Logger.alert("adding persistent arp entry for v6 gateway " + v6gw.left + " :: " + v6gw.right);
        }

        ByteArray bpfObject;
        if (client) {
            bpfObject = HANDLE_TCP_DST_PORT_RANGE_30720_32767;
        } else {
            bpfObject = BPFObject.handleDstPortProgram(port);
        }
        String bpfpath = Utils.writeTemporaryFile("kern", "o", bpfObject.toJavaArray());
        BPFObject obj = BPFObject.loadAndAttachToNic(bpfpath, BPFObject.DEFAULT_XDP_PROG_NAME, nicname, BPFMode.SKB, true);
        BPFMap bpfMap = obj.getMap(BPFObject.DEFAULT_XSKS_MAP_NAME);

        // make it small so that it's able to run in docker by default
        UMem umem = sw.addUMem("umem0", 16, 8, 8, 2048);
        XDPIface iface = sw.addXDP(nicname, bpfMap, umem, 0, 8, 8, BPFMode.SKB, false, 0, 1, BPFMapKeySelectors.useQueueId.keySelector.get());

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

    /**
     * <pre>
     * #include &lt;linux/bpf.h&gt;
     * #include &lt;bpf_helpers.h&gt;
     *
     * struct bpf_map_def SEC("maps") xsks_map = {
     *     .type = BPF_MAP_TYPE_XSKMAP,
     *     .max_entries = 128,
     *     .key_size = sizeof(int),
     *     .value_size = sizeof(int)
     * };
     *
     * #define ETHER_TYPE_OFF  (12)
     * #define IP4_OFF         (14)
     * #define IP4_IHL_OFF     (IP4_OFF)
     * #define IP4_PROTO_OFF   (IP4_OFF  + 9)
     * #define TCPUDP4_OFF     (IP4_OFF  + 20)
     * #define TCPUDP4_DST_OFF (TCPUDP4_OFF + 2)
     * #define IP6_OFF         (14)
     * #define IP6_PROTO_OFF   (IP6_OFF  + 6)
     * #define TCPUDP6_OFF     (IP6_OFF  + 40)
     * #define TCPUDP6_DST_OFF (TCPUDP6_OFF + 2)
     *
     * #define ETHER_TYPE_IPv4 (0x0800)
     * #define ETHER_TYPE_IPv6 (0x86dd)
     * #define IP_PROTO_TCP    (6)
     * #define IP_PROTO_UDP    (17)
     *
     * #define HANDLED_PORT_MIN (30720)
     * #define HANDLED_PORT_MAX (32767)
     *
     * SEC("xdp_sock") int xdp_sock_prog(struct xdp_md *ctx)
     * {
     *     unsigned char* data_end = (unsigned char*) ((long) ctx-&gt;data_end);
     *     unsigned char* data     = (unsigned char*) ((long) ctx-&gt;data);
     *     unsigned char* pos = data;
     *     pos += ETHER_TYPE_OFF + 2;
     *     if (pos &gt; data_end) {
     *         return XDP_PASS;
     *     }
     *     int ether_type = ((data[ETHER_TYPE_OFF] &amp; 0xff) &lt;&lt; 8) | (data[ETHER_TYPE_OFF + 1] &amp; 0xff);
     *     if (ether_type == ETHER_TYPE_IPv4) {
     *         pos = data + IP4_IHL_OFF + 1;
     *         if (pos &gt; data_end) {
     *             return XDP_PASS;
     *         }
     *         int ip_len = data[IP4_IHL_OFF] &amp; 0xf;
     *         if (ip_len != 5) {
     *             return XDP_PASS;
     *         }
     *         pos = data + IP4_PROTO_OFF + 1;
     *         if (pos &gt; data_end) {
     *             return XDP_PASS;
     *         }
     *         int proto = data[IP4_PROTO_OFF] &amp; 0xff;
     *         if (proto != IP_PROTO_TCP &amp;&amp; proto != IP_PROTO_UDP) {
     *             return XDP_PASS;
     *         }
     *         pos = data + TCPUDP4_DST_OFF + 2;
     *         if (pos &gt; data_end) {
     *             return XDP_PASS;
     *         }
     *         int dst = ((data[TCPUDP4_DST_OFF] &amp; 0xff) &lt;&lt; 8) | (data[TCPUDP4_DST_OFF + 1] &amp; 0xff);
     *         if (dst &lt; HANDLED_PORT_MIN || dst &gt; HANDLED_PORT_MAX) {
     *             return XDP_PASS;
     *         }
     *     } else if (ether_type == ETHER_TYPE_IPv6) {
     *         pos = data + IP6_PROTO_OFF + 1;
     *         if (pos &gt; data_end) {
     *             return XDP_PASS;
     *         }
     *         int proto = data[IP6_PROTO_OFF] &amp; 0xff;
     *         if (proto != IP_PROTO_TCP &amp;&amp; proto != IP_PROTO_UDP) {
     *             return XDP_PASS;
     *         }
     *         pos = data + TCPUDP6_DST_OFF + 2;
     *         if (pos &gt; data_end) {
     *             return XDP_PASS;
     *         }
     *         int dst = ((data[TCPUDP6_DST_OFF] &amp; 0xff) &lt;&lt; 8) | (data[TCPUDP6_DST_OFF + 1] &amp; 0xff);
     *         if (dst &lt; HANDLED_PORT_MIN || dst &gt; HANDLED_PORT_MAX) {
     *             return XDP_PASS;
     *         }
     *     } else {
     *         return XDP_PASS;
     *     }
     *     return bpf_redirect_map(&xsks_map, ctx-&gt;rx_queue_index, XDP_PASS);
     * }
     * </pre>
     */
    private static final ByteArray HANDLE_TCP_DST_PORT_RANGE_30720_32767 = ByteArray.fromHexString("" +
        "7f454c46020101000000000000000000" +
        "0100f700010000000000000000000000" +
        "00000000000000004803000000000000" +
        "00000000400000000000400007000100" +
        "b7000000020000006113040000000000" +
        "6112000000000000bf24000000000000" +
        "070400000e0000002d342b0000000000" +
        "71250d000000000071240c0000000000" +
        "67040000080000004f54000000000000" +
        "57040000ffff000015041400dd860000" +
        "5504240000080000bf24000000000000" +
        "070400000f0000002d34210000000000" +
        "bf240000000000000704000018000000" +
        "2d341e000000000071240e0000000000" +
        "570400000f00000055041b0005000000" +
        "71241700000000001504010011000000" +
        "5504180006000000bf24000000000000" +
        "07040000260000002d34150000000000" +
        "712224000000000057020000f8000000" +
        "15020d00780000000500110000000000" +
        "bf240000000000000704000015000000" +
        "2d340e00000000007124140000000000" +
        "150401001100000055040b0006000000" +
        "bf24000000000000070400003a000000" +
        "2d340800000000007122380000000000" +
        "57020000f80000005502050078000000" +
        "61121000000000001801000000000000" +
        "0000000000000000b703000002000000" +
        "85000000330000009500000000000000" +
        "11000000040000000400000080000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "300000000400f1ff0000000000000000" +
        "00000000000000006d00000000000300" +
        "00010000000000000000000000000000" +
        "65000000000003003001000000000000" +
        "00000000000000005d00000000000300" +
        "60010000000000000000000000000000" +
        "55000000000003008801000000000000" +
        "00000000000000004e00000000000300" +
        "c8000000000000000000000000000000" +
        "22000000120003000000000000000000" +
        "90010000000000000c00000011000500" +
        "00000000000000001400000000000000" +
        "68010000000000000100000008000000" +
        "002e74657874006d6170730078736b73" +
        "5f6d6170002e72656c7864705f736f63" +
        "6b007864705f736f636b5f70726f6700" +
        "73616d706c655f6b65726e2e63002e73" +
        "7472746162002e73796d746162004c42" +
        "42305f38004c4242305f3136004c4242" +
        "305f3135004c4242305f3133004c4242" +
        "305f3130000000000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000000000000000000000" +
        "00000000000000003e00000003000000" +
        "00000000000000000000000000000000" +
        "d0020000000000007500000000000000" +
        "00000000000000000100000000000000" +
        "00000000000000000100000001000000" +
        "06000000000000000000000000000000" +
        "40000000000000000000000000000000" +
        "00000000000000000400000000000000" +
        "00000000000000001900000001000000" +
        "06000000000000000000000000000000" +
        "40000000000000009001000000000000" +
        "00000000000000000800000000000000" +
        "00000000000000001500000009000000" +
        "00000000000000000000000000000000" +
        "c0020000000000001000000000000000" +
        "06000000030000000800000000000000" +
        "10000000000000000700000001000000" +
        "03000000000000000000000000000000" +
        "d0010000000000001400000000000000" +
        "00000000000000000400000000000000" +
        "00000000000000004600000002000000" +
        "00000000000000000000000000000000" +
        "e801000000000000d800000000000000" +
        "01000000070000000800000000000000" +
        "1800000000000000"
    );
}
