package io.vproxy.test.cases;

import io.vproxy.base.util.Network;
import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.component.check.HealthCheckConfig;
import io.vproxy.base.component.check.CheckProtocol;
import io.vproxy.base.component.svrgroup.Method;
import io.vproxy.base.component.svrgroup.ServerGroup;
import io.vproxy.vfd.FDProvider;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.vpacket.conntrack.Conntrack;
import io.vproxy.vpacket.dns.*;
import io.vproxy.vpacket.dns.rdata.A;
import io.vproxy.vproxyx.websocks.AgentDNSServer;
import io.vproxy.vproxyx.websocks.ConfigLoader;
import io.vproxy.vproxyx.websocks.ConfigProcessor;
import io.vproxy.vproxyx.websocks.DomainChecker;
import io.vproxy.vproxyx.websocks.ServerList;
import io.vproxy.vproxyx.websocks.SharedData;
import io.vproxy.vproxyx.websocks.relay.DomainBinder;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.LinkedHashMap;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    public void dnsResolveCoalescesCachesAndClearsFailedInflightRequests() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<Throwable> serverError = new AtomicReference<>();
        CountDownLatch firstRequestReceived = new CountDownLatch(1);
        CountDownLatch secondRequestReceived = new CountDownLatch(1);
        CountDownLatch releaseFirstResponse = new CountDownLatch(1);
        CountDownLatch releaseSecondResponse = new CountDownLatch(1);
        try (ServerSocket server = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            Thread httpServer = new Thread(() -> {
                try {
                    while (requestCount.get() < 3) {
                        try (Socket client = server.accept()) {
                            client.setSoTimeout(5_000);
                            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
                            String requestLine;
                            try {
                                requestLine = reader.readLine();
                            } catch (java.net.SocketException resetByHealthCheck) {
                                continue;
                            }
                            // ServerGroup performs a TCP health check on add; ignore
                            // that connection and handle only the HTTP resolver query.
                            if (requestLine == null || !requestLine.startsWith("GET ")) {
                                continue;
                            }
                            while (true) {
                                String line = reader.readLine();
                                if (line == null || line.isEmpty()) break;
                            }
                            requestCount.incrementAndGet();
                            int request = requestCount.get();
                            if (request == 1) {
                                firstRequestReceived.countDown();
                                if (!releaseFirstResponse.await(5, TimeUnit.SECONDS)) {
                                    throw new AssertionError("test did not release the first DNS response");
                                }
                            } else if (request == 2) {
                                secondRequestReceived.countDown();
                                if (!releaseSecondResponse.await(5, TimeUnit.SECONDS)) {
                                    throw new AssertionError("test did not release the failing DNS response");
                                }
                            }
                            byte[] body = (request == 2 ? "{}" : "{\"addresses\":[\"203.0.113.77\"]}")
                                .getBytes(StandardCharsets.UTF_8);
                            OutputStream out = client.getOutputStream();
                            out.write(("HTTP/1.1 200 OK\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
                                .getBytes(StandardCharsets.US_ASCII));
                            out.write(body);
                            out.flush();
                            if (request == 3) return;
                        }
                    }
                } catch (Throwable t) {
                    serverError.set(t);
                    firstRequestReceived.countDown();
                    secondRequestReceived.countDown();
                }
            }, "agent-dns-cache-test-server");
            httpServer.setDaemon(true);
            httpServer.start();

            var eventLoops = new EventLoopGroup("dns-cache-test");
            try {
                eventLoops.add("el0");
                var serverGroup = new ServerGroup("resolver", eventLoops,
                    new HealthCheckConfig(400, 24 * 60 * 60 * 1000, 1, 3, CheckProtocol.tcpDelay), Method.wrr);
                var resolverAddress = new IPPort("127.0.0.1", server.getLocalPort());
                var handle = serverGroup.add("resolver", resolverAddress, 1);
                handle.healthy = true;
                handle.data = new SharedData(new ServerList.Server(false, false, false, false, false,
                    "127.0.0.1", server.getLocalPort()), java.util.Map.of(), null, null);

                var resolves = new LinkedHashMap<String, List<DomainChecker>>();
                resolves.put("resolver", List.of(new DomainChecker() {
                    @Override
                    public boolean needProxy(String domain, int port) {
                        return domain.toLowerCase(java.util.Locale.ROOT).endsWith(".test");
                    }

                    @Override
                    public String serialize() {
                        return "example.test";
                    }
                }));
                var config = new ConfigProcessor(new ConfigLoader(), eventLoops, eventLoops) {
                    @Override
                    public java.util.Map<String, ServerGroup> getServers() {
                        return java.util.Map.of("resolver", serverGroup);
                    }

                    @Override
                    public LinkedHashMap<String, List<DomainChecker>> getProxyResolves() {
                        return resolves;
                    }
                };
                var dnsIP = IP.from("100.64.0.53");
                var dns = new AgentDNSServer("dns-cache-test", new IPPort(dnsIP, 53), eventLoops,
                    config, null, null, false, FDProvider.get().getProvided(), dnsIP);

                CountDownLatch callbacksDone = new CountDownLatch(2);
                AtomicReference<IP> firstResult = new AtomicReference<>();
                AtomicReference<IP> secondResult = new AtomicReference<>();
                AtomicReference<Throwable> resolveError = new AtomicReference<>();
                Callback<IP, UnknownHostException> first = new Callback<>() {
                    @Override protected void onSucceeded(IP value) { firstResult.set(value); callbacksDone.countDown(); }
                    @Override protected void onFailed(UnknownHostException err) { resolveError.set(err); callbacksDone.countDown(); }
                };
                Callback<IP, UnknownHostException> second = new Callback<>() {
                    @Override protected void onSucceeded(IP value) { secondResult.set(value); callbacksDone.countDown(); }
                    @Override protected void onFailed(UnknownHostException err) { resolveError.set(err); callbacksDone.countDown(); }
                };
                dns.resolve("Example.Test.", first);
                assertTrue("HTTP resolver request should start", firstRequestReceived.await(5, TimeUnit.SECONDS));
                assertNull(serverError.get());
                dns.resolve("example.test", second);
                releaseFirstResponse.countDown();
                assertTrue("both coalesced callbacks should complete", callbacksDone.await(5, TimeUnit.SECONDS));
                assertNull(resolveError.get());
                assertEquals(IP.from("203.0.113.77"), firstResult.get());
                assertEquals(firstResult.get(), secondResult.get());
                assertEquals(1, requestCount.get());

                AtomicReference<IP> cachedResult = new AtomicReference<>();
                dns.resolve("EXAMPLE.TEST.", new Callback<>() {
                    @Override protected void onSucceeded(IP value) { cachedResult.set(value); }
                    @Override protected void onFailed(UnknownHostException err) { resolveError.set(err); }
                });
                assertNull(resolveError.get());
                assertEquals(firstResult.get(), cachedResult.get());
                assertEquals("normalized cache hit must not issue another HTTP request", 1, requestCount.get());

                CountDownLatch failedCallbacks = new CountDownLatch(2);
                AtomicInteger failedCount = new AtomicInteger();
                AtomicReference<Throwable> requestFailure = new AtomicReference<>();
                Callback<IP, UnknownHostException> failedRequest1 = new Callback<>() {
                    @Override protected void onSucceeded(IP value) { failedCallbacks.countDown(); }
                    @Override protected void onFailed(UnknownHostException err) { failedCount.incrementAndGet(); requestFailure.set(err); failedCallbacks.countDown(); }
                };
                Callback<IP, UnknownHostException> failedRequest2 = new Callback<>() {
                    @Override protected void onSucceeded(IP value) { failedCallbacks.countDown(); }
                    @Override protected void onFailed(UnknownHostException err) { failedCount.incrementAndGet(); requestFailure.set(err); failedCallbacks.countDown(); }
                };
                dns.resolve("retry.test", failedRequest1);
                assertTrue("second uncached domain should start a request", secondRequestReceived.await(5, TimeUnit.SECONDS));
                assertNull(serverError.get());
                dns.resolve("RETRY.TEST.", failedRequest2);
                releaseSecondResponse.countDown();
                assertTrue("both failed-request callbacks should complete", failedCallbacks.await(5, TimeUnit.SECONDS));
                assertEquals("failed query should notify both coalesced waiters", 2, failedCount.get());
                assertNotNull(requestFailure.get());
                assertEquals(2, requestCount.get());

                AtomicReference<IP> retryResult = new AtomicReference<>();
                AtomicReference<Throwable> retryError = new AtomicReference<>();
                CountDownLatch retryDone = new CountDownLatch(1);
                dns.resolve("retry.test", new Callback<>() {
                    @Override protected void onSucceeded(IP value) { retryResult.set(value); retryDone.countDown(); }
                    @Override protected void onFailed(UnknownHostException err) { retryError.set(err); retryDone.countDown(); }
                });
                assertTrue("failed in-flight state should be cleared for retry", retryDone.await(5, TimeUnit.SECONDS));
                assertNull(retryError.get());
                assertEquals(IP.from("203.0.113.77"), retryResult.get());
                assertEquals(3, requestCount.get());

                AtomicReference<IP> retryCacheHit = new AtomicReference<>();
                dns.resolve("RETRY.TEST.", new Callback<>() {
                    @Override protected void onSucceeded(IP value) { retryCacheHit.set(value); }
                    @Override protected void onFailed(UnknownHostException err) { retryError.set(err); }
                });
                assertNull(retryError.get());
                assertEquals(retryResult.get(), retryCacheHit.get());
                assertEquals("cached retry must not issue another HTTP request", 3, requestCount.get());
                httpServer.join(5_000);
                assertFalse("local HTTP server should finish", httpServer.isAlive());
                assertNull(serverError.get());
            } finally {
                releaseFirstResponse.countDown();
                releaseSecondResponse.countDown();
                eventLoops.close();
            }
        }
    }
}
