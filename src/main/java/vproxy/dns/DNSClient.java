package vproxy.dns;

import vfd.DatagramFD;
import vfd.EventSet;
import vfd.FDProvider;
import vproxy.app.Config;
import vproxy.component.check.CheckProtocol;
import vproxy.component.check.HealthCheckConfig;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.ClosedException;
import vproxy.component.exception.NotFoundException;
import vproxy.component.svrgroup.Method;
import vproxy.component.svrgroup.ServerGroup;
import vproxy.dns.rdata.A;
import vproxy.dns.rdata.AAAA;
import vproxy.selector.Handler;
import vproxy.selector.HandlerContext;
import vproxy.selector.PeriodicEvent;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class DNSClient {
    private static DNSClient DEFAULT_INSTANCE;
    private static EventLoopGroup DEFAULT_EVENT_LOOP_GROUP;

    private final SelectorEventLoop loop;
    private final DatagramFD sock;
    private List<InetSocketAddress> nameServers;
    private final int dnsReqTimeout;
    private final int maxRetry;

    private final Map<Integer, Request> requests = new HashMap<>();
    private int nextId = 0;
    private ByteBuffer buffer = ByteBuffer.allocate(Config.udpMtu);

    public DNSClient(SelectorEventLoop loop, DatagramFD sock, List<InetSocketAddress> initialNameServers, int dnsReqTimeout, int maxRetry) throws IOException {
        this.loop = loop;
        this.sock = sock;
        this.nameServers = initialNameServers;
        this.dnsReqTimeout = dnsReqTimeout;
        this.maxRetry = maxRetry;

        loop.add(sock, EventSet.read(), null, new ResolverHandler());
    }

    private static EventLoopGroup getDefaultEventLoopGroup() {
        if (DEFAULT_EVENT_LOOP_GROUP != null) {
            return DEFAULT_EVENT_LOOP_GROUP;
        }
        synchronized (DNSClient.class) {
            if (DEFAULT_EVENT_LOOP_GROUP != null) {
                return DEFAULT_EVENT_LOOP_GROUP;
            }
            EventLoopGroup group = new EventLoopGroup("default-dns-client-event-loop-group");
            try {
                group.add("el0");
            } catch (AlreadyExistException | IOException | ClosedException e) {
                Logger.shouldNotHappen("creating event loop group failed", e);
                throw new RuntimeException(e);
            }
            DEFAULT_EVENT_LOOP_GROUP = group;
        }
        return DEFAULT_EVENT_LOOP_GROUP;
    }

    public static DatagramFD getSocketForDNS() {
        DatagramFD sock;
        try {
            sock = FDProvider.get().openDatagramFD();
        } catch (IOException e) {
            Logger.shouldNotHappen("creating default DNSClient sock failed", e);
            throw new RuntimeException(e);
        }
        try {
            sock.configureBlocking(false);
        } catch (IOException e) {
            Logger.shouldNotHappen("configure non-blocking failed", e);
            try {
                sock.close();
            } catch (IOException ignore) {
            }
            throw new RuntimeException(e);
        }
        try {
            sock.bind(new InetSocketAddress(Utils.l3addr(new byte[]{0, 0, 0, 0}), 0));
        } catch (IOException e) {
            Logger.shouldNotHappen("bind sock on random port failed", e);
            try {
                sock.close();
            } catch (IOException ignore) {
            }
            throw new RuntimeException(e);
        }
        return sock;
    }

    public static DNSClient getDefault() {
        if (DEFAULT_INSTANCE != null) {
            return DEFAULT_INSTANCE;
        }
        synchronized (DNSClient.class) {
            DatagramFD sock = getSocketForDNS();
            ServerGroup serverGroup;
            try {
                serverGroup = new ServerGroup("default-dns-client", getDefaultEventLoopGroup(), new HealthCheckConfig(
                    1_000, 5_000, 1, 2, CheckProtocol.domainSystem
                ), Method.wrr);
            } catch (AlreadyExistException | ClosedException e) {
                Logger.shouldNotHappen("creating server-group failed", e);
                throw new RuntimeException(e);
            }
            SelectorEventLoop loop;
            try {
                loop = getDefaultEventLoopGroup().get("el0").getSelectorEventLoop();
            } catch (NotFoundException e) {
                Logger.shouldNotHappen("el0 not found in default elg", e);
                throw new RuntimeException(e);
            }
            DNSClient client;
            try {
                client = new ServerGroupDNSClient(loop, sock, serverGroup, 2_000, 2);
            } catch (IOException e) {
                Logger.shouldNotHappen("creating default dns client failed", e);
                try {
                    sock.close();
                } catch (IOException ignore) {
                }
                throw new RuntimeException(e);
            }
            client.setNameServers(Resolver.getNameServers());
            loop.period(60_000, () -> client.setNameServers(Resolver.getNameServers()));
            DEFAULT_INSTANCE = client;
        }
        return DEFAULT_INSTANCE;
    }

    public void setNameServers(List<InetSocketAddress> nameServers) {
        if (nameServers.isEmpty()) {
            return;
        }
        this.nameServers = nameServers;
    }

    private class Request<RETURN, EXCEPTION extends IOException> {
        int retry = 0;
        int nameServerIndex = 0;
        final ByteBuffer byteBufferToSend;
        final int id;
        final PeriodicEvent timer;
        final BiFunction<DNSPacket, IOException[], RETURN> transform;
        final Supplier<EXCEPTION> retryFailErr;
        final Callback<RETURN, EXCEPTION> cb;

        Request(DNSPacket reqPacket,
                BiFunction<DNSPacket, IOException[], RETURN> transform,
                Supplier<EXCEPTION> retryFailErr,
                Callback<RETURN, EXCEPTION> cb) {
            this.byteBufferToSend = ByteBuffer.wrap(reqPacket.toByteArray().toJavaArray());
            this.id = reqPacket.id;
            this.timer = loop.period(dnsReqTimeout, () -> {
                Request r = requests.get(id);
                if (r == null) {
                    assert Logger.lowLevelDebug("the request is already handled in another event");
                    release();
                    return;
                }
                r.request();
            });
            this.transform = transform;
            this.retryFailErr = retryFailErr;
            this.cb = cb;

            requests.put(id, this);
            request();
        }

        void release() {
            timer.cancel();
            requests.remove(id);
        }

        void request() {
            // retrieve the field to local variable because it might be switched
            List<InetSocketAddress> nameServers = DNSClient.this.nameServers;

            assert Logger.lowLevelDebug("request() called on dns request: " + id);
            if (nameServerIndex >= nameServers.size()) {
                // all nameservers in the list are already tried
                // check retry times
                if (retry < maxRetry) {
                    ++retry;
                    nameServerIndex = 0;
                } else {
                    // still not found
                    release();
                    cb.failed(retryFailErr.get());
                    return;
                }
            }
            int idx = nameServerIndex++;
            var l4addr = nameServers.get(idx);

            byteBufferToSend.limit(byteBufferToSend.capacity()).position(0);
            int len = byteBufferToSend.limit();
            int sent;
            try {
                sent = sock.send(byteBufferToSend, l4addr);
            } catch (IOException e) {
                if (!Utils.isHostIsDown(e)) {
                    Logger.error(LogType.CONN_ERROR, "send dns question packet to " + l4addr + " failed", e);
                }
                return;
            }
            if (len != sent) {
                Logger.error(LogType.CONN_ERROR, "sending dns response packet to " + l4addr + " failed, sent len = " + sent);
            }
        }

        public void response(DNSPacket packet) {
            if (!packet.isResponse) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "the received packet is not a response packet");
                request();
                return;
            }
            IOException[] errHolder = new IOException[]{null};
            RETURN ret = transform.apply(packet, errHolder);
            if (errHolder[0] != null) {
                release();
                //noinspection unchecked
                cb.failed((EXCEPTION) errHolder[0]);
                return;
            }
            if (ret != null) {
                release();
                cb.succeeded(ret);
                return;
            }
            // request again
            request();
        }
    }

    private class ResolverHandler implements Handler<DatagramFD> {
        @Override
        public void accept(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void connected(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void readable(HandlerContext<DatagramFD> ctx) {
            while (true) { // read until no packet available
                buffer.limit(buffer.capacity()).position(0);
                try {
                    ctx.getChannel().receive(buffer);
                    // ignore the remote address
                    // any address would be fine
                } catch (IOException e) {
                    Logger.error(LogType.CONN_ERROR, "reading data from dns sock " + ctx.getChannel() + " failed", e);
                    return;
                }
                int read = buffer.position();
                if (read == 0) {
                    return;
                }
                buffer.flip();
                byte[] bytes = new byte[read];
                buffer.get(bytes);
                ByteArray array = ByteArray.from(bytes);

                List<DNSPacket> packets;
                try {
                    packets = Formatter.parsePackets(array);
                } catch (InvalidDNSPacketException e) {
                    Logger.error(LogType.INVALID_EXTERNAL_DATA, "got malformed dns packet", e);
                    return;
                }
                assert Logger.lowLevelDebug("received dns packets: " + packets);
                for (DNSPacket p : packets) {
                    int id = p.id;
                    Request req = requests.get(id);
                    if (req == null) {
                        assert Logger.lowLevelDebug("packet.id == " + id + " cannot be found in the requests map");
                        return;
                    }
                    req.response(p);
                }
            }
        }

        @Override
        public void writable(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void removed(HandlerContext<DatagramFD> ctx) {
            assert Logger.lowLevelDebug("dns sock " + ctx.getChannel() + " removed from loop");
        }
    }

    private int getNextId() {
        int id = ++nextId;
        if (id > 65535) {
            nextId = 0;
        }
        return id;
    }

    private void getAllByName0(String domain, boolean ipv4, Callback<List<InetAddress>, UnknownHostException> cb) {
        DNSPacket reqPacket = new DNSPacket();
        reqPacket.id = getNextId();
        reqPacket.isResponse = false;
        reqPacket.opcode = DNSPacket.Opcode.QUERY;
        reqPacket.aa = false;
        reqPacket.tc = false;
        reqPacket.rd = true;
        reqPacket.ra = false;
        reqPacket.rcode = DNSPacket.RCode.NoError;
        DNSQuestion q = new DNSQuestion();
        q.qname = domain;
        q.qtype = ipv4 ? DNSType.A : DNSType.AAAA;
        q.qclass = DNSClass.IN;
        reqPacket.questions.add(q);
        assert Logger.lowLevelDebug("is going to send packet " + reqPacket);

        BiFunction<DNSPacket, IOException[], List<InetAddress>> transform = (packet, errHolder) -> {
            if (packet.rcode != DNSPacket.RCode.NoError) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "the remote dns server respond with error: " + packet.rcode);
                return null;
            }
            if (packet.tc) {
                Logger.error(LogType.IMPROPER_USE, "we do not support truncation for now. packet is " + packet);
                return null;
            }
            if (packet.answers.isEmpty()) {
                assert Logger.lowLevelDebug("nothing found, so cannot find the requested domain");
                errHolder[0] = new UnknownHostException(domain);
                return null;
            }
            List<InetAddress> addresses = new ArrayList<>();
            for (DNSResource answer : packet.answers) {
                if (answer.type == DNSType.A) {
                    addresses.add(((A) answer.rdata).address);
                } else if (answer.type == DNSType.AAAA) {
                    addresses.add(((AAAA) answer.rdata).address);
                } else {
                    assert Logger.lowLevelDebug("ignore answer with type " + answer.type);
                }
            }
            if (addresses.isEmpty()) {
                assert Logger.lowLevelDebug("no A or AAAA record found, so cannot find the requested domain");
                errHolder[0] = new UnknownHostException(domain);
                return null;
            }
            return addresses;
        };
        new Request<>(reqPacket, transform, () -> new UnknownHostException(domain), cb);
    }

    public void resolveIPv4(String domain, Callback<List<InetAddress>, UnknownHostException> cb) {
        getAllByName0(domain, true, new RunOnLoopCallback<>(cb));
    }

    public void resolveIPv6(String domain, Callback<List<InetAddress>, UnknownHostException> cb) {
        getAllByName0(domain, false, new RunOnLoopCallback<>(cb));
    }

    public void request(DNSPacket reqPacket, Callback<DNSPacket, IOException> cb) {
        new Request<>(reqPacket, (resp, holder) -> resp, SocketTimeoutException::new, new RunOnLoopCallback<>(cb));
    }

    public void close() {
        for (Request req : requests.values()) {
            req.timer.cancel();
        }
        try {
            loop.remove(sock);
        } catch (Throwable ignore) {
        }

        if (this == DEFAULT_INSTANCE) { // should also remove the default instance ref
            try {
                sock.close();
            } catch (IOException ignore) {
            }
            if (DEFAULT_EVENT_LOOP_GROUP != null) {
                DEFAULT_EVENT_LOOP_GROUP.close();
            }
            DEFAULT_INSTANCE = null;
            DEFAULT_EVENT_LOOP_GROUP = null;
        }
    }
}
