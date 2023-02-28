package io.vproxy.vpacket.dns;

import io.vproxy.base.util.ByteArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
 * 1  1  1  1  1  1
 * 0  1  2  3  4  5  6  7  8  9  0  1  2  3  4  5
 * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * |                      ID                       |
 * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * |QR|   Opcode  |AA|TC|RD|RA|   Z    |   RCODE   |
 * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * |                    QDCOUNT                    |
 * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * |                    ANCOUNT                    |
 * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * |                    NSCOUNT                    |
 * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * |                    ARCOUNT                    |
 * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 */

public class DNSPacket {
    public int id;
    public boolean isResponse; // QR
    public Opcode opcode;
    public boolean aa; // Authoritative Answer
    public boolean tc; // TrunCation
    public boolean rd; // Recursion Desired
    public boolean ra; // Recursion Available
    public RCode rcode;
    int qdcount;
    int ancount;
    int nscount;
    int arcount;
    public List<DNSQuestion> questions = new ArrayList<>();
    public List<DNSResource> answers = new ArrayList<>();
    public List<DNSResource> nameServers = new ArrayList<>();
    public List<DNSResource> additionalResources = new ArrayList<>();

    public enum Opcode {
        QUERY(0),
        IQUERY(1),
        STATUS(2),
        Notify(4),
        Update(5),
        DSO(6), // dns stateful operations
        ;
        public final int code;

        Opcode(int code) {
            this.code = code;
        }
    }

    public enum RCode {
        NoError(0),
        FormatError(1),
        ServerFailure(2),
        NameError(3),
        NotImplemented(4),
        Refused(5),
        YXDomain(6),
        YXRRSet(7),
        NXRRSet(8),
        NotAuth(9),
        NotZone(10),
        DSOTYPENI(11),

        BADVERS(16),
        BADKEY(17),
        BADTIME(18),
        BADMODE(19),
        BADNAME(20),
        BADALG(21),
        BADTRUNC(22),
        BADCOOKIE(23),
        ;
        public final int code;

        RCode(int code) {
            this.code = code;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DNSPacket packet = (DNSPacket) o;
        return id == packet.id &&
            isResponse == packet.isResponse &&
            aa == packet.aa &&
            tc == packet.tc &&
            rd == packet.rd &&
            ra == packet.ra &&
            opcode == packet.opcode &&
            rcode == packet.rcode &&
            Objects.equals(questions, packet.questions) &&
            Objects.equals(answers, packet.answers) &&
            Objects.equals(nameServers, packet.nameServers) &&
            Objects.equals(additionalResources, packet.additionalResources);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, isResponse, opcode, aa, tc, rd, ra, rcode, questions, answers, nameServers, additionalResources);
    }

    @Override
    public String toString() {
        return "DNSPacket{" +
            "id=" + id +
            ", isResponse=" + isResponse +
            ", opcode=" + opcode +
            ", aa=" + aa +
            ", tc=" + tc +
            ", rd=" + rd +
            ", ra=" + ra +
            ", rcode=" + rcode +
            ", qdcount=" + qdcount +
            ", ancount=" + ancount +
            ", nscount=" + nscount +
            ", arcount=" + arcount +
            ", questions=" + questions +
            ", answers=" + answers +
            ", nameServers=" + nameServers +
            ", additionalResources=" + additionalResources +
            '}';
    }

    public ByteArray toByteArray() {
        return Formatter.format(this);
    }
}
