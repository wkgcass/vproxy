package vproxy.dns;

public enum DNSType {
    A(1), // a host address
    NS(2), // an authoritative name server
    MD(3), // a mail destination (Obsolete - use MX)
    MF(4), // a mail forwarder (Obsolete - use MX)
    CNAME(5), // the canonical name for an alias
    SOA(6), // marks the start of a zone of authority
    MB(7), // a mailbox domain name (EXPERIMENTAL)
    MG(8), // a mail group member (EXPERIMENTAL)
    MR(9), // a mail rename domain name (EXPERIMENTAL)
    NULL(10), // a null RR (EXPERIMENTAL)
    WKS(11), // a well known service description
    PTR(12), // a domain name pointer
    HINFO(13), // host information
    MINFO(14), // mailbox or mail list information
    MX(15), // mail exchange
    TXT(16), // text strings
    AAAA(28), // ipv6
    OPT(41), // OPT pseudo-RR | meta-RR

    AXFR(252), // only available in qtype, A request for a transfer of an entire zone
    MAILB(253), // only available in qtype,  A request for mailbox-related records (MB, MG or MR)
    MAILA(254), // only available in qtype,  A request for mail agent RRs (Obsolete - see MX)
    ANY(255), // only available in qtype,  A request for all records
    ;
    public final int code;

    DNSType(int code) {
        this.code = code;
    }
}
