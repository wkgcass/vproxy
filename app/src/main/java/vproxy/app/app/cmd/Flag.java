package vproxy.app.app.cmd;

public enum Flag {
    noipv4("no-ipv4"),
    noipv6("no-ipv6"),

    allownonbackend("allow-non-backend"),
    denynonbackend("deny-non-backend"),

    noswitchflag("no-switch-flag"),

    force("force"),
    zerocopy("zerocopy"),
    ;
    public final String fullname;

    Flag(String fullname) {
        this.fullname = fullname;
    }
}
