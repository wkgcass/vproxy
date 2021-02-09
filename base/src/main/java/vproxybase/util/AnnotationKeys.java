package vproxybase.util;

public enum AnnotationKeys {
    ServerGroup_HintHost("vproxy/hint-host"),
    ServerGroup_HintPort("vproxy/hint-port"),
    ServerGroup_HintUri("vproxy/hint-uri"),
    ServerGroup_HCHttpMethod("vproxy/hc-http-method"),
    ServerGroup_HCHttpUrl("vproxy/hc-http-url"),
    ServerGroup_HCHttpHost("vproxy/hc-http-host"),
    ServerGroup_HCHttpStatus("vproxy/hc-http-status"),
    ServerGroup_HCDnsDomain("vproxy/hc-dns-domain"),

    SWTable_DockerNetworkDriverNetworkId("docker-network-driver/network-id"),
    SWIp_DockerNetworkDriverGatewayIp("docker-network-driver/gateway-ip"),
    SWTap_DockerNetworkDriverEndpointId("docker-network-driver/endpoint-id"),
    SWTap_DockerNetworkDriverEndpointIpv4("docker-network-driver/endpoint-ipv4"),
    SWTap_DockerNetworkDriverEndpointIpv6("docker-network-driver/endpoint-ipv6"),
    SWTap_DockerNetworkDriverEndpointMac("docker-network-driver/endpoint-mac"),
    ;
    public final String name;
    public final boolean deserialize; // will deserialize into vproxybase.util.Annotations

    AnnotationKeys(String name) {
        this.name = name;
        this.deserialize = name.startsWith("vproxy/");
    }

    @Override
    public String toString() {
        return name;
    }
}
