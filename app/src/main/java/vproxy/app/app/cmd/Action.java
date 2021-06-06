package vproxy.app.app.cmd;

public enum Action {
    l("list"),
    L("list-detail"),
    a("add"),
    r("remove"),
    R("force-remove"),
    u("update"),
    ;
    public final String fullname;

    Action(String fullname) {
        this.fullname = fullname;
    }
}
