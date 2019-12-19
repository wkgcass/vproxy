package vproxy.dns;

/*
 *                                     1  1  1  1  1  1
 *       0  1  2  3  4  5  6  7  8  9  0  1  2  3  4  5
 *     +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *     |                                               |
 *     /                     QNAME                     /
 *     /                                               /
 *     +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *     |                     QTYPE                     |
 *     +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *     |                     QCLASS                    |
 *     +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 */

import java.util.Objects;

public class DNSQuestion {
    public String qname;
    public DNSType qtype;
    public DNSClass qclass;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DNSQuestion that = (DNSQuestion) o;
        return Objects.equals(qname, that.qname) &&
            qtype == that.qtype &&
            qclass == that.qclass;
    }

    @Override
    public int hashCode() {
        return Objects.hash(qname, qtype, qclass);
    }

    @Override
    public String toString() {
        return "DNSQuestion{" +
            "qname='" + qname + '\'' +
            ", qtype=" + qtype +
            ", qclass=" + qclass +
            '}';
    }
}
