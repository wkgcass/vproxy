package net.cassite.vproxy.app.cmd;

public enum Flag {
    noipv4("no-ipv4"),
    noipv6("no-ipv6"),
    ;
    public final String fullname;

    Flag(String fullname) {
        this.fullname = fullname;
    }
}
