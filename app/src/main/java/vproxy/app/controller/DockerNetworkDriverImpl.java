package vproxy.app.controller;

import vproxy.app.app.Application;
import vproxy.base.component.elgroup.EventLoopGroup;
import vproxy.base.util.AnnotationKeys;
import vproxy.base.util.Annotations;
import vproxy.base.util.Logger;
import vproxy.base.util.Network;
import vproxy.base.util.coll.IntMap;
import vproxy.base.util.exception.NotFoundException;
import vproxy.component.secure.SecurityGroup;
import vproxy.vfd.*;
import vproxy.vswitch.Switch;
import vproxy.vswitch.Table;
import vproxy.vswitch.iface.TapIface;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DockerNetworkDriverImpl implements DockerNetworkDriver {
    private static final String SWITCH_NAME = "DockerNetworkDriverSW";
    private static final String TABLE_NETWORK_ID_ANNOTATION = AnnotationKeys.SWTable_DockerNetworkDriverNetworkId.name;
    private static final MacAddress GATEWAY_MAC_ADDRESS = new MacAddress("02:00:00:00:00:20");
    private static final String GATEWAY_IP_ANNOTATION = AnnotationKeys.SWIp_DockerNetworkDriverGatewayIp.name;
    private static final String GATEWAY_IPv4_FLAG_VALUE = "gateway-ipv4";
    private static final String GATEWAY_IPv6_FLAG_VALUE = "gateway-ipv6";
    private static final String TAP_ENDPOINT_ID_ANNOTATION = AnnotationKeys.SWTap_DockerNetworkDriverEndpointId.name;
    private static final String TAP_ENDPOINT_IPv4_ANNOTATION = AnnotationKeys.SWTap_DockerNetworkDriverEndpointIpv4.name;
    private static final String TAP_ENDPOINT_IPv6_ANNOTATION = AnnotationKeys.SWTap_DockerNetworkDriverEndpointIpv6.name;
    private static final String TAP_ENDPOINT_MAC_ANNOTATION = AnnotationKeys.SWTap_DockerNetworkDriverEndpointMac.name;
    private static final String POST_SCRIPT_BASE_DIRECTORY = "/var/vproxy/docker-network-plugin/post-scripts/";

    @Override
    public synchronized void createNetwork(CreateNetworkRequest req) throws Exception {
        // check ipv4 data length
        if (req.ipv4Data.size() > 1) {
            throw new Exception("we only support at most one ipv4 cidr in one network");
        }
        if (req.ipv6Data.size() > 1) {
            throw new Exception("we only support at most one ipv6 cidr in one network");
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
        }

        // handle
        var sw = ensureSwitch();
        IntMap<Table> tables = sw.getTables();
        int n = 0;
        for (int i : tables.keySet()) {
            if (n < i) {
                n = i;
            }
        }
        n += 1; // greater than the biggest recorded vni

        Network v4net;
        {
            v4net = new Network(req.ipv4Data.get(0).pool);
        }
        Network v6net = null;
        if (!req.ipv6Data.isEmpty()) {
            v6net = new Network(req.ipv6Data.get(0).pool);
        }
        sw.addTable(n, v4net, v6net, new Annotations(Collections.singletonMap(TABLE_NETWORK_ID_ANNOTATION, req.networkId)));
        Logger.alert("table added: vni=" + n + ", v4=" + v4net + ", v6=" + v6net + ", docker:networkId=" + req.networkId);
        Table tbl = sw.getTable(n);
        if (!req.networkId.equals(tbl.getAnnotations().other.get(TABLE_NETWORK_ID_ANNOTATION))) {
            Logger.shouldNotHappen("adding table failed, maybe concurrent modification");
            throw new Exception("unexpected state");
        }

        // add ipv4 gateway ip
        {
            var gateway = IP.from(req.ipv4Data.get(0).__transformedGateway);
            var mac = GATEWAY_MAC_ADDRESS;
            tbl.addIp(gateway, mac, new Annotations(Collections.singletonMap(GATEWAY_IP_ANNOTATION, GATEWAY_IPv4_FLAG_VALUE)));
            Logger.alert("ip added: vni=" + n + ", ip=" + gateway + ", mac=" + mac);
        }

        if (!req.ipv6Data.isEmpty()) {
            // add ipv6 gateway ip
            var gateway = IP.from(req.ipv6Data.get(0).__transformedGateway);
            var mac = GATEWAY_MAC_ADDRESS;
            tbl.addIp(gateway, mac, new Annotations(Collections.singletonMap(GATEWAY_IP_ANNOTATION, GATEWAY_IPv6_FLAG_VALUE)));
            Logger.alert("ip added: vni=" + n + ", ip=" + gateway + ", mac=" + mac);
        }
    }

    private Switch ensureSwitch() throws Exception {
        try {
            return Application.get().switchHolder.get(SWITCH_NAME);
        } catch (NotFoundException e) {
            // need to create one
            EventLoopGroup elg;
            try {
                elg = Application.get().eventLoopGroupHolder.get(Application.DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME);
            } catch (NotFoundException x) {
                Logger.shouldNotHappen(Application.DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME + " not exists");
                throw new RuntimeException("should not happen: no event loop to handle the request");
            }
            var ret = Application.get().switchHolder.add(
                SWITCH_NAME,
                new IPPort("127.7.7.7", 7777),
                elg,
                300000,
                14400000,
                SecurityGroup.allowAll(),
                1500,
                true);
            Logger.alert("switch " + SWITCH_NAME + " created");
            return ret;
        }
    }

    private Table findNetwork(Switch sw, String networkId) throws Exception {
        var tables = sw.getTables();
        for (var tbl : tables.values()) {
            var netId = tbl.getAnnotations().other.get(TABLE_NETWORK_ID_ANNOTATION);
            if (netId != null && netId.equals(networkId)) {
                return tbl;
            }
        }
        throw new Exception("network " + networkId + " not found");
    }

    @Override
    public synchronized void deleteNetwork(String networkId) throws Exception {
        var sw = ensureSwitch();
        var tbl = findNetwork(sw, networkId);
        sw.delTable(tbl.vni);
        Logger.alert("table deleted: vni=" + tbl.vni + ", docker:networkId=" + networkId);
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
        var tbl = findNetwork(sw, req.networkId);
        if (req.netInterface.addressIPV6 != null && !req.netInterface.addressIPV6.isEmpty()) {
            if (tbl.v6network == null) {
                throw new Exception("network " + req.networkId + " does not support ipv6");
            }
        }

        Map<String, String> anno = new HashMap<>();
        anno.put(TAP_ENDPOINT_ID_ANNOTATION, req.endpointId);
        anno.put(TAP_ENDPOINT_IPv4_ANNOTATION, req.netInterface.address);
        if (req.netInterface.addressIPV6 != null && !req.netInterface.addressIPV6.isEmpty()) {
            anno.put(TAP_ENDPOINT_IPv6_ANNOTATION, req.netInterface.addressIPV6);
        }
        if (req.netInterface.macAddress != null && !req.netInterface.macAddress.isEmpty()) {
            anno.put(TAP_ENDPOINT_MAC_ANNOTATION, req.netInterface.macAddress);
        }

        ensurePostScript(req.endpointId, "");

        String nameSuffix = req.endpointId.substring(0, 12);
        TapIface tapIface = sw.addTap("tap" + nameSuffix, tbl.vni, POST_SCRIPT_BASE_DIRECTORY + req.endpointId, new Annotations(anno));
        String tapName = tapIface.getTap().getTap().dev;
        Logger.alert("tap added: " + tapName + ", vni=" + tbl.vni
            + ", endpointId=" + req.endpointId
            + ", ipv4=" + anno.get(TAP_ENDPOINT_IPv4_ANNOTATION)
            + ", ipv6=" + anno.get(TAP_ENDPOINT_IPv6_ANNOTATION)
            + ", mac=" + anno.get(TAP_ENDPOINT_MAC_ANNOTATION)
            + ", netId=" + req.networkId
        );
        var resp = new CreateEndpointResponse();
        resp.netInterface = null;
        return resp;
    }

    private void ensurePostScript(String filename, String content) throws Exception {
        File dir = new File(POST_SCRIPT_BASE_DIRECTORY);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new Exception("mkdir " + POST_SCRIPT_BASE_DIRECTORY + " failed");
            }
        }
        File file = new File(POST_SCRIPT_BASE_DIRECTORY + filename);
        if (!file.exists()) {
            if (!file.createNewFile()) {
                throw new Exception("creating post script " + filename + " failed");
            }
        }
        byte[] bytes = content.getBytes();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(bytes);
            fos.flush();
        }
        if (!file.setExecutable(true)) {
            throw new Exception("setting post script +x failed");
        }
        Logger.alert("post script updated(" + bytes.length + " bytes): " + file.getAbsolutePath());
    }

    private TapIface findEndpoint(Switch sw, String endpointId) throws Exception {
        var ifaces = sw.getIfaces();
        for (var iface : ifaces) {
            if (iface instanceof TapIface) {
                var tap = (TapIface) iface;
                var epId = tap.annotations.other.get(TAP_ENDPOINT_ID_ANNOTATION);
                if (epId != null && epId.equals(endpointId)) {
                    return tap;
                }
            }
        }
        throw new Exception("endpoint " + endpointId + " not found");
    }

    @Override
    public synchronized void deleteEndpoint(String networkId, String endpointId) throws Exception {
        var sw = ensureSwitch();
        findNetwork(sw, networkId);
        var tap = findEndpoint(sw, endpointId);
        sw.delTap(tap.getTap().getTap().dev);
        Logger.alert("tap deleted: " + tap.getTap().getTap().dev + ", endpointId=" + endpointId);

        File f = new File(POST_SCRIPT_BASE_DIRECTORY + endpointId);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        Logger.alert("post script deleted: " + f.getAbsolutePath());
    }

    @Override
    public synchronized JoinResponse join(String networkId, String endpointId, String sandboxKey) throws Exception {
        var sw = ensureSwitch();
        var tbl = findNetwork(sw, networkId);
        var tap = findEndpoint(sw, endpointId);
        var tapName = tap.getTap().getTap().dev;
        var ipv4 = tap.annotations.other.get(TAP_ENDPOINT_IPv4_ANNOTATION);
        var ipv6 = tap.annotations.other.get(TAP_ENDPOINT_IPv6_ANNOTATION);
        var mac = tap.annotations.other.get(TAP_ENDPOINT_MAC_ANNOTATION);

        if (tbl.v6network == null && ipv6 != null) {
            throw new Exception("internal error: should not reach here: " +
                "network " + networkId + " does not support ipv6 but the endpoint is assigned with ipv6 addr");
        }

        String gatewayV4 = null;
        String gatewayV6 = null;
        for (var info : tbl.ips.entries()) {
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

        String postScript = "";
        {
            String netnsAlias = sandboxKey.substring(sandboxKey.lastIndexOf("/") + 1);
            postScript += "#!/bin/bash\n";
            postScript += "set -e\n";
            postScript += "if [ ! -f " + sandboxKey + " ]\n";
            postScript += "then\n";
            postScript += "  rm -f " + POST_SCRIPT_BASE_DIRECTORY + endpointId + "\n";
            postScript += "  exit 0\n";
            postScript += "fi\n";
            postScript += "if [ ! -d /var/run/netns ]\n";
            postScript += "then\n";
            postScript += "  mkdir -p /var/run/netns\n";
            postScript += "fi\n";
            postScript += "if [ ! -f /var/run/netns/" + netnsAlias + " ]\n";
            postScript += "then\n";
            postScript += "  ln -s " + sandboxKey + " /var/run/netns/" + netnsAlias + "\n";
            postScript += "fi\n";
            postScript += "ip link set $DEV netns " + netnsAlias + "\n";
            // change nic name
            postScript += "nic_list=`ip netns exec " + netnsAlias + " ip -o link show | awk -F': ' '{print $2}'`\n";
            postScript += "n=0\n";
            postScript += "while [ 1 ]\n";
            postScript += "do\n";
            postScript += "  found=0\n";
            postScript += "  for nic in $nic_list\n";
            postScript += "  do\n";
            postScript += "    if [ \"eth$n\" == $nic ]\n";
            postScript += "    then\n";
            postScript += "      found=1\n";
            postScript += "      break\n";
            postScript += "    fi\n";
            postScript += "  done\n";
            postScript += "  if [ \"$found\" == \"0\" ]\n";
            postScript += "  then\n";
            postScript += "    break\n";
            postScript += "  fi\n";
            postScript += "  n=$((n + 1))\n";
            postScript += "done\n";
            postScript += "NEW_DEV=\"eth$n\"\n";
            postScript += "ip netns exec " + netnsAlias + " ip link set $DEV name $NEW_DEV\n";
            postScript += "DEV=\"$NEW_DEV\"\n";
            // set mac
            if (mac != null) {
                postScript += "ip netns exec " + netnsAlias + " ip link set $DEV address " + mac + "\n";
            }
            // set to up
            postScript += "ip netns exec " + netnsAlias + " ip link set $DEV up\n";
            // ipv4
            {
                postScript += "ip netns exec " + netnsAlias + " ip address add " + ipv4 + " dev $DEV\n";
                postScript += "ip netns exec " + netnsAlias + " ip route add default via " + gatewayV4 + " dev $DEV\n";
            }
            // ipv6
            if (ipv6 != null) {
                postScript += "ip netns exec " + netnsAlias + " sysctl -w net.ipv6.conf.$DEV.disable_ipv6=0\n";
                postScript += "ip netns exec " + netnsAlias + " ip -6 address add " + ipv6 + " dev $DEV\n";
                postScript += "ip netns exec " + netnsAlias + " ip -6 route add default via " + gatewayV6 + " dev $DEV\n";
            }
            // remove the symbolic link after handling
            postScript += "rm -f /var/run/netns/" + netnsAlias + "\n";
        }
        ensurePostScript(endpointId, postScript);

        var resp = new JoinResponse();
        resp.interfaceName.srcName = tapName;
        resp.interfaceName.dstPrefix = "eth";
        resp.gateway = gatewayV4;
        if (gatewayV6 != null && ipv6 != null) {
            resp.gatewayIPv6 = gatewayV6;
        }
        return resp;
    }

    @Override
    public synchronized void leave(String networkId, String endpointId) throws Exception {
        ensurePostScript(endpointId, "");
    }
}
