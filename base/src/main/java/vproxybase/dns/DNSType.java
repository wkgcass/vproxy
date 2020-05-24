package vproxybase.dns;

// only record what we might care for
public enum DNSType {
    A(1), // a host addressX)
    CNAME(5), // the canonical name for an alias
    PTR(12),
    TXT(16), // text strings
    AAAA(28), // ipv6
    SRV(33), // Server Selection
    OPT(41), // OPT pseudo-RR | meta-RR

    AXFR(252, true), // only available in qtype, A request for a transfer of an entire zone
    MAILB(253, true), // only available in qtype,  A request for mailbox-related records (MB, MG or MR)
    MAILA(254, true), // only available in qtype,  A request for mail agent RRs (Obsolete - see MX)
    ANY(255, true), // only available in qtype,  A request for all records

    OTHER(-1),
    ;
    public final int code;
    public final boolean question;

    DNSType(int code) {
        this(code, false);
    }

    DNSType(int code, boolean question) {
        this.code = code;
        this.question = question;
    }
}
