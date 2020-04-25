package vproxy.app.cmd;

public enum ResourceType {
    tl("tcp-lb"),
    socks5("socks5-server"),
    dns("dns-server"),
    elg("event-loop-group"),
    ups("upstream"),
    sg("server-group"),
    el("event-loop"),
    svr("server"),
    ss("server-sock"),
    conn("connection"),
    sess("session"),
    bin("bytes-in"),
    bout("bytes-out"),
    acceptedconncount("accepted-conn-count"),
    secg("security-group"),
    secgr("security-group-rule"),
    resolver("resolver"),
    dnscache("dns-cache"),
    ck("cert-key"),

    sw("switch"),
    vpc("vpc"),
    arp("arp"),
    iface("iface"),
    user("user"),
    ip("ip"),
    route("route"),

    respcontroller("resp-controller"),
    httpcontroller("http-controller"),
    ;
    public final String fullname;

    ResourceType(String fullname) {
        this.fullname = fullname;
    }
}
