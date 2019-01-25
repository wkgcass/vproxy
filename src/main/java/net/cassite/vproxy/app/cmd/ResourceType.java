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

    respcontroller("resp-controller"),
    ;
    public final String fullname;

    ResourceType(String fullname) {
        this.fullname = fullname;
    }
}
