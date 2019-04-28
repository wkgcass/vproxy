package net.cassite.vproxy.app.cmd;

public enum Param {
    timeout("timeout"),
    period("period"),
    up("up"),
    down("down"),
    elg("event-loop-group"),
    aelg("acceptor-elg"),
    addr("address"),
    sgs("server-groups"),
    inbuffersize("in-buffer-size"),
    outbuffersize("out-buffer-size"),
    meth("method"),
    w("weight"),
    secg("security-group"),
    secgrdefault("default"),
    net("network"),
    protocol("protocol"),
    portrange("port-range"),
    tl("tcp-lb"),
    sg("server-group"),

    service("service"),
    zone("zone"),
    port("port"),

    pass("password"),
    ;
    public final String fullname;

    Param(String fullname) {
        this.fullname = fullname;
    }
}
