package io.vproxy.vpacket.dns;

import io.vproxy.vpacket.dns.rdata.RData;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Logger;

import java.util.LinkedList;
import java.util.List;

// rfc1035 impl
// enums: https://www.iana.org/assignments/dns-parameters/dns-parameters.xhtml
public class Formatter {
    private Formatter() {
    }

    public static ByteArray format(DNSPacket packet) {
        fillPacket(packet);
        ByteArray ret = formatHeader(packet);
        for (DNSQuestion q : packet.questions) {
            ret = ret.concat(formatQuestion(q));
        }
        for (DNSResource r : packet.answers) {
            ret = ret.concat(formatResource(r));
        }
        for (DNSResource r : packet.nameServers) {
            ret = ret.concat(formatResource(r));
        }
        for (DNSResource r : packet.additionalResources) {
            ret = ret.concat(formatResource(r));
        }
        return ret;
    }

    public static void fillPacket(DNSPacket packet) {
        packet.qdcount = packet.questions.size();
        packet.ancount = packet.answers.size();
        packet.nscount = packet.nameServers.size();
        packet.arcount = packet.additionalResources.size();
        for (DNSQuestion q : packet.questions) {
            fillQuestion(q);
        }
        for (DNSResource r : packet.answers) {
            fillResource(r);
        }
        for (DNSResource r : packet.nameServers) {
            fillResource(r);
        }
        for (DNSResource r : packet.additionalResources) {
            fillResource(r);
        }
    }

    @SuppressWarnings("unused")
    public static void fillQuestion(DNSQuestion q) {
        // do nothing
    }

    public static void fillResource(DNSResource r) {
        if (r.rdataBytes == null) {
            r.rdataBytes = r.rdata.toByteArray();
        }
        r.rdlen = r.rdataBytes.length();
    }

    public static ByteArray formatHeader(DNSPacket packet) {
        int len = 2 // id
            + 2 // qr+opcode+aa+tc=rd+ra+Z+rcode
            + 2 // qdcount
            + 2 // ancount
            + 2 // nscount
            + 2; // arcount
        ByteArray header = ByteArray.allocate(len);
        byte qr_opcode_aa_tc_rd = 0;
        {
            if (packet.isResponse) qr_opcode_aa_tc_rd |= 0b10000000;
            int opcode = packet.opcode.code;
            opcode = opcode << 3;
            qr_opcode_aa_tc_rd |= opcode;
            if (packet.aa) qr_opcode_aa_tc_rd |= 0b00000100;
            if (packet.tc) qr_opcode_aa_tc_rd |= 0b00000010;
            if (packet.rd) qr_opcode_aa_tc_rd |= 0b00000001;
        }
        byte ra_z_rcode = 0;
        if (packet.ra) ra_z_rcode |= 0b10000000;
        ra_z_rcode |= packet.rcode.code;
        header
            .int16(0, packet.id)
            .set(2, qr_opcode_aa_tc_rd)
            .set(3, ra_z_rcode)
            .int16(4, packet.qdcount)
            .int16(6, packet.ancount)
            .int16(8, packet.nscount)
            .int16(10, packet.arcount)
        ;
        return header;
    }

    public static ByteArray formatDomainName(String domain) {
        if (domain.isEmpty()) {
            return ByteArray.from((byte) 0);
        }
        // add missing trailing dot
        if (!domain.endsWith(".")) {
            domain += ".";
        }
        ByteArray ret = null;
        int start = 0;
        int end;
        while (start < domain.length()) {
            end = domain.indexOf(".", start + 1);
            assert end != -1;
            String sub = domain.substring(start, end);
            start = end + 1;
            byte[] bytes = sub.getBytes();
            ByteArray arr = ByteArray.from((byte) bytes.length);
            if (bytes.length > 0) {
                arr = arr.concat(ByteArray.from(bytes));
            }
            if (ret == null) {
                ret = arr;
            } else {
                ret = ret.concat(arr);
            }
        }
        assert ret != null;
        return ret.concat(ByteArray.from((byte) 0));
    }

    public static ByteArray formatString(String s) {
        byte[] bytes = s.getBytes();
        ByteArray len = ByteArray.from((byte) bytes.length);
        if (bytes.length == 0) {
            return len;
        } else {
            return len.concat(ByteArray.from(bytes));
        }
    }

    public static ByteArray formatQuestion(DNSQuestion q) {
        ByteArray qname = formatDomainName(q.qname);
        ByteArray qtype_qclass = ByteArray.allocate(4);
        qtype_qclass.int16(0, q.qtype.code);
        qtype_qclass.int16(2, q.qclass.code);
        return qname.concat(qtype_qclass);
    }

    public static ByteArray formatResource(DNSResource r) {
        if (r.rawBytes != null) {
            return r.rawBytes;
        }
        ByteArray name = formatDomainName(r.name);
        ByteArray type_class_ttl_rdlen = ByteArray.allocate(10);
        type_class_ttl_rdlen.int16(0, r.type.code);
        type_class_ttl_rdlen.int16(2, r.clazz.code);
        type_class_ttl_rdlen.int32(4, r.ttl);
        type_class_ttl_rdlen.int16(8, r.rdlen);
        ByteArray ret = name.concat(type_class_ttl_rdlen).concat(r.rdataBytes);
        r.rawBytes = ret;
        return ret;
    }

    public static List<DNSPacket> parsePackets(ByteArray input) throws InvalidDNSPacketException {
        List<DNSPacket> packets = new LinkedList<>();
        int totalOffset = 0;
        while (totalOffset < input.length()) {
            ByteArray data = input.sub(totalOffset, input.length() - totalOffset);
            try {
                DNSPacket packet = new DNSPacket();
                int offset = parseHeader(packet, data);
                for (int i = 0; i < packet.qdcount; ++i) {
                    DNSQuestion q = new DNSQuestion();
                    ByteArray sub = data.sub(offset, data.length() - offset);
                    offset += parseQuestion(q, sub, input);
                    packet.questions.add(q);
                }
                for (int i = 0; i < packet.ancount; ++i) {
                    DNSResource r = new DNSResource();
                    ByteArray sub = data.sub(offset, data.length() - offset);
                    offset += parseResource(r, sub, input);
                    packet.answers.add(r);
                }
                for (int i = 0; i < packet.nscount; ++i) {
                    DNSResource r = new DNSResource();
                    ByteArray sub = data.sub(offset, data.length() - offset);
                    offset += parseResource(r, sub, input);
                    packet.nameServers.add(r);
                }
                for (int i = 0; i < packet.arcount; ++i) {
                    DNSResource r = new DNSResource();
                    ByteArray sub = data.sub(offset, data.length() - offset);
                    offset += parseResource(r, sub, input);
                    packet.additionalResources.add(r);
                }
                packets.add(packet);
                totalOffset += offset;
                assert Logger.lowLevelDebug("parsed packet: " + packet);
            } catch (IndexOutOfBoundsException e) {
                throw new InvalidDNSPacketException("not a complete packet", e);
            }
        }
        assert totalOffset == input.length();
        return packets;
    }

    public static DNSPacket.Opcode parseOpcode(int opcode) throws InvalidDNSPacketException {
        DNSPacket.Opcode[] opcodeArr = DNSPacket.Opcode.values();
        for (DNSPacket.Opcode o : opcodeArr) {
            if (o.code == opcode) {
                return o;
            }
        }
        throw new InvalidDNSPacketException("unknown opcode: " + opcode);
    }

    public static DNSPacket.RCode parseRCode(int rcode) throws InvalidDNSPacketException {
        DNSPacket.RCode[] rCodeArr = DNSPacket.RCode.values();
        for (DNSPacket.RCode r : rCodeArr) {
            if (r.code == rcode) {
                return r;
            }
        }
        throw new InvalidDNSPacketException("unknown rcode: " + rcode);
    }

    public static String parseDomainName(ByteArray data, ByteArray rawPacket, int[] offsetHolder) {
        StringBuilder sb = new StringBuilder();
        int len = 0;
        int i = 0;
        for (; ; ++i) {
            byte b = data.get(i);
            if (len == 0) {
                // need to read a length
                if (b == 0) {
                    break;
                } else if ((b & 0b11000000) == 0b11000000) {
                    // is pointer
                    int offset = (b & 0b00111111) << 8;
                    byte b2 = data.get(++i);
                    offset |= (b2 & 0xff);
                    String name = parseDomainName(rawPacket.sub(offset, rawPacket.length() - offset), rawPacket, offsetHolder);
                    sb.append(name);
                    break; // pointer must be the last piece of the domain name
                } else {
                    len = b & 0xff;
                }
            } else {
                sb.append((char) b);
                --len;
                if (len == 0) {
                    sb.append(".");
                }
            }
        }
        String name = sb.toString();
        offsetHolder[0] = i + 1;
        return name;
    }

    public static DNSType parseType(int type, boolean question) throws InvalidDNSPacketException {
        DNSType[] typeArr = DNSType.values();
        for (DNSType t : typeArr) {
            if (t.code == type) {
                if (!question) {
                    if (t.question) {
                        throw new InvalidDNSPacketException("the type " + t + " is only allowed for questions");
                    }
                }
                return t;
            }
        }
        // there are so many types for us to record, so allow unknown classes
        return DNSType.OTHER;
    }

    public static int parseHeader(DNSPacket packet, ByteArray data) throws InvalidDNSPacketException {
        {
            packet.id = data.uint16(0);
        }
        {
            byte qr_opcode_aa_tc_rd = data.get(2);
            if ((qr_opcode_aa_tc_rd & 0b10000000) == 0b10000000) {
                packet.isResponse = true;
            }
            int _i = qr_opcode_aa_tc_rd & 0xff;
            _i = _i >> 3;
            _i = _i & 0x0f;
            packet.opcode = parseOpcode(_i);
            if ((qr_opcode_aa_tc_rd & 0b00000100) == 0b00000100) {
                packet.aa = true;
            }
            if ((qr_opcode_aa_tc_rd & 0b00000010) == 0b00000010) {
                packet.tc = true;
            }
            if ((qr_opcode_aa_tc_rd & 0b00000001) == 0b00000001) {
                packet.rd = true;
            }
        }
        {
            byte ra_z_rcode = data.get(3);
            if ((ra_z_rcode & 0b10000000) == 0b10000000) {
                packet.ra = true;
            }
            int _i = ra_z_rcode & 0x0f;
            packet.rcode = parseRCode(_i);
        }
        {
            packet.qdcount = data.uint16(4);
            packet.ancount = data.uint16(6);
            packet.nscount = data.uint16(8);
            packet.arcount = data.uint16(10);
        }
        return 12;
    }

    private static DNSClass parseClass(int clazz, boolean question) throws InvalidDNSPacketException {
        DNSClass[] classArr = DNSClass.values();
        for (DNSClass c : classArr) {
            if (c.code == clazz) {
                if (!question) {
                    if (c.question) {
                        throw new InvalidDNSPacketException("the class " + c + " is only allowed for questions");
                    }
                }
                return c;
            }
        }
        throw new InvalidDNSPacketException("unknown class: " + clazz);
    }

    public static int parseQuestion(DNSQuestion q, ByteArray data, ByteArray rawPacket) throws InvalidDNSPacketException {
        int[] offsetHolder = {0};
        q.qname = parseDomainName(data, rawPacket, offsetHolder);
        int offset = offsetHolder[0];
        int qtype = data.uint16(offset);
        int qclass = data.uint16(offset + 2);
        q.qtype = parseType(qtype, true);
        q.qclass = parseClass(qclass, true);
        return offset + 4;
    }

    public static int parseResource(DNSResource r, ByteArray data, ByteArray rawPacket) throws InvalidDNSPacketException {
        int[] offsetHolder = {0};
        r.name = parseDomainName(data, rawPacket, offsetHolder);
        int offset = offsetHolder[0];
        int type = data.uint16(offset);
        int clazz = data.uint16(offset + 2);
        int ttl = data.int32(offset + 4);
        int rdlen = data.uint16(offset + 8);
        r.type = parseType(type, false);
        if (r.type == DNSType.OPT) {
            r.clazz = DNSClass.NOT_CLASS; // this field is not class
        } else {
            r.clazz = parseClass(clazz, false);
        }
        r.ttl = ttl;
        r.rdlen = rdlen;
        if (r.rdlen < 0) {
            throw new InvalidDNSPacketException("invalid rdlen: " + r.rdlen);
        }

        offset = offset + 10;
        ByteArray rdataBytes = data.sub(offset, r.rdlen);
        r.rdataBytes = rdataBytes;
        RData rData = RData.newRData(r.type);
        if (rData != null) {
            rData.fromByteArray(rdataBytes, rawPacket);
            r.rdata = rData;
        }
        int len = offset + r.rdlen;
        r.rawBytes = data.sub(0, len);
        return len;
    }
}
