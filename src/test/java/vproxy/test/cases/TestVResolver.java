package vproxy.test.cases;

import org.junit.Test;
import vproxy.dns.*;
import vproxy.dns.rdata.*;
import vproxy.util.BlockCallback;
import vproxy.util.ByteArray;

import java.net.*;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class TestVResolver {
    @Test
    public void packet() throws Exception {
        DNSPacket packet = new DNSPacket();
        packet.id = 0xabcd;
        packet.isResponse = true;
        packet.opcode = DNSPacket.Opcode.QUERY;
        packet.aa = true;
        packet.tc = false;
        packet.rd = true;
        packet.ra = true;
        packet.rcode = DNSPacket.RCode.NoError;
        packet.questions.add(getQuestion());
        packet.answers.add(getAResource());
        packet.answers.add(getAAAAResource());
        packet.nameServers.add(getCNAMEResource());
        packet.additionalResources.add(getTXTResource());

        ByteArray bytes = packet.toByteArray();

        List<DNSPacket> packets = Formatter.parsePackets(bytes);
        assertEquals(1, packets.size());
        DNSPacket parsed = packets.get(0);

        assertEquals(packet, parsed);
        assertEquals(packet.toString(), parsed.toString());
    }

    private DNSQuestion getQuestion() {
        DNSQuestion q = new DNSQuestion();
        q.qname = "www.example.com.";
        q.qtype = DNSType.ANY;
        q.qclass = DNSClass.ANY;
        return q;
    }

    private DNSResource getResource(String name, RData data) {
        DNSResource r = new DNSResource();
        r.name = name;
        r.type = data.type();
        r.clazz = DNSClass.IN;
        r.ttl = 600;
        r.rdata = data;
        return r;
    }

    private DNSResource getAResource() throws Exception {
        A a = new A();
        a.address = (Inet4Address) InetAddress.getByAddress(new byte[]{1, 2, 3, 4});
        return getResource("www.example.com.", a);
    }

    private DNSResource getAAAAResource() throws Exception {
        AAAA aaaa = new AAAA();
        aaaa.address = (Inet6Address) InetAddress.getByName("ABCD:EF01:2345:6789:ABCD:EF01:2345:6789");
        return getResource("www.example.com.", aaaa);
    }

    private DNSResource getCNAMEResource() {
        CNAME cname = new CNAME();
        cname.cname = "dns.server.com.";
        return getResource("my.dns.com.", cname);
    }

    private DNSResource getTXTResource() {
        TXT txt = new TXT();
        txt.texts.add("abcdefghijklmn");
        txt.texts.add("hello world");
        return getResource("some.text.com.", txt);
    }

    @Test
    public void resolve() throws Exception {
        Resolver resolver = new VResolver("my", Collections.singletonList(
            new InetSocketAddress(InetAddress.getByAddress(new byte[]{8, 8, 8, 8}), 53)), Collections.emptyMap());
        resolver.start();
        BlockCallback<InetAddress, UnknownHostException> cb = new BlockCallback<>();
        resolver.resolve("github.com", cb);
        InetAddress addr = cb.block();
        assertTrue(addr instanceof Inet4Address);

        cb = new BlockCallback<>();
        resolver.resolve("ipv6.taobao.com", false, true, cb);
        addr = cb.block();
        assertTrue(addr instanceof Inet6Address);
    }
}
