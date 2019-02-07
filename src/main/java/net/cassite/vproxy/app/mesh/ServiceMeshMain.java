package net.cassite.vproxy.app.mesh;

import net.cassite.vproxy.app.Application;
import net.cassite.vproxy.component.auto.AutoConfig;
import net.cassite.vproxy.component.elgroup.EventLoopGroup;
import net.cassite.vproxy.component.exception.XException;
import net.cassite.vproxy.component.khala.Khala;
import net.cassite.vproxy.component.khala.KhalaConfig;
import net.cassite.vproxy.component.svrgroup.Method;
import net.cassite.vproxy.discovery.DiscoveryConfig;
import net.cassite.vproxy.discovery.TimeoutConfig;
import net.cassite.vproxy.util.IPType;
import net.cassite.vproxy.util.Tuple;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.*;
import java.util.*;

public class ServiceMeshMain {
    class Sidecar {
        private String zone;
        private int localPort;
        private int minExportPort;
        private int maxExportPort;

        @Override
        public String toString() {
            return "Sidecar{" +
                "zone='" + zone + '\'' +
                ", localPort=" + localPort +
                ", minExportPort=" + minExportPort +
                ", maxExportPort=" + maxExportPort +
                '}';
        }
    }

    class AutoLB {
        private String alias;
        private String service;
        private String zone;
        private int port;

        @Override
        public String toString() {
            return "AutoLB{" +
                "alias='" + alias + '\'' +
                ", service='" + service + '\'' +
                ", zone='" + zone + '\'' +
                ", port=" + port +
                '}';
        }
    }

    class Discovery {
        class Search {
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

    private static final ServiceMeshMain instance = new ServiceMeshMain();

    public static ServiceMeshMain getInstance() {
        return instance;
    }

    private String mode;
    private int workers;
    private String nic;
    private IPType ipType;
    private Sidecar sidecar;
    private Map<String, AutoLB> autoLBs;
    private Discovery discovery;

    private AutoConfig autoConfig;

    private ServiceMeshMain() {
    }

    public AutoConfig getAutoConfig() {
        return autoConfig;
    }

    @Override
    public String toString() {
        return "ServiceMeshMain{" +
            "mode='" + mode + '\'' +
            ", workers=" + workers +
            ", nic='" + nic + '\'' +
            ", ipType=" + ipType +
            ", sidecar=" + sidecar +
            ", autoLBs=" + autoLBs +
            ", discovery=" + discovery +
            '}';
    }

    // return 0 for success, positive integer for exit code
    public int load(String filepath) {
        if (filepath.startsWith("~")) {
            filepath = System.getProperty("user.home") + filepath.substring("~".length());
        }
        File f = new File(filepath);
        if (!f.exists()) {
            return exit("specified service mesh config file not exists");
        }
        Properties p = new Properties();
        try {
            p.load(new FileInputStream(filepath));
        } catch (IOException e) {
            return exit("loading config failed");
        }

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
            EventLoopGroup aelg = new EventLoopGroup(discovery.name + ":acceptor");
            aelg.add("acceptor0");

            EventLoopGroup workerGroup = new EventLoopGroup(discovery.name + ":worker");
            for (int i = 0; i < workers; ++i) {
                workerGroup.add("worker" + i);
            }
            net.cassite.vproxy.discovery.Discovery dis = new net.cassite.vproxy.discovery.Discovery(
                discovery.name,
                new DiscoveryConfig(
                    discovery.nic, discovery.ipType, discovery.udpSockPort, discovery.udpPort, discovery.tcpPort,
                    discovery.search.mask, discovery.search.minUDPPort, discovery.search.maxUDPPort,
                    TimeoutConfig.getDefault(), TimeoutConfig.getDefaultHc()
                ));
            Khala khala = new Khala(dis, KhalaConfig.getDefault());

            this.autoConfig = new AutoConfig(aelg, workerGroup, khala,
                nic, ipType, TimeoutConfig.getDefaultHc(), Method.wrr);
        } catch (Exception e) {
            return exit("got exception: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
        return 0;
    }

    public int start() {
        try {
            if (mode.equals("sidecar")) {
                launchSidecar();
            } else {
                assert mode.equals("auto_lb");
                launchAutoLB();
            }
        } catch (Exception e) {
            return exit("got exception when launching. " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
        return 0;
    }

    private void launchSidecar() throws Exception {
        Application.get().sidecarHolder.sidecar = new net.cassite.vproxy.component.auto.Sidecar(
            "sidecar", sidecar.zone, sidecar.localPort, getAutoConfig(), sidecar.minExportPort, sidecar.maxExportPort
        );
    }

    private void launchAutoLB() throws Exception {
        for (String alias : autoLBs.keySet()) {
            AutoLB autoLB = autoLBs.get(alias);
            Application.get().autoLBHolder.add(
                autoLB.alias, autoLB.service, autoLB.zone, autoLB.port
            );
        }
    }

    private void checkWithException() throws XException {
        checkNull("mode", mode);
        checkNull("workers", workers);
        checkNull("nic", nic);
        checkNull("ip_type", ipType);

        checkNull("discovery", discovery);
        checkNull("discovery.nic", discovery.nic);
        checkNull("discovery.ip_type", discovery.ipType);
        checkNull("discovery.udp_sock_port", discovery.udpSockPort);
        checkNull("discovery.udp_port", discovery.udpPort);
        checkNull("discovery.tcp_port", discovery.tcpPort);
        InetAddress addr = getNicAddress(discovery.nic, discovery.ipType);
        if (addr == null)
            throw new XException("discovery nic address not found");
        discovery.name = addr.getHostName();

        checkNull("discovery.search", discovery.search);
        checkNull("discovery.search.mask", discovery.search.mask);
        checkNull("discovery.search.min_udp_port", discovery.search.minUDPPort);
        checkNull("discovery.search.max_udp_port", discovery.search.maxUDPPort);

        if (mode.equals("sidecar")) {
            checkNull("sidecar", sidecar);
            checkNull("sidecar.zone", sidecar.zone);
            checkNull("sidecar.local_port", sidecar.localPort);
            checkNull("sidecar.min_export_port", sidecar.minExportPort);
            checkNull("sidecar.max_export_port", sidecar.maxExportPort);
        } else {
            assert mode.equals("auto_lb");

            checkNull("auto_lb", autoLBs);
            for (String alias : autoLBs.keySet()) {
                AutoLB autoLB = autoLBs.get(alias);
                String prefix = "auto_lb." + alias + ".";
                checkNull(prefix + "service", autoLB.service);
                checkNull(prefix + "zone", autoLB.zone);
                checkNull(prefix + "port", autoLB.port);
            }
        }
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
        if (key.equals("mode")) {
            mode = loadMode(value);
        } else if (key.equals("workers")) {
            workers = loadPositiveInt(value);
        } else if (key.equals("nic")) {
            nic = loadNic(value);
        } else if (key.equals("ip_type")) {
            ipType = loadIPType(value);
        } else if (key.startsWith("sidecar.")) {
            Sidecar sidecar = constructSidecar();
            String[] arr = key.split("\\.");
            if (arr.length != 2)
                throw new XException("invalid key");
            String k = arr[1];
            switch (k) {
                case "zone":
                    sidecar.zone = loadString(value);
                    break;
                case "local_port":
                    sidecar.localPort = loadPort(value);
                    break;
                case "min_export_port":
                    sidecar.minExportPort = loadPort(value);
                    break;
                case "max_export_port":
                    sidecar.maxExportPort = loadPort(value);
                    break;
                default:
                    throw new XException("unknown config");
            }
        } else if (key.startsWith("auto_lb.")) {
            Tuple<String, String> tup = extractAutoLB(key);
            AutoLB autoLB = constructAutoLB(tup.left);
            String k = tup.right;
            switch (k) {
                case "service":
                    autoLB.service = loadString(value);
                    break;
                case "zone":
                    autoLB.zone = loadString(value);
                    break;
                case "port":
                    autoLB.port = loadPort(value);
                    break;
                default:
                    throw new XException("unknown config");
            }
        } else if (key.startsWith("discovery.search.")) {
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

    private String loadMode(String value) throws XException {
        if (!value.equals("sidecar") && !value.equals("auto_lb"))
            throw new XException("invalid mode");
        return value;
    }

    private Sidecar constructSidecar() {
        if (sidecar == null) {
            sidecar = new Sidecar();
        }
        return sidecar;
    }

    private Tuple<String/*alias*/, String/*key*/> extractAutoLB(String key) throws XException {
        String[] arr = key.split("\\.");
        if (arr.length != 3)
            throw new XException("invalid key");
        return new Tuple<>(arr[1], arr[2]);
    }

    private AutoLB constructAutoLB(String alias) {
        if (autoLBs == null)
            autoLBs = new HashMap<>();
        if (!autoLBs.containsKey(alias)) {
            AutoLB autoLB = new AutoLB();
            autoLB.alias = alias;
            autoLBs.put(alias, autoLB);
        }
        return autoLBs.get(alias);
    }

    private Discovery constructDiscovery() {
        if (discovery == null) {
            discovery = new Discovery();
        }
        return discovery;
    }

    private Discovery.Search constructSearch(Discovery discovery) {
        if (discovery.search == null) {
            discovery.search = discovery.new Search();
        }
        return discovery.search;
    }

    private int loadPositiveInt(String value) throws XException {
        int i;
        try {
            i = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new XException("not integer");
        }
        if (i <= 0)
            throw new XException("is not positive");
        return i;
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

    private String loadString(String s) throws XException {
        if (s.isEmpty())
            throw new XException("empty string");
        return s;
    }

    private int exit(String msg) {
        System.err.println(msg);
        return 1;
    }
}
