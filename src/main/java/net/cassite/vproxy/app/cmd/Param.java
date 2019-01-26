package net.cassite.vproxy.app.cmd;

public enum Param {
    timeout("timeout"),
    period("period"),
    up("up"),
    down("down"),
    elg("event-loop-group"),
    aelg("acceptor-elg"),
    addr("address"),
    ip("via"),
    sgs("server-groups"),
    inbuffersize("in-buffer-size"),
    outbuffersize("out-buffer-size"),
    meth("method"),
    w("weight"),

    pass("password"),
    ;
    public final String fullname;

    Param(String fullname) {
        this.fullname = fullname;
    }
}
