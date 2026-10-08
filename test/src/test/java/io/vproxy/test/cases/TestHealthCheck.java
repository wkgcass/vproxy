package io.vproxy.test.cases;

import io.vproxy.base.component.check.CheckProtocol;
import io.vproxy.base.component.check.HealthCheckConfig;
import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.component.svrgroup.Method;
import io.vproxy.base.component.svrgroup.ServerGroup;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.IP;
import io.vproxy.base.util.ByteArray;
import io.vproxy.vpacket.dns.*;
import io.vproxy.vpacket.dns.rdata.A;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestHealthCheck {
    private EventLoopGroup eventLoopGroup;
    private ServerGroup serverGroup;

    @Before
    public void setUp() throws Exception {
        eventLoopGroup = new EventLoopGroup("elg");
        eventLoopGroup.add("el0");
    }

    @After
    public void tearDown() {
        if (serverGroup != null) {
            serverGroup.clear();
        }
        if (eventLoopGroup != null) {
            eventLoopGroup.close();
        }
    }

    @Test
    public void dns() throws Exception {
        // Exercise actual DNS health checks without depending on public DNS
        // reachability or a particular unused port on the test host.
        try (var a = new LocalDnsResponder(); var b = new LocalDnsResponder();
             var silent = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
            serverGroup = new ServerGroup("sg0", eventLoopGroup, new HealthCheckConfig(1000, 1000, 2, 3, CheckProtocol.dns), Method.wrr);
            serverGroup.add("a", new IPPort("127.0.0.1", a.socket.getLocalPort()), 10);
            serverGroup.add("b", new IPPort("127.0.0.1", b.socket.getLocalPort()), 10);
            serverGroup.add("c", new IPPort("127.0.0.1", silent.getLocalPort()), 10);
            assertEquals(3, serverGroup.getServerHandles().size());
            assertEquals(0, serverGroup.getServerHandles().stream().filter(s -> s.healthy).count());
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (serverGroup.getServerHandles().stream().filter(s -> s.healthy).count() != 2
                   && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            a.checkFailure();
            b.checkFailure();
            assertEquals(3, serverGroup.getServerHandles().size());
            assertEquals(2, serverGroup.getServerHandles().stream().filter(s -> s.healthy).count());

            ServerGroup.ServerHandle h = null;
            for (var svr : serverGroup.getServerHandles()) {
                if (!svr.healthy) {
                    h = svr;
                    break;
                }
            }
            assertNotNull(h);
            assertEquals(silent.getLocalPort(), h.server.getPort());
        }
    }

    private static final class LocalDnsResponder implements AutoCloseable {
        final DatagramSocket socket = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread thread;

        LocalDnsResponder() throws Exception {
            thread = new Thread(() -> {
                try {
                    while (!socket.isClosed()) {
                        var datagram = new DatagramPacket(new byte[2048], 2048);
                        socket.receive(datagram);
                        var query = Formatter.parsePackets(ByteArray.from(
                            Arrays.copyOf(datagram.getData(), datagram.getLength()))).get(0);
                        var response = new DNSPacket();
                        response.id = query.id;
                        response.isResponse = true;
                        response.opcode = DNSPacket.Opcode.QUERY;
                        response.rd = query.rd;
                        response.ra = true;
                        response.rcode = DNSPacket.RCode.NoError;
                        response.questions.addAll(query.questions);
                        for (var question : query.questions) {
                            var answer = new DNSResource();
                            answer.name = question.qname;
                            answer.type = DNSType.A;
                            answer.clazz = DNSClass.IN;
                            answer.ttl = 1;
                            var data = new A();
                            data.address = IP.fromIPv4(new byte[]{127, 0, 0, 1});
                            answer.rdata = data;
                            response.answers.add(answer);
                        }
                        byte[] bytes = response.toByteArray().toJavaArray();
                        socket.send(new DatagramPacket(bytes, bytes.length, datagram.getSocketAddress()));
                    }
                } catch (Throwable e) {
                    if (!socket.isClosed()) failure.set(e);
                }
            }, "health-check-dns-fixture");
            thread.setDaemon(true);
            thread.start();
        }

        void checkFailure() throws Exception {
            if (failure.get() != null) throw new AssertionError("DNS fixture failed", failure.get());
        }

        @Override
        public void close() throws Exception {
            socket.close();
            thread.join(1000);
            checkFailure();
        }
    }
}
