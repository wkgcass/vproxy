package vproxy.component.auto;

import vproxy.component.khala.KhalaNode;

public class AutoUtil {
    private AutoUtil() {
    }

    static String utilServerNameFromNode(KhalaNode node) {
        return utilServerName(node.service, node.address, node.port);
    }

    static String utilServerName(String service, String address, int port) {
        return service + "@" + address + ":" + port;
    }
}
