package net.cassite.vproxy.app.cmd;

public enum ResourceType {
    tl("tcp-lb"),
    elg("event-loop-group"),
    sgs("server-groups"),
    sg("server-group"),
    el("event-loop"),
    svr("server"),
    bs("bind-server"),
    conn("connection"),
    sess("session"),
    bin("bytes-in"),
    bout("bytes-out"),
    acceptedconncount("accepted-conn-count"),
    secg("security-group"),
    secgr("security-group-rule"),
    persist("persist"),
    resolver("resolver"),
    dnscache("dns-cache"),

    respcontroller("resp-controller"),
    ;
    public final String fullname;

    ResourceType(String fullname) {
        this.fullname = fullname;
    }
}
