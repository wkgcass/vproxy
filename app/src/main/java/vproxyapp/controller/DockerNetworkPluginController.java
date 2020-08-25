package vproxyapp.controller;

import vjson.JSON;
import vjson.util.ObjectBuilder;
import vproxybase.util.LogType;
import vproxybase.util.Logger;
import vserver.HttpServer;
import vserver.RoutingContext;
import vserver.Tool;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class DockerNetworkPluginController {
    private static final String dockerNetworkPluginBase = "/";
    private static final DockerNetworkDriver driver = new DockerNetworkDriverImpl();

    static void init(HttpServer server) {
        new DockerNetworkPluginController(server);
    }

    private DockerNetworkPluginController(HttpServer server) {
        // see https://github.com/moby/libnetwork/blob/master/docs/remote.md
        server.pst(dockerNetworkPluginBase + "/*", Tool.bodyJsonHandler());
        server.pst(dockerNetworkPluginBase + "/Plugin.Activate", this::handshake);
        server.pst(dockerNetworkPluginBase + "/NetworkDriver.GetCapabilities", this::capabilities);
        server.pst(dockerNetworkPluginBase + "/NetworkDriver.CreateNetwork", this::createNetwork);
        server.pst(dockerNetworkPluginBase + "/NetworkDriver.DeleteNetwork", this::deleteNetwork);
        server.pst(dockerNetworkPluginBase + "/NetworkDriver.CreateEndpoint", this::createEndpoint);
        server.pst(dockerNetworkPluginBase + "/NetworkDriver.EndpointOperInfo", this::endpointOperationalInfo);
        server.pst(dockerNetworkPluginBase + "/NetworkDriver.DeleteEndpoint", this::deleteEndpoint);
        server.pst(dockerNetworkPluginBase + "/NetworkDriver.Join", this::join);
        server.pst(dockerNetworkPluginBase + "/NetworkDriver.Leave", this::leave);
        server.pst(dockerNetworkPluginBase + "/NetworkDriver.DiscoverNew", this::discoverNew);
        server.pst(dockerNetworkPluginBase + "/NetworkDriver.DiscoverDelete", this::discoverDelete);
    }

    private void handshake(RoutingContext rctx) {
        rctx.response().end(new ObjectBuilder()
            .putArray("Implements", arr ->
                arr.add("NetworkDriver"))
            .build());
    }

    private void capabilities(RoutingContext rctx) {
        rctx.response().end(new ObjectBuilder()
            .put("Scope", "local")
            .put("ConnectivityScope", "local")
            .build());
    }

    private JSON.Object err(String err) {
        return new ObjectBuilder()
            .put("Err", err)
            .build();
    }

    private void createNetwork(RoutingContext rctx) {
        var req = new DockerNetworkDriver.CreateNetworkRequest();
        try {
            var body = (JSON.Object) rctx.get(Tool.bodyJson);
            req.networkId = body.getString("NetworkID");
            req.ipv4Data = new LinkedList<>();
            var ipv4Data = body.getArray("IPv4Data");
            parseIPData(req.ipv4Data, ipv4Data);
            req.ipv6Data = new LinkedList<>();
            var ipv6Data = body.getArray("IPv6Data");
            parseIPData(req.ipv6Data, ipv6Data);
        } catch (RuntimeException e) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "invalid request body: ", e);
            rctx.response().end(err("invalid request body"));
            return;
        }
        try {
            driver.createNetwork(req);
        } catch (Exception e) {
            Logger.error(LogType.SYS_ERROR, "failed to create network", e);
            rctx.response().end(err(e.getMessage()));
            return;
        }
        rctx.response().end(new ObjectBuilder().build());
    }

    private void parseIPData(List<DockerNetworkDriver.IPData> ipv4Data, JSON.Array raw) {
        for (int i = 0; i < raw.length(); ++i) {
            var obj = raw.getObject(i);
            var data = new DockerNetworkDriver.IPData();
            data.addressSpace = obj.getString("AddressSpace");
            data.pool = obj.getString("Pool");
            data.gateway = obj.getString("Gateway");
            if (obj.containsKey("AuxAddresses")) {
                data.auxAddresses = new HashMap<>();
                var auxAddresses = obj.getObject("AuxAddresses");
                for (var key : auxAddresses.keySet()) {
                    data.auxAddresses.put(key, auxAddresses.getString(key));
                }
            }
            ipv4Data.add(data);
        }
    }

    private void deleteNetwork(RoutingContext rctx) {
        String networkId;
        try {
            networkId = ((JSON.Object) rctx.get(Tool.bodyJson)).getString("NetworkID");
        } catch (RuntimeException e) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "invalid request body: ", e);
            rctx.response().end(err("invalid request body"));
            return;
        }
        try {
            driver.deleteNetwork(networkId);
        } catch (Exception e) {
            Logger.error(LogType.SYS_ERROR, "failed to delete network", e);
            rctx.response().end(err(e.getMessage()));
            return;
        }
        rctx.response().end(new ObjectBuilder().build());
    }

    private void createEndpoint(RoutingContext rctx) {
        var req = new DockerNetworkDriver.CreateEndpointRequest();
        try {
            var body = (JSON.Object) rctx.get(Tool.bodyJson);
            req.networkId = body.getString("NetworkID");
            req.endpointId = body.getString("EndpointID");
            if (body.containsKey("Interface")) {
                var interf = body.getObject("Interface");
                req.netInterface = new DockerNetworkDriver.NetInterface();
                req.netInterface.address = interf.getString("Address");
                req.netInterface.addressIPV6 = interf.getString("AddressIPv6");
                req.netInterface.macAddress = interf.getString("MacAddress");
            }
        } catch (RuntimeException e) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "invalid request body: ", e);
            rctx.response().end(err("invalid request body"));
            return;
        }
        DockerNetworkDriver.CreateEndpointResponse resp;
        try {
            resp = driver.createEndpoint(req);
        } catch (Exception e) {
            Logger.error(LogType.SYS_ERROR, "failed to create endpoint", e);
            rctx.response().end(err(e.getMessage()));
            return;
        }
        if (resp.netInterface == null) {
            rctx.response().end(new ObjectBuilder().build());
        } else {
            rctx.response().end(new ObjectBuilder()
                .putObject("Interface", o -> o
                    .put("Address", resp.netInterface.address)
                    .put("AddressIPv6", resp.netInterface.addressIPV6)
                    .put("MacAddress", resp.netInterface.macAddress)
                )
                .build());
        }
    }

    private void endpointOperationalInfo(RoutingContext rctx) {
        rctx.response().end(new ObjectBuilder().putObject("Value", o -> {
        }).build());
    }

    private void deleteEndpoint(RoutingContext rctx) {
        String networkId;
        String endpointId;
        try {
            var body = (JSON.Object) rctx.get(Tool.bodyJson);
            networkId = body.getString("NetworkID");
            endpointId = body.getString("EndpointID");
        } catch (RuntimeException e) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "invalid request body: ", e);
            rctx.response().end("invalid request body");
            return;
        }
        try {
            driver.deleteEndpoint(networkId, endpointId);
        } catch (Exception e) {
            Logger.error(LogType.SYS_ERROR, "failed to delete endpoint", e);
            rctx.response().end(err(e.getMessage()));
            return;
        }
        rctx.response().end(new ObjectBuilder().build());
    }

    private void join(RoutingContext rctx) {
        String networkId;
        String endpointId;
        String sandboxKey;
        try {
            var body = (JSON.Object) rctx.get(Tool.bodyJson);
            networkId = body.getString("NetworkID");
            endpointId = body.getString("EndpointID");
            sandboxKey = body.getString("SandboxKey");
        } catch (RuntimeException e) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "invalid request body: ", e);
            rctx.response().end("invalid request body");
            return;
        }
        DockerNetworkDriver.JoinResponse resp;
        try {
            resp = driver.join(networkId, endpointId, sandboxKey);
        } catch (Exception e) {
            Logger.error(LogType.SYS_ERROR, "failed to join", e);
            rctx.response().end(err(e.getMessage()));
            return;
        }
        rctx.response().end(new ObjectBuilder()
            .putObject("InterfaceName", o -> o
                .put("SrcName", resp.interfaceName.srcName)
                .put("DstPrefix", resp.interfaceName.dstPrefix)
            )
            .put("Gateway", resp.gateway)
            .put("GatewayIPv6", resp.gatewayIPv6)
            .putArray("StaticRoutes", arr -> {
                for (var x : resp.staticRoutes) {
                    arr.addObject(o -> {
                        o.put("Destination", x.destination)
                            .put("RouteType", x.routeType);
                        if (x.nextHop != null) {
                            o.put("NextHop", x.nextHop);
                        }
                    });
                }
            })
            .build());
    }

    private void leave(RoutingContext rctx) {
        String networkId;
        String endpointId;
        try {
            var body = (JSON.Object) rctx.get(Tool.bodyJson);
            networkId = body.getString("NetworkID");
            endpointId = body.getString("EndpointID");
        } catch (RuntimeException e) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "invalid request body: ", e);
            rctx.response().end("invalid request body");
            return;
        }
        try {
            driver.leave(networkId, endpointId);
        } catch (Exception e) {
            Logger.error(LogType.SYS_ERROR, "failed to leave", e);
            rctx.response().end(err(e.getMessage()));
            return;
        }
        rctx.response().end(new ObjectBuilder().build());
    }

    private void discoverNew(RoutingContext rctx) {
        // TODO do not care about this event, this driver only work on local for now
        rctx.response().end(new ObjectBuilder().build());
    }

    private void discoverDelete(RoutingContext rctx) {
        // TODO do not care about this event, this driver only work on local for now
        rctx.response().end(new ObjectBuilder().build());
    }
}
