package io.vproxy.vproxyx.websocks;

public class RedirectBaseInfo {
    public final String protocol;
    public final String domain;
    public final int port;

    public RedirectBaseInfo(String protocol, String domain, int port) {
        this.protocol = protocol;
        this.domain = domain;
        this.port = port;
    }
}
