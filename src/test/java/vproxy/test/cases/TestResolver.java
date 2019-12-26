package vproxy.test.cases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import vproxy.dns.*;
import vproxy.dns.rdata.*;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.BlockCallback;
import vproxy.util.ByteArray;
import vproxy.util.Utils;

import java.io.IOException;
import java.net.*;
import java.util.Collections;
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
        resolver = new VResolver("TestResolver" + ((int) (Math.random() * 10000)), Collections.singletonList(
            new InetSocketAddress(Utils.l3addr(8, 8, 8, 8), 53)),
            Collections.emptyMap());
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

    private DNSResource getAResource() throws Exception {
        A a = new A();
        a.address = (Inet4Address) Utils.l3addr(new byte[]{1, 2, 3, 4});
        return getResource("www.example.com.", a);
    }

    private DNSResource getAAAAResource() throws Exception {
        AAAA aaaa = new AAAA();
        aaaa.address = (Inet6Address) Utils.l3addr("ABCD:EF01:2345:6789:ABCD:EF01:2345:6789");
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
        BlockCallback<InetAddress, UnknownHostException> cb = new BlockCallback<>();
        resolver.resolve("github.com", cb);
        InetAddress addr = cb.block();
        assertTrue(addr instanceof Inet4Address);

        cb = new BlockCallback<>();
        resolver.resolve("ipv6.taobao.com", false, true, cb);
        addr = cb.block();
        assertTrue(addr instanceof Inet6Address);
    }

    @Test
    public void resolve() throws Exception {
        BlockCallback<InetAddress, UnknownHostException> cb = new BlockCallback<>();
        resolver.resolve("localhost", cb);
        InetAddress address = cb.block();
        assertEquals("127.0.0.1", Utils.ipStr(address.getAddress()));

        List<Cache> cacheList = new LinkedList<>();
        resolver.copyCache(cacheList);
        assertEquals(1, cacheList.size());
        assertEquals("localhost", cacheList.get(0).host);
    }

    @Test
    public void resolveIpv6() throws Exception {
        BlockCallback<Inet6Address, UnknownHostException> cb = new BlockCallback<>();
        resolver.resolveV6("localhost", cb);
        InetAddress address = cb.block();
        assertEquals("[0000:0000:0000:0000:0000:0000:0000:0001]", Utils.ipStr(address.getAddress()));
    }

    @Test
    public void resolveCache() throws Exception {
        resolver.ttl = 2000;
        // let's resolve
        BlockCallback<InetAddress, UnknownHostException> cb = new BlockCallback<>();
        resolver.resolve("localhost", cb);
        InetAddress address = cb.block();

        // check
        assertEquals("127.0.0.1", Utils.ipStr(address.getAddress()));
        assertEquals("cache count should be 1 because only one resolve", 1, resolver.cacheCount());

        // wait for > 2000 ms for cache to expire
        Thread.sleep(2500);
        assertEquals("there should be no cache now", 0, resolver.cacheCount());

        // resolve again
        cb = new BlockCallback<>();
        resolver.resolve("localhost", cb);
        address = cb.block();
        assertEquals("127.0.0.1", Utils.ipStr(address.getAddress()));

        assertEquals("now should be 1 cache because resolved again", 1, resolver.cacheCount());

        // resolve again
        cb = new BlockCallback<>();
        resolver.resolve("localhost", cb);
        address = cb.block();
        assertEquals("127.0.0.1", Utils.ipStr(address.getAddress()));

        assertEquals("should still be 1 cache because already cached", 1, resolver.cacheCount());
    }
}
