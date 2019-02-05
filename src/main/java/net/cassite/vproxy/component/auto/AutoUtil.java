package net.cassite.vproxy.component.auto;

import net.cassite.vproxy.component.khala.KhalaNode;

public class AutoUtil {
    private AutoUtil() {
    }

    static String utilLBNameFromServiceName(String mainResource, String service) {
        return mainResource + ":" + service; // use the service name as the lb name
    }

    static String utilSgsName(String mainResource, String service) {
        return mainResource + ":groups:" + service;
    }

    static String utilServerGroupNameFromServiceName(String mainResource, String usage, String service) {
        return mainResource + ":" + usage + ":grp:" + service;
    }

    static String utilServerNameFromNode(KhalaNode node) {
        return node.service + "@" + node.address + ":" + node.port;
    }
}
