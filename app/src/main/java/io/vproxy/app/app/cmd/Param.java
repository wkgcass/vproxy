package io.vproxy.app.app.cmd;

public enum Param {
    timeout("timeout"),
    period("period"),
    up("up"),
    down("down"),
    elg("event-loop-group"),
    aelg("acceptor-elg"),
    addr("address"),
    ip("ip"),
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

    pass("password"),

    cert("cert"),
    key("key"),
    ck("cert-key"),

    mactabletimeout("mac-table-timeout"),
    arptabletimeout("arp-table-timeout"),
    mac("mac"),
    vni("vni"),
    iface("iface"),
    postscript("post-script"),
    routing("routing"),

    path("path"),

    mtu("mtu"),
    flood("flood"),
    csumrecalc("csum-recalc"),

    offload("offload"),

    prog("program"),
    mode("mode"),
    umem("umem"),
    nic("nic"),
    queue("queue"),
    xskmap("xsk-map"),
    macmap("mac-map"),
    rxringsize("rx-ring-size"),
    txringsize("tx-ring-size"),
    chunks("chunks"),
    fillringsize("fill-ring-size"),
    compringsize("comp-ring-size"),
    framesize("frame-size"),
    xskmapkeyselector("xsk-map-key"),
    busypoll("busy-poll"),

    url("url"),
    cls("class"),
    args("arguments"),
    ;
    public final String fullname;

    Param(String fullname) {
        this.fullname = fullname;
    }
}
