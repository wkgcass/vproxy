package vproxy.app.app.cmd;

public enum Param {
    timeout("timeout"),
    period("period"),
    up("up"),
    down("down"),
    elg("event-loop-group"),
    aelg("acceptor-elg"),
    addr("address"),
    via("via"),
    ups("upstream"),
    inbuffersize("in-buffer-size"),
    outbuffersize("out-buffer-size"),
    meth("method"),
    weight("weight"),
    secg("security-group"),
    secgrdefault("default"),
    net("network"),
    v4net("v4network"),
    v6net("v6network"),
    protocol("protocol"),
    portrange("port-range"),
    tl("tcp-lb"),
    sg("server-group"),
    ttl("ttl"),
    anno("annotations"),

    service("service"),
    zone("zone"),
    nic("nic"),
    iptype("ip-type"),
    port("port"),

    pass("password"),

    cert("cert"),
    key("key"),
    ck("cert-key"),

    mactabletimeout("mac-table-timeout"),
    arptabletimeout("arp-table-timeout"),
    mac("mac"),
    vni("vni"),
    postscript("post-script"),

    path("path"),

    mtu("mtu"),
    flood("flood"),
    ;
    public final String fullname;

    Param(String fullname) {
        this.fullname = fullname;
    }
}