package vproxy.app.controller;

import vproxy.app.app.Application;
import vproxy.app.app.cmd.Command;
import vproxy.app.app.cmd.handle.resource.BPFObjectHandle;
import vproxy.app.app.cmd.handle.resource.SwitchHandle;
import vproxy.app.process.Shutdown;
import vproxy.base.component.elgroup.EventLoopGroup;
import vproxy.base.util.*;
import vproxy.base.util.coll.IntMap;
import vproxy.base.util.exception.NotFoundException;
import vproxy.component.secure.SecurityGroup;
import vproxy.vfd.*;
import vproxy.vswitch.Switch;
import vproxy.vswitch.VirtualNetwork;
import vproxy.vswitch.dispatcher.BPFMapKeySelectors;
import vproxy.vswitch.iface.XDPIface;
import vproxy.xdp.BPFMode;
import vproxy.xdp.UMem;

import java.util.*;

public class DockerNetworkDriverImpl implements DockerNetworkDriver {
    private static final String SWITCH_NAME = "sw-docker";
    private static final String UMEM_NAME = "umem0";
    private static final String NETWORK_NETWORK_ID_ANNOTATION = AnnotationKeys.SWNetwork_DockerNetworkDriverNetworkId.name;
    private static final MacAddress GATEWAY_MAC_ADDRESS = new MacAddress("02:00:00:00:00:20");
    private static final String GATEWAY_IP_ANNOTATION = AnnotationKeys.SWIp_DockerNetworkDriverGatewayIp.name;
    private static final String GATEWAY_IPv4_FLAG_VALUE = "gateway-ipv4";
    private static final String GATEWAY_IPv6_FLAG_VALUE = "gateway-ipv6";
    private static final String VETH_ENDPOINT_ID_ANNOTATION = AnnotationKeys.SWIface_DockerNetworkDriverEndpointId.name;
    private static final String VETH_ENDPOINT_IPv4_ANNOTATION = AnnotationKeys.SWIface_DockerNetworkDriverEndpointIpv4.name;
    private static final String VETH_ENDPOINT_IPv6_ANNOTATION = AnnotationKeys.SWIface_DockerNetworkDriverEndpointIpv6.name;
    private static final String VETH_ENDPOINT_MAC_ANNOTATION = AnnotationKeys.SWIface_DockerNetworkDriverEndpointMac.name;
    private static final String CONTAINER_VETH_SUFFIX = "pod";
    private static final String NETWORK_ENTRY_VETH_PREFIX = "vproxy";
    private static final String NETWORK_ENTRY_VETH_PEER_SUFFIX = "sw";
    private static final int NETWORK_ENTRY_VNI = 15999999;
    private static final int VNI_MAX = 9999999;

    @Override
    public synchronized void createNetwork(CreateNetworkRequest req) throws Exception {
        // validate options
        int optionVNI = 0;
        if (req.optionsDockerNetworkGeneric.containsKey(VNI_OPTION)) {
            String vniStr = req.optionsDockerNetworkGeneric.get(VNI_OPTION);
            if (!Utils.isInteger(vniStr)) {
                throw new Exception(VNI_OPTION + ": " + vniStr + " is not an integer");
            }
            optionVNI = Integer.parseInt(vniStr);
            if (optionVNI < 1 || optionVNI > VNI_MAX) { // nic name limit
                throw new Exception(VNI_OPTION + ": " + vniStr + " is out of the plugin supported vni range: [1, " + VNI_MAX + "]");
            }
        }
        Network optionV4Net = null;
        if (req.optionsDockerNetworkGeneric.containsKey(SUBNET4_OPTION)) {
            String net = req.optionsDockerNetworkGeneric.get(SUBNET4_OPTION);
            if (!Network.validNetworkStr(net)) {
                throw new Exception(SUBNET4_OPTION + ": " + net + " is not a valid network");
            }
            optionV4Net = new Network(net);
            if (!(optionV4Net.getIp() instanceof IPv4)) {
                throw new Exception(SUBNET4_OPTION + ": " + net + " is not v4 network");
            }
        }
        Network optionV6Net = null;
        if (req.optionsDockerNetworkGeneric.containsKey(SUBNET6_OPTION)) {
            String net = req.optionsDockerNetworkGeneric.get(SUBNET6_OPTION);
            if (!Network.validNetworkStr(net)) {
                throw new Exception(SUBNET6_OPTION + ": " + net + " is not a valid network");
            }
            optionV6Net = new Network(net);
            if (!(optionV6Net.getIp() instanceof IPv6)) {
                throw new Exception(SUBNET4_OPTION + ": " + net + " is not v6 network");
            }
            // v6 should exist in ip data as well
            if (req.ipv6Data.isEmpty()) {
                throw new Exception(SUBNET6_OPTION + " is set but ipv6 network is not specified for docker");
            }
        }
        // check ipv4 data length
        if (req.ipv4Data.size() > 1) {
            if (optionV4Net == null) {
                throw new Exception(SUBNET4_OPTION + " option must be specified when more than one ipv4 cidr in one network");
            }
        }
        if (req.ipv6Data.size() > 1) {
            if (optionV6Net == null) {
                throw new Exception(SUBNET6_OPTION + " option must be specified when more than one ipv6 cidr in one network");
            }
        }
        if (req.ipv4Data.isEmpty()) {
            throw new Exception("no ipv4 network info provided");
        }
        // validate
        for (var ipv4Data : req.ipv4Data) {
            if (ipv4Data.auxAddresses != null && !ipv4Data.auxAddresses.isEmpty()) {
                throw new Exception("auxAddresses are not supported");
            }
            Network net;
            try {
                net = new Network(ipv4Data.pool);
            } catch (IllegalArgumentException e) {
                throw new Exception("ipv4 network is not a valid cidr " + ipv4Data.pool);
            }
            if (!(net.getIp() instanceof IPv4)) {
                throw new Exception("address " + ipv4Data.pool + " is not ipv4 cidr");
            }
            var gatewayStr = ipv4Data.gateway;
            if (gatewayStr.contains("/")) {
                int mask;
                try {
                    mask = Integer.parseInt(gatewayStr.substring(gatewayStr.indexOf("/") + 1));
                } catch (NumberFormatException e) {
                    throw new Exception("invalid format for ipv4 gateway " + gatewayStr);
                }
                if (mask != net.getMask()) {
                    throw new Exception("the gateway mask " + mask + " must be the same as the network " + net.getMask());
                }
            }
            gatewayStr = gatewayStr.substring(0, gatewayStr.indexOf("/"));
            IP gateway;
            try {
                gateway = IP.from(gatewayStr);
            } catch (IllegalArgumentException e) {
                throw new Exception("ipv4 gateway is not a valid ip address " + ipv4Data.gateway);
            }
            if (!net.contains(gateway)) {
                throw new Exception("the cidr " + ipv4Data.pool + " does not contain the gateway " + ipv4Data.gateway);
            }
            ipv4Data.__transformedGateway = gateway.formatToIPString();

            if (optionV4Net != null && !optionV4Net.contains(net)) {
                throw new Exception(SUBNET4_OPTION + ": " + optionV4Net + " does not contain ipv4Data network " + net);
            }
        }
        for (var ipv6Data : req.ipv6Data) {
            if (ipv6Data.auxAddresses != null && ipv6Data.auxAddresses.isEmpty()) {
                throw new Exception("auxAddresses are not supported");
            }
            Network net;
            try {
                net = new Network(ipv6Data.pool);
            } catch (IllegalArgumentException e) {
                throw new Exception("ipv6 network is not a valid cidr " + ipv6Data.pool);
            }
            if (!(net.getIp() instanceof IPv6)) {
                throw new Exception("address " + ipv6Data.pool + " is not ipv6 cidr");
            }
            var gatewayStr = ipv6Data.gateway;
            if (gatewayStr.contains("/")) {
                int mask;
                try {
                    mask = Integer.parseInt(gatewayStr.substring(gatewayStr.indexOf("/") + 1));
                } catch (NumberFormatException e) {
                    throw new Exception("invalid format for ipv6 gateway " + gatewayStr);
                }
                if (mask != net.getMask()) {
                    throw new Exception("the gateway mask " + mask + " must be the same as the network " + net.getMask());
                }
            }
            gatewayStr = gatewayStr.substring(0, gatewayStr.indexOf("/"));
            IP gateway;
            try {
                gateway = IP.from(gatewayStr);
            } catch (IllegalArgumentException e) {
                throw new Exception("ipv6 gateway is not a valid ip address " + ipv6Data.gateway);
            }
            if (!net.contains(gateway)) {
                throw new Exception("the cidr " + ipv6Data.pool + " does not contain the gateway " + ipv6Data.gateway);
            }
            ipv6Data.__transformedGateway = gateway.formatToIPString();

            if (optionV6Net != null && !optionV6Net.contains(net)) {
                throw new Exception(SUBNET6_OPTION + ": " + optionV6Net + " does not contain ipv6Data network " + net);
            }
        }

        // handle
        var sw = ensureSwitch();
        IntMap<VirtualNetwork> networks = sw.getNetworks();
        int n = 0;
        if (optionVNI == 0) {
            for (int i : networks.keySet()) {
                if (n < i) {
                    n = i;
                }
            }
            n += 1; // greater than the biggest recorded vni
            if (n > VNI_MAX) {
                throw new Exception("cannot use auto selected vni " + n + ", out of range: [1," + VNI_MAX + "]");
            }
        } else {
            try {
                sw.getNetwork(optionVNI);
            } catch (NotFoundException ignore) {
                n = optionVNI;
            }
            if (n == 0) {
                throw new Exception(VNI_OPTION + ": " + optionVNI + " already exists");
            }
        }

        Network v4net;
        //noinspection ReplaceNullCheck
        if (optionV4Net == null) {
            v4net = new Network(req.ipv4Data.get(0).pool);
        } else {
            v4net = optionV4Net;
        }
        Network v6net;
        if (optionV6Net == null && !req.ipv6Data.isEmpty()) {
            v6net = new Network(req.ipv6Data.get(0).pool);
        } else {
            v6net = optionV6Net;
        }
        sw.addNetwork(n, v4net, v6net, new Annotations(Collections.singletonMap(NETWORK_NETWORK_ID_ANNOTATION, req.networkId)));
        Logger.alert("network added: vni=" + n + ", v4=" + v4net + ", v6=" + v6net + ", docker:networkId=" + req.networkId);
        VirtualNetwork net = sw.getNetwork(n);
        if (!req.networkId.equals(net.getAnnotations().other.get(NETWORK_NETWORK_ID_ANNOTATION))) {
            Logger.shouldNotHappen("adding network failed, maybe concurrent modification");
            try {
                sw.delNetwork(n);
            } catch (Exception e2) {
                Logger.error(LogType.SYS_ERROR, "rollback network " + n + " failed", e2);
            }
            throw new Exception("unexpected state");
        }

        // add entry veth
        try {
            var umem = ensureUMem();
            createNetworkEntryVeth(sw, umem, net);
        } catch (Exception e) {
            Logger.error(LogType.SYS_ERROR, "creating network entry veth for network " + n + " failed", e);
            try {
                sw.delNetwork(n);
            } catch (Exception e2) {
                Logger.error(LogType.SYS_ERROR, "rollback network " + n + " failed", e2);
            }
            throw e;
        }

        // add ipv4 gateway ip
        {
            var gateway = IP.from(req.ipv4Data.get(0).__transformedGateway);
            var mac = GATEWAY_MAC_ADDRESS;
            net.addIp(gateway, mac, new Annotations(Collections.singletonMap(GATEWAY_IP_ANNOTATION, GATEWAY_IPv4_FLAG_VALUE)));
            Logger.alert("ip added: vni=" + n + ", ip=" + gateway + ", mac=" + mac);
        }

        if (!req.ipv6Data.isEmpty()) {
            // add ipv6 gateway ip
            var gateway = IP.from(req.ipv6Data.get(0).__transformedGateway);
            var mac = GATEWAY_MAC_ADDRESS;
            net.addIp(gateway, mac, new Annotations(Collections.singletonMap(GATEWAY_IP_ANNOTATION, GATEWAY_IPv6_FLAG_VALUE)));
            Logger.alert("ip added: vni=" + n + ", ip=" + gateway + ", mac=" + mac);
        }

        persistConfig();
    }

    private Switch ensureSwitch() throws Exception {
        Switch sw;
        try {
            sw = Application.get().switchHolder.get(SWITCH_NAME);
        } catch (NotFoundException ignore) {
            // need to create one
            EventLoopGroup elg;
            try {
                elg = Application.get().eventLoopGroupHolder.get(Application.DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME);
            } catch (NotFoundException x) {
                Logger.shouldNotHappen(Application.DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME + " not exists");
                throw new RuntimeException("should not happen: no event loop to handle the request");
            }
            sw = Application.get().switchHolder.add(
                SWITCH_NAME,
                new IPPort("0.0.0.0", 4789),
                elg,
                SwitchHandle.MAC_TABLE_TIMEOUT,
                SwitchHandle.ARP_TABLE_TIMEOUT,
                SecurityGroup.allowAll(),
                1500,
                true);
            Logger.alert("switch " + SWITCH_NAME + " created");
        }
        return sw;
    }

    private UMem ensureUMem() throws Exception {
        var sw = ensureSwitch();
        var umemOpt = sw.getUMems().stream().filter(n -> n.alias.equals(UMEM_NAME)).findAny();
        UMem umem;
        if (umemOpt.isEmpty()) {
            // need to add umem
            umem = sw.addUMem(UMEM_NAME, 4096, 32, 32, 2048);
        } else {
            return umemOpt.get();
        }

        try {
            createNetworkEntryVeth(sw, umem, null);
        } catch (Exception e) {
            try {
                sw.delUMem(UMEM_NAME);
            } catch (Exception e2) {
                Logger.error(LogType.SYS_ERROR, "rollback umem failed", e2);
            }
            throw e;
        }

        return umem;
    }

    private void createNetworkEntryVeth(Switch sw, UMem umem, VirtualNetwork net) throws Exception {
        int index = 0;
        if (net != null) {
            index = net.vni;
        }

        String hostNic = NETWORK_ENTRY_VETH_PREFIX + index;
        String swNic = NETWORK_ENTRY_VETH_PREFIX + index + NETWORK_ENTRY_VETH_PEER_SUFFIX;

        // veth for the host to access docker must be created
        createVethPair(hostNic, swNic, null);

        // add xdp for the veth
        try {
            createXDPIface(sw, umem, net, swNic);
        } catch (Exception e) {
            try {
                deleteNic(sw, swNic);
            } catch (Exception e2) {
                Logger.error(LogType.SYS_ERROR, "rollback nic " + hostNic + " failed", e2);
            }
            throw e;
        }
    }

    private XDPIface createXDPIface(Switch sw, UMem umem, VirtualNetwork net, String nicname) throws Exception {
        var cmd = Command.parseStrCmd("add bpf-object " + nicname + " mode SKB force");
        var bpfobj = BPFObjectHandle.add(cmd);
        try {
            return sw.addXDP(nicname, bpfobj.getMap("xsks_map"), umem, 0,
                32, 32, BPFMode.SKB, false, 0,
                net != null ? net.vni : NETWORK_ENTRY_VNI,
                BPFMapKeySelectors.useQueueId.keySelector.get());
        } catch (Exception e) {
            try {
                Application.get().bpfObjectHolder.removeAndRelease(bpfobj.nic);
            } catch (Exception e2) {
                Logger.error(LogType.SYS_ERROR, "rollback bpf-object " + bpfobj.nic + " failed", e2);
            }
            throw e;
        }
    }

    private VirtualNetwork findNetwork(Switch sw, String networkId) throws Exception {
        var networks = sw.getNetworks();
        for (var net : networks.values()) {
            var netId = net.getAnnotations().other.get(NETWORK_NETWORK_ID_ANNOTATION);
            if (netId != null && netId.equals(networkId)) {
                return net;
            }
        }
        throw new Exception("network " + networkId + " not found");
    }

    @Override
    public synchronized void deleteNetwork(String networkId) throws Exception {
        var sw = ensureSwitch();
        var net = findNetwork(sw, networkId);

        deleteNetworkEntryVeth(sw, net);

        sw.delNetwork(net.vni);
        Logger.alert("network deleted: vni=" + net.vni + ", docker:networkId=" + networkId);

        persistConfig();
    }

    private void persistConfig() {
        List<String> netEntryIfaces = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        String currentConfig = Shutdown.currentConfig();
        String[] split = currentConfig.split("\n");
        for (String s : split) {
            if (s.startsWith("add xdp ")) {
                if (s.startsWith("add xdp " + NETWORK_ENTRY_VETH_PREFIX)) {
                    netEntryIfaces.add(s.split(" ")[2]);
                } else {
                    continue;
                }
            }
            if (s.startsWith("update iface xdp:") && !s.startsWith("update iface xdp:" + NETWORK_ENTRY_VETH_PREFIX)) {
                continue;
            }
            sb.append(s).append("\n");
        }
        try {
            Utils.writeFileWithBackup(PERSISTENT_CONFIG_FILE, sb.toString());
        } catch (Exception e) {
            Logger.error(LogType.FILE_ERROR, "persist configuration failed: " + PERSISTENT_CONFIG_FILE, e);
            return;
        }

        sb.delete(0, sb.length());
        sb.append("#!/bin/bash\n");
        for (String iface : netEntryIfaces) {
            iface = iface.substring(0, iface.length() - NETWORK_ENTRY_VETH_PEER_SUFFIX.length());
            sb.append("ip link add ").append(iface)
                .append(" type veth peer name ")
                .append(iface).append(NETWORK_ENTRY_VETH_PEER_SUFFIX)
                .append("\n");
            sb.append("ip link set ").append(iface).append(" up\n");
            sb.append("ip link set ").append(iface).append(NETWORK_ENTRY_VETH_PEER_SUFFIX).append(" up\n");
        }
        try {
            Utils.writeFileWithBackup(PERSISTENT_SCRIPT, sb.toString());
        } catch (Exception e) {
            Logger.error(LogType.FILE_ERROR, "persist script failed: " + PERSISTENT_SCRIPT, e);
        }
    }

    private void deleteNetworkEntryVeth(Switch sw, VirtualNetwork net) throws Exception {
        String swNic = NETWORK_ENTRY_VETH_PREFIX + net.vni + NETWORK_ENTRY_VETH_PEER_SUFFIX;
        deleteNic(sw, swNic);
    }

    @Override
    public synchronized CreateEndpointResponse createEndpoint(CreateEndpointRequest req) throws Exception {
        if (req.netInterface == null) {
            throw new Exception("we do not support auto ip allocation for now");
        }
        if (req.netInterface.address == null || req.netInterface.address.isEmpty()) {
            throw new Exception("ipv4 must be provided");
        }

        var sw = ensureSwitch();
        var net = findNetwork(sw, req.networkId);
        if (req.netInterface.addressIPV6 != null && !req.netInterface.addressIPV6.isEmpty()) {
            if (net.v6network == null) {
                throw new Exception("network " + req.networkId + " does not support ipv6");
            }
        }

        Map<String, String> anno = new HashMap<>();
        anno.put(VETH_ENDPOINT_ID_ANNOTATION, req.endpointId);
        anno.put(VETH_ENDPOINT_IPv4_ANNOTATION, req.netInterface.address);
        if (req.netInterface.addressIPV6 != null && !req.netInterface.addressIPV6.isEmpty()) {
            anno.put(VETH_ENDPOINT_IPv6_ANNOTATION, req.netInterface.addressIPV6);
        }
        if (req.netInterface.macAddress != null && !req.netInterface.macAddress.isEmpty()) {
            anno.put(VETH_ENDPOINT_MAC_ANNOTATION, req.netInterface.macAddress);
        }

        String swNic = "veth" + req.endpointId.substring(0, 8);
        String containerNic = swNic + CONTAINER_VETH_SUFFIX;
        createVethPair(swNic, containerNic, req.netInterface.macAddress);

        try {
            var umem = ensureUMem();

            XDPIface xdpIface = createXDPIface(sw, umem, net, swNic);
            xdpIface.setAnnotations(new Annotations(anno));
            Logger.alert("xdp added: " + xdpIface.nic + ", vni=" + net.vni
                + ", endpointId=" + req.endpointId
                + ", ipv4=" + anno.get(VETH_ENDPOINT_IPv4_ANNOTATION)
                + ", ipv6=" + anno.get(VETH_ENDPOINT_IPv6_ANNOTATION)
                + ", mac=" + anno.get(VETH_ENDPOINT_MAC_ANNOTATION)
                + ", netId=" + req.networkId
            );
        } catch (Exception e) {
            try {
                deleteNic(sw, swNic);
            } catch (Exception e2) {
                Logger.error(LogType.SOCKET_ERROR, "failed to rollback nic " + swNic, e2);
            }

            throw e;
        }
        var resp = new CreateEndpointResponse();
        resp.netInterface = null;
        return resp;
    }

    private void createVethPair(String hostVeth, String containerVeth, String mac) throws Exception {
        String scriptContent = "#!/bin/bash\n" +
            "set -e\n" +
            "ip link add " + hostVeth + " type veth peer name " + containerVeth + "\n";
        if (mac != null && !mac.isBlank()) {
            scriptContent += "" +
                "ip link set " + containerVeth + " address " + mac + "\n";
        }
        scriptContent += "" +
            "ip link set " + hostVeth + " up\n" +
            "ip link set " + containerVeth + " up\n";
        Utils.execute(scriptContent);
    }

    private XDPIface findEndpoint(Switch sw, String endpointId) throws Exception {
        var ifaces = sw.getIfaces();
        for (var iface : ifaces) {
            if (iface instanceof XDPIface) {
                var xdp = (XDPIface) iface;
                var epId = xdp.getAnnotations().other.get(VETH_ENDPOINT_ID_ANNOTATION);
                if (epId != null && epId.equals(endpointId)) {
                    return xdp;
                }
            }
        }
        throw new Exception("endpoint " + endpointId + " not found");
    }

    @Override
    public synchronized void deleteEndpoint(String networkId, String endpointId) throws Exception {
        var sw = ensureSwitch();
        findNetwork(sw, networkId);
        var xdp = findEndpoint(sw, endpointId);

        // delete nic
        try {
            deleteNic(sw, xdp.nic);
        } catch (Exception e) {
            Logger.warn(LogType.ALERT, "failed to delete nic " + xdp.nic, e);
        }
        Logger.alert("xdp deleted: " + xdp.nic + ", endpointId=" + endpointId);
    }

    private void deleteNic(Switch sw, String swNic) throws Exception {
        try {
            Application.get().bpfObjectHolder.removeAndRelease(swNic);
        } catch (NotFoundException ignore) {
        }
        try {
            sw.delXDP(swNic);
        } catch (NotFoundException ignore) {
        }

        Utils.execute("" +
            "#!/bin/bash\n" +
            "set +e\n" +
            "x=`ip link show dev " + swNic + " | wc -l`\n" +
            "set -e\n" +
            "if [ \"$x\" == \"0\" ]\n" +
            "then\n" +
            "    exit 0\n" +
            "fi\n" +
            "ip link del " + swNic + "\n");
    }

    @Override
    public synchronized JoinResponse join(String networkId, String endpointId, String sandboxKey) throws Exception {
        var sw = ensureSwitch();
        var net = findNetwork(sw, networkId);
        var xdp = findEndpoint(sw, endpointId);
        var ifName = xdp.nic + CONTAINER_VETH_SUFFIX;
        var ipv6 = xdp.getAnnotations().other.get(VETH_ENDPOINT_IPv6_ANNOTATION);

        if (net.v6network == null && ipv6 != null) {
            throw new Exception("internal error: should not reach here: " +
                "network " + networkId + " does not support ipv6 but the endpoint is assigned with ipv6 addr");
        }

        String gatewayV4 = null;
        String gatewayV6 = null;
        for (var info : net.ips.entries()) {
            var value = info.annotations.other.get(GATEWAY_IP_ANNOTATION);
            if (value != null) {
                if (value.equals(GATEWAY_IPv4_FLAG_VALUE)) {
                    gatewayV4 = info.ip.formatToIPString();
                } else if (value.equals(GATEWAY_IPv6_FLAG_VALUE)) {
                    gatewayV6 = info.ip.formatToIPString();
                }
            }
        }
        if (gatewayV4 == null) {
            throw new Exception("ipv4 gateway not found in network " + networkId);
        }
        if (gatewayV6 == null && ipv6 != null) {
            throw new Exception("ipv6 gateway not found in network " + networkId);
        }
        if (gatewayV6 != null && gatewayV6.startsWith("[") && gatewayV6.endsWith("]")) {
            gatewayV6 = gatewayV6.substring(1, gatewayV6.length() - 1);
        }

        var resp = new JoinResponse();
        resp.interfaceName.srcName = ifName;
        resp.interfaceName.dstPrefix = "eth";
        resp.gateway = gatewayV4;
        if (gatewayV6 != null && ipv6 != null) {
            resp.gatewayIPv6 = gatewayV6;
        }
        return resp;
    }

    @SuppressWarnings("RedundantThrows")
    @Override
    public synchronized void leave(String networkId, String endpointId) throws Exception {
        // do nothing
    }
}
