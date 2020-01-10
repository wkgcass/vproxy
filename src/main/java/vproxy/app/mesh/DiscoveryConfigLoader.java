package vproxy.app.mesh;

import vproxy.component.auto.AutoConfig;
import vproxy.component.exception.XException;
import vproxy.component.khala.Khala;
import vproxy.component.khala.KhalaConfig;
import vproxy.discovery.DiscoveryConfig;
import vproxy.discovery.TimeConfig;
import vproxy.util.IPType;
import vproxy.util.Logger;
import vproxy.util.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.*;
import java.util.*;

public class DiscoveryConfigLoader {
    static class Discovery {
        static class Search {
            int mask;
            int minUDPPort;
            int maxUDPPort;

            @Override
            public String toString() {
                return "Search{" +
                    "mask=" + mask +
                    ", minUDPPort=" + minUDPPort +
                    ", maxUDPPort=" + maxUDPPort +
                    '}';
            }
        }

        String name;
        String nic;
        IPType ipType;
        int udpSockPort;
        int udpPort;
        int tcpPort;
        Search search;

        @Override
        public String toString() {
            return "Discovery{" +
                "name='" + name + '\'' +
                ", nic='" + nic + '\'' +
                ", ipType=" + ipType +
                ", udpSockPort=" + udpSockPort +
                ", udpPort=" + udpPort +
                ", tcpPort=" + tcpPort +
                ", search=" + search +
                '}';
        }
    }

    private static final DiscoveryConfigLoader instance = new DiscoveryConfigLoader();

    public static DiscoveryConfigLoader getInstance() {
        return instance;
    }

    private Discovery discovery;

    private AutoConfig autoConfig;

    private DiscoveryConfigLoader() {
    }

    public AutoConfig getAutoConfig() {
        return autoConfig;
    }

    @Override
    public String toString() {
        return "DiscoveryMain{" +
            "discovery=" + discovery +
            '}';
    }

    // return 0 for success, positive integer for exit code
    public int load(String filepath) {
        filepath = Utils.filename(filepath);
        File f = new File(filepath);
        if (!f.exists()) {
            return exit("specified discovery config file not exists");
        }
        Properties p = new Properties();
        try {
            p.load(new FileInputStream(filepath));
        } catch (IOException e) {
            return exit("loading config failed");
        }

        return loadFrom(p);
    }

    private int loadFrom(Properties p) {
        Enumeration<?> keys = p.propertyNames();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            String value = p.getProperty(key);
            int res = tryLoad(key, value);
            if (res != 0)
                return res;
        }

        return 0;
    }

    public void loadDefault() throws Exception {
        // find the nic to bind
        Map<InterfaceAddress, String> addr2nicMap = new HashMap<>();
        List<InterfaceAddress> addresses = new ArrayList<>();
        Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
        while (nics.hasMoreElements()) {
            NetworkInterface nic = nics.nextElement();
            var ls = nic.getInterfaceAddresses();
            addresses.addAll(ls);
            for (InterfaceAddress a : ls) {
                addr2nicMap.put(a, nic.getName());
            }
        }
        addresses.sort((a, b) -> {
            var aa = a.getAddress();
            var bb = b.getAddress();
            // non-loopback better than loopback
            {
                if (!aa.isLoopbackAddress() && bb.isLoopbackAddress()) {
                    return -1;
                }
                if (aa.isLoopbackAddress() && !bb.isLoopbackAddress()) {
                    return 1;
                }
            }
            // ipv4 better than ipv6
            {
                if (aa instanceof Inet4Address && !(bb instanceof Inet4Address)) {
                    return -1;
                }
                if (!(aa instanceof Inet4Address) && bb instanceof Inet4Address) {
                    return 1;
                }
            }
            // other-127/::1 better than 127.0.0.1/::1
            {
                var strA = Utils.ipStr(aa.getAddress());
                var strB = Utils.ipStr(bb.getAddress());
                if (aa instanceof Inet4Address) {
                    if (!strA.equals("127.0.0.1") && strB.equals("127.0.0.1")) {
                        return -1;
                    }
                    if (strA.equals("127.0.0.1") && !strB.equals("127.0.0.1")) {
                        return 1;
                    }
                } else {
                    if (!strA.equals("[0000:0000:0000:0000:0000:0000:0000:0001]") && strB.equals("[0000:0000:0000:0000:0000:0000:0000:0001]")) {
                        return -1;
                    }
                    if (strA.equals("[0000:0000:0000:0000:0000:0000:0000:0001]") && !strB.equals("[0000:0000:0000:0000:0000:0000:0000:0001]")) {
                        return 1;
                    }
                }
            }
            return 0;
        });
        if (addresses.isEmpty()) {
            throw new Exception("no address found on nics");
        }
        InterfaceAddress addr = addresses.get(0);
        InetAddress l3addr = addr.getAddress();
        int mask = addr.getNetworkPrefixLength();
        String nic = addr2nicMap.get(addr);
        String ipType = (l3addr instanceof Inet4Address) ? "v4" : "v6";
        Logger.alert("using default discovery config: nic = " + nic + ", ip = " + Utils.ipStr(l3addr.getAddress()) + ", network mask = " + mask);

        Properties p = new Properties();
        p.setProperty("discovery.nic", nic);
        p.setProperty("discovery.ip_type", ipType);
        p.setProperty("discovery.udp_sock_port", "56565");
        p.setProperty("discovery.udp_port", "31000");
        p.setProperty("discovery.tcp_port", "31000");
        p.setProperty("discovery.search.mask", "" + mask);
        p.setProperty("discovery.search.min_udp_port", "31000");
        p.setProperty("discovery.search.max_udp_port", "31000");
        int ret = loadFrom(p);
        if (ret != 0) {
            Logger.shouldNotHappen("invalid default config: " + p);
            throw new Exception("loading default config failed");
        }
    }

    public int check() {
        try {
            checkWithException();
        } catch (XException e) {
            return exit(e.getMessage());
        }
        return 0;
    }

    public int gen() {
        try {
            vproxy.discovery.Discovery dis = new vproxy.discovery.Discovery(
                discovery.name,
                new DiscoveryConfig(
                    discovery.nic, discovery.ipType, discovery.udpSockPort, discovery.udpPort, discovery.tcpPort,
                    discovery.search.mask, discovery.search.minUDPPort, discovery.search.maxUDPPort,
                    TimeConfig.getDefault(), TimeConfig.getDefaultHc()
                ));
            Khala khala = new Khala(dis, KhalaConfig.getDefault());

            this.autoConfig = new AutoConfig(khala);
        } catch (Exception e) {
            e.printStackTrace();
            return exit("got exception: " + Utils.formatErr(e));
        }
        Logger.alert("discovery module launched");
        return 0;
    }

    private void checkWithException() throws XException {
        checkNull("discovery", discovery);
        checkNull("discovery.nic", discovery.nic);
        checkNull("discovery.ip_type", discovery.ipType);
        checkNull("discovery.udp_sock_port", discovery.udpSockPort);
        checkNull("discovery.udp_port", discovery.udpPort);
        checkNull("discovery.tcp_port", discovery.tcpPort);
        InetAddress addr = getNicAddress(discovery.nic, discovery.ipType);
        if (addr == null)
            throw new XException("discovery nic address not found");
        try {
            discovery.name = Utils.runBlockWithTimeout(1000, addr::getHostName);
        } catch (Exception e) {
            discovery.name = Utils.ipStr(addr.getAddress());
        }

        checkNull("discovery.search", discovery.search);
        checkNull("discovery.search.mask", discovery.search.mask);
        checkNull("discovery.search.min_udp_port", discovery.search.minUDPPort);
        checkNull("discovery.search.max_udp_port", discovery.search.maxUDPPort);
    }

    private void checkNull(String key, Object value) throws XException {
        if (value == null)
            throw new XException(key + " not specified");
    }

    private void checkNull(String key, int value) throws XException {
        if (value == 0)
            throw new XException(key + " not specified");
    }

    private int tryLoad(String key, String value) {
        try {
            load(key, value);
        } catch (XException e) {
            return exit("invalid entry for " + key + ": " + e.getMessage());
        }
        return 0;
    }

    private void load(String key, String value) throws XException {
        if (key.startsWith("discovery.search.")) {
            Discovery.Search search = constructSearch(constructDiscovery());
            String[] arr = key.split("\\.");
            if (arr.length != 3)
                throw new XException("invalid key");
            String k = arr[2];
            switch (k) {
                case "mask":
                    search.mask = loadMask(value);
                    break;
                case "min_udp_port":
                    search.minUDPPort = loadPort(value);
                    break;
                case "max_udp_port":
                    search.maxUDPPort = loadPort(value);
                    break;
                default:
                    throw new XException("unknown config");
            }
        } else if (key.startsWith("discovery.") /*search is already handled*/) {
            Discovery discovery = constructDiscovery();
            String[] arr = key.split("\\.");
            if (arr.length != 2)
                throw new XException("invalid key");
            String k = arr[1];
            switch (k) {
                case "nic":
                    discovery.nic = loadNic(value);
                    break;
                case "ip_type":
                    discovery.ipType = loadIPType(value);
                    break;
                case "udp_sock_port":
                    discovery.udpSockPort = loadPort(value);
                    break;
                case "udp_port":
                    discovery.udpPort = loadPort(value);
                    break;
                case "tcp_port":
                    discovery.tcpPort = loadPort(value);
                    break;
                default:
                    throw new XException("unknown config");
            }
        } else {
            throw new XException("unknown config");
        }
    }

    private Discovery constructDiscovery() {
        if (discovery == null) {
            discovery = new Discovery();
        }
        return discovery;
    }

    private Discovery.Search constructSearch(Discovery discovery) {
        if (discovery.search == null) {
            discovery.search = new Discovery.Search();
        }
        return discovery.search;
    }

    private int loadPort(String value) throws XException {
        int i;
        try {
            i = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new XException("not integer");
        }
        if (i < 1 || i > 65535)
            throw new XException("is not valid port");
        return i;
    }

    private int loadMask(String value) throws XException {
        int i;
        try {
            i = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new XException("not integer");
        }
        if (i < 1 || i > 128)
            throw new XException("is not valid port");
        return i;
    }

    private String loadNic(String value) throws XException {
        Enumeration<NetworkInterface> nics;
        try {
            nics = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            throw new XException("get network interfaces failed. " + e.getMessage());
        }
        while (nics.hasMoreElements()) {
            NetworkInterface nic = nics.nextElement();
            if (nic.getName().equals(value)) {
                return value;
            }
        }
        throw new XException("nic " + value + " not found");
    }

    private InetAddress getNicAddress(String nicName, IPType ipType) {
        Enumeration<NetworkInterface> nics;
        try {
            nics = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            return null;
        }
        while (nics.hasMoreElements()) {
            NetworkInterface nic = nics.nextElement();
            if (nic.getName().equals(nicName)) {
                Enumeration<InetAddress> addresses = nic.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress inet = addresses.nextElement();
                    if (ipType == IPType.v4) {
                        if (inet instanceof Inet4Address)
                            return inet;
                    } else {
                        if (inet instanceof Inet6Address)
                            return inet;
                    }
                }
            }
        }
        return null;
    }

    private IPType loadIPType(String value) throws XException {
        try {
            return IPType.valueOf(value);
        } catch (Exception e) {
            throw new XException("unknown ip_type " + value);
        }
    }

    private int exit(String msg) {
        System.err.println(msg);
        return 1;
    }
}
