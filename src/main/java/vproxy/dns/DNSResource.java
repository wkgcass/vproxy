package vproxy.dns;

/*
 *                                     1  1  1  1  1  1
 *       0  1  2  3  4  5  6  7  8  9  0  1  2  3  4  5
 *     +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *     |                                               |
 *     /                                               /
 *     /                      NAME                     /
 *     |                                               |
 *     +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *     |                      TYPE                     |
 *     +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *     |                     CLASS                     |
 *     +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *     |                      TTL                      |
 *     |                                               |
 *     +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *     |                   RDLENGTH                    |
 *     +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--|
 *     /                     RDATA                     /
 *     /                                               /
 *     +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 */

import vproxy.dns.rdata.RData;
import vproxy.util.ByteArray;

import java.util.Objects;

public class DNSResource {
    public String name;
    public DNSType type;
    public DNSClass clazz;
    public int ttl;
    int rdlen;
    public ByteArray rdataBytes;
    public RData rdata;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DNSResource that = (DNSResource) o;
        return ttl == that.ttl &&
            Objects.equals(name, that.name) &&
            type == that.type &&
            clazz == that.clazz &&
            Objects.equals(rdataBytes, that.rdataBytes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, clazz, ttl, rdataBytes);
    }

    @Override
    public String toString() {
        return "DNSResource{" +
            "name='" + name + '\'' +
            ", type=" + type +
            ", clazz=" + clazz +
            ", ttl=" + ttl +
            ", rdlen=" + rdlen +
            ", rdataBytes=" + rdataBytes +
            ", rdata=" + rdata +
            '}';
    }
}
