package vproxyapp.controller;

import vfd.*;
import vproxy.component.secure.SecurityGroup;
import vproxyapp.app.Application;
import vproxybase.component.elgroup.EventLoopGroup;
import vproxybase.util.AnnotationKeys;
import vproxybase.util.Logger;
import vproxybase.util.Network;
import vproxybase.util.exception.NotFoundException;
import vswitch.Switch;
import vswitch.Table;
import vswitch.iface.TapIface;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DockerNetworkDriverImpl implements DockerNetworkDriver {
    private static final String SWITCH_NAME = "DockerNetworkDriverSW";
    private static final String TABLE_NETWORK_ID_ANNOTATION = AnnotationKeys.SWTable_DockerNetworkDriverNetworkId;
    private static final MacAddress GATEWAY_MAC_ADDRESS = new MacAddress("02:00:00:00:00:20");
    private static final String GATEWAY_IP_ANNOTATION = AnnotationKeys.SWIp_DockerNetworkDriverGatewayIp;
    private static final String GATEWAY_IPv4_FLAG_VALUE = "gateway-ipv4";
    private static final String GATEWAY_IPv6_FLAG_VALUE = "gateway-ipv6";
    private static final String TAP_ENDPOINT_ID_ANNOTATION = AnnotationKeys.SWTap_DockerNetworkDriverEndpointId;
    private static final String TAP_ENDPOINT_IPv4_ANNOTATION = AnnotationKeys.SWTap_DockerNetworkDriverEndpointIpv4;
    private static final String TAP_ENDPOINT_IPv6_ANNOTATION = AnnotationKeys.SWTap_DockerNetworkDriverEndpointIpv6;
    private static final String TAP_ENDPOINT_MAC_ANNOTATION = AnnotationKeys.SWTap_DockerNetworkDriverEndpointMac;

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
        Map<Integer, Table> tables = sw.getTables();
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
        sw.addTable(n, v4net, v6net, Collections.singletonMap(TABLE_NETWORK_ID_ANNOTATION, req.networkId));
        Logger.alert("table added: vni=" + n + ", v4=" + v4net + ", v6=" + v6net + ", docker:networkId=" + req.networkId);
        Table tbl = sw.getTable(n);
        if (!req.networkId.equals(tbl.getAnnotations().get(TABLE_NETWORK_ID_ANNOTATION))) {
            Logger.shouldNotHappen("adding table failed, maybe concurrent modification");
            throw new Exception("unexpected state");
        }

        // add ipv4 gateway ip
        {
            var gateway = IP.from(req.ipv4Data.get(0).__transformedGateway);
            var mac = GATEWAY_MAC_ADDRESS;
            tbl.addIp(gateway, mac, Collections.singletonMap(GATEWAY_IP_ANNOTATION, GATEWAY_IPv4_FLAG_VALUE));
            Logger.alert("ip added: vni=" + n + ", ip=" + gateway + ", mac=" + mac);
        }

        if (!req.ipv6Data.isEmpty()) {
            // add ipv6 gateway ip
            var gateway = IP.from(req.ipv6Data.get(0).__transformedGateway);
            var mac = GATEWAY_MAC_ADDRESS;
            tbl.addIp(gateway, mac, Collections.singletonMap(GATEWAY_IP_ANNOTATION, GATEWAY_IPv6_FLAG_VALUE));
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
                SecurityGroup.allowAll());
            Logger.alert("switch " + SWITCH_NAME + " created");
            return ret;
        }
    }

    private Table findNetwork(Switch sw, String networkId) throws Exception {
        var tables = sw.getTables();
        for (var entry : tables.entrySet()) {
            var tbl = entry.getValue();
            var netId = tbl.getAnnotations().get(TABLE_NETWORK_ID_ANNOTATION);
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
        String suffix = req.endpointId.substring(0, 12);
        String tapName = sw.addTap("tap" + suffix, tbl.vni, null, anno);
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

    private TapIface findEndpoint(Switch sw, String endpointId) throws Exception {
        var ifaces = sw.getIfaces();
        for (var iface : ifaces) {
            if (iface instanceof TapIface) {
                var tap = (TapIface) iface;
                var epId = tap.annotations.get(TAP_ENDPOINT_ID_ANNOTATION);
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
        sw.delTap(tap.tap.getTap().dev);
        Logger.alert("tap deleted: " + tap.tap.getTap().dev + ", endpointId=" + endpointId);
    }

    @Override
    public synchronized JoinResponse join(String networkId, String endpointId, String sandboxKey) throws Exception {
        var sw = ensureSwitch();
        var tbl = findNetwork(sw, networkId);
        var tap = findEndpoint(sw, endpointId);
        var tapName = tap.tap.getTap().dev;
        // var ipv4 = tap.annotations.get(TAP_ENDPOINT_IPv4_ANNOTATION);
        var ipv6 = tap.annotations.get(TAP_ENDPOINT_IPv6_ANNOTATION);
        // var mac = tap.annotations.get(TAP_ENDPOINT_MAC_ANNOTATION);

        if (tbl.v6network == null && ipv6 != null) {
            throw new Exception("internal error: should not reach here: " +
                "network " + networkId + " does not support ipv6 but the endpoint is assigned with ipv6 addr");
        }

        String gatewayV4 = null;
        String gatewayV6 = null;
        for (var info : tbl.ips.entries()) {
            var value = info.annotations.get(GATEWAY_IP_ANNOTATION);
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

        var resp = new JoinResponse();
        resp.interfaceName.srcName = tapName;
        resp.interfaceName.dstPrefix = "eth";
        resp.gateway = gatewayV4;
        if (gatewayV6 != null && ipv6 != null) {
            if (gatewayV6.startsWith("[") && gatewayV6.endsWith("]")) {
                gatewayV6 = gatewayV6.substring(1, gatewayV6.length() - 1);
            }
            resp.gatewayIPv6 = gatewayV6;
        }
        return resp;
    }

    @Override
    public synchronized void leave(String networkId, String endpointId) {
        // do nothing when leaving
    }
}
