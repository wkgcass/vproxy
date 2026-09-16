package io.vproxy.test.cases;

import io.vproxy.base.util.Network;
import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.vfd.FDProvider;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.conntrack.Conntrack;
import io.vproxy.vpacket.dns.*;
import io.vproxy.vpacket.dns.rdata.A;
import io.vproxy.vproxyx.websocks.AgentDNSServer;
import io.vproxy.vproxyx.websocks.ConfigLoader;
import io.vproxy.vproxyx.websocks.ConfigProcessor;
import io.vproxy.vproxyx.websocks.DomainChecker;
import io.vproxy.vproxyx.websocks.relay.DomainBinder;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.LinkedHashMap;

import static org.junit.Assert.*;

public class TestDirectRelayAllocation {
    @Test
    public void noBinderResponse() throws Exception {
        var loader = new ConfigLoader();
        var domains = new LinkedHashMap<String, List<DomainChecker>>();
        domains.put("test", List.of(new DomainChecker() {
            public boolean needProxy(String domain, int port) { return true; }
            public String serialize() { return ".test"; }
        }));
        var group = new EventLoopGroup("dns-test");
        try {
            var config = new ConfigProcessor(loader, group, group) {
                @Override
                public boolean isDirectRelay() { return true; }
                @Override
                public int getDirectRelayIpBondTimeout() { return 0; }
                @Override
                public LinkedHashMap<String, List<DomainChecker>> getDomains() { return domains; }
            };
            var dnsIP = IP.from("100.64.0.53");
            var binder = new DomainBinder(null, Network.from("100.64.0.0/24"), Set.of(dnsIP));
            class CapturingDNS extends AgentDNSServer {
                DNSPacket response;
                CapturingDNS(boolean relayHttpHttps) {
                    super("dns-test", new IPPort(dnsIP, 53), group, config, binder, null,
                        relayHttpHttps, FDProvider.get().getProvided(), dnsIP);
                }
                @Override
                protected void sendPacket(int id, IPPort remote, DNSPacket packet) {
                    response = packet;
                }
                void query(DNSType type) {
                    var request = new DNSPacket();
                    request.id = 123;
                    request.opcode = DNSPacket.Opcode.QUERY;
                    request.rcode = DNSPacket.RCode.NoError;
                    request.rd = true;
                    var question = new DNSQuestion();
                    question.qname = "example.test.";
                    question.qclass = DNSClass.IN;
                    question.qtype = type;
                    request.questions.add(question);
                    runRecursive(request, new IPPort("100.64.0.1", 12345));
                }
            }
            // no relay http/https servers on the agent (e.g. tun mode):
            // no-binder queries get an empty NoError answer
            var dns = new CapturingDNS(false);
            dns.query(DNSType.AAAA);
            assertEquals(DNSPacket.RCode.NoError, dns.response.rcode);
            assertTrue(dns.response.answers.isEmpty());
            assertEquals(DNSType.AAAA, dns.response.questions.get(0).qtype);
            dns.query(DNSType.A);
            var answer = (A) dns.response.answers.get(0).rdata;
            assertNotEquals(dnsIP, answer.address);
            assertEquals("example.test", binder.getDomain(answer.address));

            // relay http/https servers running (e.g. tproxy mode): fall back to
            // the self ip; here tunSelfIp is set only to make the expectation deterministic
            var dns2 = new CapturingDNS(true);
            dns2.query(DNSType.AAAA);
            assertEquals(DNSPacket.RCode.NoError, dns2.response.rcode);
            assertEquals(1, dns2.response.answers.size());
            assertEquals(dnsIP, ((A) dns2.response.answers.get(0).rdata).address);
        } finally {
            group.close();
        }
    }

    @Test
    public void reservedAddressesAndExhaustion() {
        for (String cidr : new String[]{"100.64.0.0/29", "fd00::/125"}) {
            boolean v6 = cidr.contains(":");
            Set<IP> reserved = Set.of(IP.from(v6 ? "fd00::1" : "100.64.0.1"),
                IP.from(v6 ? "fd00::3" : "100.64.0.3"));
            var binder = new DomainBinder(null, Network.from(cidr), reserved);
            var allocated = new HashSet<IP>();
            for (int i = 0; i < 4; ++i) {
                IP ip = binder.assignForDomain("domain-" + i, 0);
                assertNotNull(ip);
                assertFalse(reserved.contains(ip));
                assertTrue(allocated.add(ip));
                assertEquals("domain-" + i, binder.getDomain(ip));
                assertEquals(ip, binder.assignForDomain("domain-" + i, 0));
            }
            assertNull(binder.assignForDomain("exhausted", 0));
            assertNull(binder.getDomain(null));
            assertNull(binder.assignForDomain("exhausted", 0));
        }
        assertNull(new DomainBinder(null, Network.from("100.64.0.0/31")).assignForDomain("empty", 0));
    }

    @Test
    public void anyPortListeningMatchesWithoutAffectingPortMatching() {
        var ct = new Conntrack(null);
        var any4 = ct.listenTcp(new IPPort("0.0.0.0", 0), e -> {});
        var any6 = ct.listenTcp(new IPPort("::", 0), e -> {});
        var dst4 = new IPPort("100.64.0.99", 443);
        var dst6 = new IPPort("fd00::99", 443);
        // a bound any-port listener matches any dst ip:port (of the same family)
        assertSame(any4, ct.lookupTcpListen(dst4));
        assertSame(any6, ct.lookupTcpListen(dst6));
        // but is ignored when probing for an exact/wildcard-ip listener
        assertNull(ct.lookupTcpListenWithoutAnyPort(dst4));
        // wildcard-ip with the same port takes precedence over any-port
        var port = ct.listenTcp(new IPPort("0.0.0.0", 443), e -> {});
        assertSame(port, ct.lookupTcpListen(dst4));
        assertSame(port, ct.lookupTcpListenWithoutAnyPort(dst4));
        // exact ip:port takes precedence over everything
        var exact = ct.listenTcp(dst4, e -> {});
        assertSame(exact, ct.lookupTcpListen(dst4));
        assertSame(any6, ct.lookupTcpListen(dst6));
    }
}
