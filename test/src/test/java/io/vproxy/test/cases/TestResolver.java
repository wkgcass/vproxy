package io.vproxy.test.cases;

import io.vproxy.base.dns.*;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.callback.BlockCallback;
import io.vproxy.vfd.FDProvider;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.IPv6;
import io.vproxy.vpacket.dns.*;
import io.vproxy.vpacket.dns.rdata.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestResolver {
    private SelectorEventLoop loop;
    private AbstractResolver resolver;

    @Before
    public void setUp() throws IOException {
        loop = SelectorEventLoop.open();
        resolver = new VResolver("TestResolver" + ((int) (Math.random() * 10000)), FDProvider.get().getProvided());
        resolver.start();
    }

    @After
    public void tearDown() throws IOException {
        resolver.stop();
        loop.close();
    }

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

    private DNSResource getAResource() {
        A a = new A();
        a.address = IP.fromIPv4(new byte[]{1, 2, 3, 4});
        return getResource("www.example.com.", a);
    }

    private DNSResource getAAAAResource() {
        AAAA aaaa = new AAAA();
        aaaa.address = IP.fromIPv6("ABCD:EF01:2345:6789:ABCD:EF01:2345:6789");
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
    public void resolveCustomized() throws Exception {
        BlockCallback<IP, UnknownHostException> cb = new BlockCallback<>();
        resolver.resolve("github.com", cb);
        IP addr = cb.block();
        assertTrue(addr instanceof IPv4);

        cb = new BlockCallback<>();
        resolver.resolve("ipv6.taobao.com", false, true, cb);
        addr = cb.block();
        assertTrue(addr instanceof IPv6);
    }

    @Test
    public void resolve() throws Exception {
        BlockCallback<IP, UnknownHostException> cb = new BlockCallback<>();
        resolver.resolve("localhost", cb);
        IP address = cb.block();
        assertEquals("127.0.0.1", address.formatToIPString());

        List<Cache> cacheList = new LinkedList<>();
        resolver.copyCache(cacheList);
        assertEquals(1, cacheList.size());
        assertEquals("localhost", cacheList.get(0).host);
    }

    @Test
    public void resolveIpv6() throws Exception {
        BlockCallback<IP, UnknownHostException> cb = new BlockCallback<>();
        resolver.resolveV6("localhost", cb);
        IP address = cb.block();
        assertEquals("[::1]", address.formatToIPString());
    }

    @Test
    public void resolveCache() throws Exception {
        resolver.ttl = 2000;
        // let's resolve
        BlockCallback<IP, UnknownHostException> cb = new BlockCallback<>();
        resolver.resolve("localhost", cb);
        IP address = cb.block();

        // check
        assertEquals("127.0.0.1", address.formatToIPString());
        assertEquals("cache count should be 1 because only one resolve", 1, resolver.cacheCount());

        // wait for > 2000 ms for cache to expire
        Thread.sleep(2500);
        assertEquals("there should be no cache now", 0, resolver.cacheCount());

        // resolve again
        cb = new BlockCallback<>();
        resolver.resolve("localhost", cb);
        address = cb.block();
        assertEquals("127.0.0.1", address.formatToIPString());

        assertEquals("now should be 1 cache because resolved again", 1, resolver.cacheCount());

        // resolve again
        cb = new BlockCallback<>();
        resolver.resolve("localhost", cb);
        address = cb.block();
        assertEquals("127.0.0.1", address.formatToIPString());

        assertEquals("should still be 1 cache because already cached", 1, resolver.cacheCount());
    }
}
