package io.vproxy.base.dns;

import io.vproxy.base.Config;
import io.vproxy.base.component.check.CheckProtocol;
import io.vproxy.base.component.check.HealthCheckConfig;
import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.component.svrgroup.Method;
import io.vproxy.base.component.svrgroup.ServerGroup;
import io.vproxy.vpacket.dns.*;
import io.vproxy.vpacket.dns.Formatter;
import io.vproxy.vpacket.dns.rdata.A;
import io.vproxy.vpacket.dns.rdata.AAAA;
import io.vproxy.base.selector.Handler;
import io.vproxy.base.selector.HandlerContext;
import io.vproxy.base.selector.PeriodicEvent;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.base.util.callback.RunOnLoopCallback;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.ClosedException;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.vfd.*;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@SuppressWarnings("rawtypes")
public class DNSClient {
    private static DNSClient DEFAULT_INSTANCE;
    private static EventLoopGroup DEFAULT_EVENT_LOOP_GROUP;

    private final SelectorEventLoop loop;
    private final DatagramFD sock;
    private final DatagramFD sock6;
    private List<IPPort> nameServers;
    private final int dnsReqTimeout;
    private final int maxRetry;

    private final Map<Integer, Request> requests = new HashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(0);
    private final ByteBuffer buffer = Utils.allocateByteBuffer(Config.udpMtu);

    public DNSClient(SelectorEventLoop loop, DatagramFD sock, DatagramFD sock6, int dnsReqTimeout, int maxRetry) throws IOException {
        this(loop, sock, sock6, Collections.emptyList(), dnsReqTimeout, maxRetry);
    }

    public DNSClient(SelectorEventLoop loop, DatagramFD sock, DatagramFD sock6, List<IPPort> initialNameServers, int dnsReqTimeout, int maxRetry) throws IOException {
        this.loop = loop;
        this.sock = sock;
        this.sock6 = sock6;
        this.nameServers = initialNameServers;
        this.dnsReqTimeout = dnsReqTimeout;
        this.maxRetry = maxRetry;

        loop.add(sock, EventSet.read(), null, new ResolverHandler());
        loop.add(sock6, EventSet.read(), null, new ResolverHandler());
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

    public static Tuple<DatagramFD, DatagramFD> createSocketsForDNS() {
        return createSocketsForDNS(FDProvider.get().getProvided());
    }

    public static Tuple<DatagramFD, DatagramFD> createSocketsForDNS(FDs fds) {
        DatagramFD sock = null;
        DatagramFD sock6 = null;
        boolean failed = true;
        try {
            sock = fds.openDatagramFD();
            sock.configureBlocking(false);
            sock.bind(new IPPort(IP.from(new byte[]{0, 0, 0, 0}), 0));

            if (fds.isV4V6DualStack()) {
                sock6 = sock;
            } else {
                sock6 = fds.openDatagramFD();
                sock6.configureBlocking(false);
                sock6.bind(new IPPort(IP.from("::"), 0));
            }

            failed = false;
        } catch (IOException e) {
            Logger.shouldNotHappen("creating sockets for dns client failed", e);
            throw new RuntimeException(e);
        } finally {
            if (failed) {
                if (sock != null) {
                    try {
                        sock.close();
                    } catch (IOException ignore) {
                    }
                }
                if (sock6 != null) {
                    try {
                        sock6.close();
                    } catch (IOException ignore) {
                    }
                }
            }
        }

        return new Tuple<>(sock, sock6);
    }

    public static DNSClient getDefault() {
        if (DEFAULT_INSTANCE != null) {
            return DEFAULT_INSTANCE;
        }
        synchronized (DNSClient.class) {
            if (DEFAULT_INSTANCE != null) {
                return DEFAULT_INSTANCE;
            }

            Tuple<DatagramFD, DatagramFD> sockets = createSocketsForDNS();
            ServerGroup serverGroup;
            try {
                serverGroup = new ServerGroup("default-dns-client", getDefaultEventLoopGroup(), new HealthCheckConfig(
                    1_000, 5_000, 1, 2, CheckProtocol.dns
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
                client = new ServerGroupDNSClient(loop, sockets._1, sockets._2, serverGroup, 2_000, 2);
            } catch (IOException e) {
                Logger.shouldNotHappen("creating default dns client failed", e);
                try {
                    sockets._1.close();
                } catch (IOException ignore) {
                }
                try {
                    sockets._2.close();
                } catch (IOException ignore) {
                }
                throw new RuntimeException(e);
            }
            client.setNameServers(Resolver.blockGetNameServers());
            loop.period(60_000, () -> Resolver.getNameServers(client::setNameServers));
            DEFAULT_INSTANCE = client;
        }
        return DEFAULT_INSTANCE;
    }

    public void setNameServers(List<IPPort> nameServers) {
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
            List<IPPort> nameServers = DNSClient.this.nameServers;

            assert Logger.lowLevelDebug("request() called on dns request: " + id);
            if (nameServerIndex >= nameServers.size()) {
                // all nameservers in the list are already tried
                // check retry times
                if (retry < maxRetry && nameServers.size() > 0) {
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
                if (l4addr.getAddress() instanceof IPv4) {
                    sent = sock.send(byteBufferToSend, l4addr);
                } else {
                    sent = sock6.send(byteBufferToSend, l4addr);
                }
            } catch (IOException e) {
                if (!Utils.isHostIsDown(e) && !Utils.isNoRouteToHost(e)) {
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
                byte[] bytes = Utils.allocateByteArray(read);
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
        int id = nextId.incrementAndGet();
        if (id > 65535) {
            nextId.set(0);
        }
        return id;
    }

    private void getAllByName0(String domain, boolean ipv4, Callback<List<IP>, UnknownHostException> cb) {
        DNSPacket reqPacket = buildDnsRequestCommonPart();
        DNSQuestion q = new DNSQuestion();
        q.qname = domain;
        q.qtype = ipv4 ? DNSType.A : DNSType.AAAA;
        q.qclass = DNSClass.IN;
        reqPacket.questions.add(q);
        assert Logger.lowLevelDebug("is going to send packet " + reqPacket);

        BiFunction<DNSPacket, IOException[], List<IP>> transform = (packet, errHolder) -> {
            if (packet.rcode != DNSPacket.RCode.NoError) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "the remote dns server respond with error: " + packet.rcode + ", req domain is " + domain);
                return null;
            }
            if (packet.tc) {
                Logger.warn(LogType.ALERT, "we do not support truncation for now. packet is " + packet + ", req domain is " + domain);
            }
            if (packet.answers.isEmpty()) {
                assert Logger.lowLevelDebug("nothing found, so cannot find the requested domain");
                errHolder[0] = new UnknownHostException(domain);
                return null;
            }
            List<IP> addresses = new ArrayList<>();
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

    public void resolveIPv4(String domain, Callback<List<IP>, UnknownHostException> cb) {
        getAllByName0(domain, true, new RunOnLoopCallback<>(cb));
    }

    public void resolveIPv6(String domain, Callback<List<IP>, UnknownHostException> cb) {
        getAllByName0(domain, false, new RunOnLoopCallback<>(cb));
    }

    public void request(DNSPacket reqPacket, Callback<DNSPacket, IOException> cb) {
        new Request<>(reqPacket, (resp, holder) -> resp, SocketTimeoutException::new, new RunOnLoopCallback<>(cb));
    }

    public void request(String domain, DNSType qtype, DNSClass qclass, Callback<List<DNSResource>, IOException> cb) {
        var req = buildDnsRequestCommonPart();
        var q = new DNSQuestion();
        q.qname = domain;
        q.qtype = qtype;
        q.qclass = qclass;
        req.questions.add(q);
        request(req, new Callback<>() {
            @Override
            protected void onSucceeded(DNSPacket value) {
                if (value.answers.isEmpty()) {
                    String err = domain;
                    if (qtype != DNSType.A && qtype != DNSType.AAAA && qtype != DNSType.ANY) {
                        err = qtype + ": " + domain;
                    }
                    cb.failed(new UnknownHostException(err));
                    return;
                }
                cb.succeeded(value.answers);
            }

            @Override
            protected void onFailed(IOException err) {
                cb.failed(err);
            }
        });
    }

    private DNSPacket buildDnsRequestCommonPart() {
        DNSPacket req = new DNSPacket();
        req.id = getNextId();
        req.isResponse = false;
        req.opcode = DNSPacket.Opcode.QUERY;
        req.aa = false;
        req.tc = false;
        req.rd = true;
        req.ra = false;
        req.rcode = DNSPacket.RCode.NoError;
        return req;
    }

    public void close() {
        for (Request req : requests.values()) {
            req.timer.cancel();
        }
        try {
            loop.remove(sock);
        } catch (Throwable ignore) {
        }
        try {
            loop.remove(sock6);
        } catch (Throwable ignore) {
        }

        if (this == DEFAULT_INSTANCE) { // should also remove the default instance ref
            try {
                sock.close();
            } catch (IOException ignore) {
            }
            try {
                sock6.close();
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
