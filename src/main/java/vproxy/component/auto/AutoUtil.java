package vproxy.component.auto;

import vproxy.component.khala.KhalaNode;

public class AutoUtil {
    private AutoUtil() {
    }

    static String utilLBNameFromServiceName(String mainResource, String service) {
        return mainResource + ":" + service; // use the service name as the lb name
    }

    static String utilExtractServiceNameFromName(String mainResource, String name) {
        return name.substring(mainResource.length() + ":".length());
    }

    static String utilSgsName(String mainResource, String service) {
        return mainResource + ":groups:" + service;
    }

    static String utilServerGroupNameFromServiceName(String service) {
        return service;
    }

    static String utilServerNameFromNode(KhalaNode node) {
        return utilServerName(node.service, node.address, node.port);
    }

    static String utilServerName(String service, String address, int port) {
        return service + "@" + address + ":" + port;
    }
}
