package vproxy.dns;

public enum DNSClass {
    IN(1), // internet
    CS(2), // the CSNET class (Obsolete - used only for examples in some obsolete RFCs)
    CH(3), // the CHAOS class
    HS(4), // Hesiod [Dyer 87]

    ANY(255), // only used as QClass

    NOT_CLASS(-1), // some RR does not use this field as class name
    ;
    public final int code;

    DNSClass(int code) {
        this.code = code;
    }
}
