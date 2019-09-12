package vproxy.component.auto;

import vjson.util.ObjectBuilder;
import vproxy.component.check.HealthCheckConfig;
import vproxy.component.check.HealthCheckHandler;
import vproxy.component.check.TCPHealthCheckClient;
import vproxy.component.exception.AlreadyExistException;
import vproxy.component.khala.KhalaNode;
import vproxy.connection.BindServer;
import vproxy.util.IPType;
import vproxy.util.LogType;
import vproxy.util.Logger;
import vproxy.util.Utils;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;

public class SmartNodeDelegate {
    public final String alias;
    public final String service;
    public final String zone;
    public final String nic;
    public final IPType ipType;
    public final int exposedPort;
    public final int weight;
    public final AutoConfig config;
    private final TCPHealthCheckClient hcClient;
    private boolean healthy = false;

    private final KhalaNode kNode;

    private static InetAddress getAddressByNicAndIPType(String nicName, IPType ipType) throws Exception {
        Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
        while (nics.hasMoreElements()) {
            NetworkInterface nic = nics.nextElement();
            if (nic.getName().equals(nicName)) {
                Enumeration<InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (ipType == IPType.v4) {
                        if (addr instanceof Inet4Address) {
                            return addr;
                        }
                    } else {
                        assert ipType == IPType.v6;
                        if (addr instanceof Inet6Address) {
                            return addr;
                        }
                    }
                }
                throw new Exception("ip" + ipType + " not found in nic " + nicName);
            }
        }
        throw new Exception("ip" + ipType + " not found in nic " + nicName);
    }

    private static int allocatePort(String nic, IPType ipType) throws Exception {
        InetAddress l3addr = getAddressByNicAndIPType(nic, ipType);
        while (true) {
            int port = (int) ((Math.random() * 20000) + 10000); // 10000 ~ 30000
            InetSocketAddress l4addr = new InetSocketAddress(l3addr, port);
            try {
                BindServer.checkBind(l4addr);
            } catch (IOException e) {
                // bind failed, try another port
                continue;
            }
            return port;
        }
    }

    private class HcHandler implements HealthCheckHandler {
        @Override
        public void up(SocketAddress remote) {
            // ignore
        }

        @Override
        public void down(SocketAddress remote) {
            // ignore
        }

        @Override
        public void upOnce(SocketAddress remote) {
            if (healthy)
                return;
            Logger.alert("health check up for " + alias + ", register " + kNode);
            healthy = true;
            config.khala.addLocal(kNode);
        }

        @Override
        public void downOnce(SocketAddress remote) {
            if (!healthy)
                return;
            Logger.warn(LogType.KHALA_EVENT, "health check down for " + alias + ", deregister " + kNode);
            healthy = false;
            config.khala.removeLocal(kNode);
        }
    }

    public SmartNodeDelegate(String alias,
                             String service,
                             String zone,
                             String nic,
                             IPType ipType,
                             int exposedPort,
                             int weight,
                             AutoConfig config) throws Exception {
        if (exposedPort == 0) {
            exposedPort = allocatePort(nic, ipType);
        }

        this.alias = alias;
        this.service = service;
        this.zone = zone;
        this.nic = nic;
        this.ipType = ipType;
        this.exposedPort = exposedPort;
        this.weight = weight;
        this.config = config;

        InetAddress address = Utils.getInetAddressFromNic(nic, ipType);

        kNode = new KhalaNode(service, zone, Utils.ipStr(address.getAddress()), exposedPort,
            new ObjectBuilder().put("weight", weight).build());
        var localKNodes = config.khala.getNodeToKhalaNodesMap().get(config.discovery.localNode);
        if (localKNodes != null) {
            for (var kn : localKNodes) {
                if (kn.equals(kNode)) {
                    throw new AlreadyExistException("service " + service + " zone " + zone);
                }
            }
        }

        // init hc
        hcClient = new TCPHealthCheckClient(
            config.discovery.loop, new InetSocketAddress(address, exposedPort),
            new HealthCheckConfig(1000, 10_000, 1, 1), true,
            new HcHandler()
        );
        hcClient.start();
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void destroy() {
        this.config.khala.removeLocal(kNode);
        hcClient.stop();
    }
}
