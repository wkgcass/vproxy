package io.vproxy.app.app.cmd;

public enum Action {
    ls("list"),
    ll("list-detail"),
    add("add"),
    rm("remove"),
    mod("update"),
    ;
    public final String fullname;

    Action(String fullname) {
        this.fullname = fullname;
    }
}
