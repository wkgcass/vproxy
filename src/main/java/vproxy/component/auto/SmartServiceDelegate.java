package vproxy.component.auto;

import vproxy.component.exception.AlreadyExistException;
import vproxy.component.khala.KhalaNode;
import vproxy.util.IPType;
import vproxy.util.Utils;

import java.net.InetAddress;

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
                                AutoConfig config) throws Exception {
        this.alias = alias;
        this.service = service;
        this.zone = zone;
        this.nic = nic;
        this.ipType = ipType;
        this.exposedPort = exposedPort;
        this.config = config;

        InetAddress address = Utils.getInetAddressFromNic(nic, ipType);

        kNode = new KhalaNode(service, zone, Utils.ipStr(address.getAddress()), exposedPort);
        var localKNodes = config.khala.getNodeToKhalaNodesMap().get(config.discovery.localNode);
        if (localKNodes != null) {
            for (var kn : localKNodes) {
                if (kn.equals(kNode)) {
                    throw new AlreadyExistException("service " + service + " zone " + zone);
                }
            }
        }
        this.config.khala.addLocal(kNode);
    }

    public void destroy() {
        this.config.khala.removeLocal(kNode);
    }
}
