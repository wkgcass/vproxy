package vproxy.component.auto;

import vproxy.component.khala.KhalaNode;
import vproxy.util.IPType;
import vproxy.util.Utils;

import java.net.InetAddress;
import java.net.SocketException;

public class SmartServiceDelegate {
    public final String alias;
    public final String service;
    public final String zone;
    public final String nic;
    public final IPType ipType;
    public final int exposedPort;
    public final AutoConfig config;

    private final KhalaNode kNode;

    public SmartServiceDelegate(String alias,
                                String service,
                                String zone,
                                String nic,
                                IPType ipType,
                                int exposedPort,
                                AutoConfig config) throws SocketException {
        this.alias = alias;
        this.service = service;
        this.zone = zone;
        this.nic = nic;
        this.ipType = ipType;
        this.exposedPort = exposedPort;
        this.config = config;

        InetAddress address = Utils.getInetAddressFromNic(nic, ipType);

        kNode = new KhalaNode(service, zone, Utils.ipStr(address.getAddress()), exposedPort);
        this.config.khala.addLocal(kNode);
    }

    public void destroy() {
        this.config.khala.removeLocal(kNode);
    }
}
