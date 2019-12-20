package vproxy.dns;

import vfd.DatagramFD;
import vfd.EventSet;
import vfd.FDProvider;
import vproxy.app.Config;
import vproxy.dns.rdata.A;
import vproxy.dns.rdata.AAAA;
import vproxy.selector.Handler;
import vproxy.selector.HandlerContext;
import vproxy.selector.PeriodicEvent;
import vproxy.util.ByteArray;
import vproxy.util.Callback;
import vproxy.util.LogType;
import vproxy.util.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VResolver extends AbstractResolver {
    private static final InetAddress[] LOCALHOST;

    static {
        try {
            LOCALHOST = new InetAddress[]{
                InetAddress.getByName("127.0.0.1"),
                InetAddress.getByName("::1")
            };
        } catch (UnknownHostException e) {
            Logger.shouldNotHappen("retrieving l3addr from 127.0.0.1 and ::1 failed", e);
            throw new RuntimeException(e);
        }
    }

    private static final int reloadConfigFilePeriod = 30_000;
    private static final int dnsReqTimeout = 1_500;
    private static final int maxRetry = 2;

    private final DatagramFD sock;
    private List<InetSocketAddress> nameServers;
    private Map<String, InetAddress> hosts;
    private final Map<Integer, Request> requests = new HashMap<>();
    private int nextId = 0;
    private ByteBuffer buffer = ByteBuffer.allocate(Config.udpMtu);

    public VResolver(String alias, List<InetSocketAddress> initialNameServers, Map<String, InetAddress> initialHosts) throws IOException {
        super(alias);
        this.nameServers = initialNameServers;
        this.hosts = initialHosts;

        loop.getSelectorEventLoop().period(reloadConfigFilePeriod, () -> {
            var nameServers = getNameServers();
            if (!nameServers.isEmpty()) {
                // replace
                this.nameServers = nameServers;
            }
            var hosts = getHosts();
            if (!hosts.isEmpty()) {
                this.hosts = hosts;
            }
        }); // no need to record the periodic event, when the resolver is shutdown, the loop would be shutdown as well
        DatagramFD sock = null;
        try {
            sock = FDProvider.get().openDatagramFD();
            sock.configureBlocking(false);
            sock.bind(new InetSocketAddress(0)); // bind any port
            loop.getSelectorEventLoop().add(sock, EventSet.read(), null, new ResolverHandler());
        } catch (IOException e) {
            try {
                loop.getSelectorEventLoop().close();
            } catch (IOException ignore) {
            }
            if (sock != null) {
                try {
                    sock.close();
                } catch (IOException ignore) {
                }
            }
            throw e;
        }
        this.sock = sock;
    }

    private class Request {
        int retry = 0;
        int nameServerIndex = 0;
        final ByteBuffer byteBufferToSend;
        final int id;
        final String domain;
        final PeriodicEvent timer;
        final Callback<List<InetAddress>, UnknownHostException> cb;

        Request(ByteBuffer byteBufferToSend,
                int id,
                String domain,
                PeriodicEvent timer,
                Callback<List<InetAddress>, UnknownHostException> cb) {
            this.byteBufferToSend = byteBufferToSend;
            this.id = id;
            this.domain = domain;
            this.timer = timer;
            this.cb = cb;
        }

        void release() {
            timer.cancel();
            requests.remove(id);
        }

        void request() {
            assert Logger.lowLevelDebug("request() called on dns request: " + id + " " + domain);
            if (nameServerIndex >= nameServers.size()) {
                // all nameservers in the list are already tried
                // check retry times
                if (retry < maxRetry) {
                    ++retry;
                    nameServerIndex = 0;
                } else {
                    // not found
                    release();
                    cb.failed(new UnknownHostException(domain));
                    return;
                }
            }
            int idx = nameServerIndex++;
            var l4addr = nameServers.get(idx);

            byteBufferToSend.limit(byteBufferToSend.capacity()).position(0);
            try {
                sock.send(byteBufferToSend, l4addr);
            } catch (IOException e) {
                Logger.error(LogType.CONN_ERROR, "send dns question packet to " + l4addr + " failed", e);
            }
        }

        public void response(DNSPacket packet) {
            if (!packet.isResponse) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "the received packet is not a response packet");
                request();
                return;
            }
            if (packet.rcode != DNSPacket.RCode.NoError) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "the remote dns server respond with error: " + packet.rcode);
                request();
                return;
            }
            if (packet.tc) {
                Logger.error(LogType.IMPROPER_USE, "we do not support truncation for now. packet is " + packet);
                request();
                return;
            }
            if (packet.answers.isEmpty()) {
                assert Logger.lowLevelDebug("nothing found, so cannot find the requested domain");
                release();
                cb.failed(new UnknownHostException(domain));
                return;
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
                release();
                cb.failed(new UnknownHostException(domain));
                return;
            }
            cb.succeeded(addresses);
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

    private InetAddress[] listToArray(List<InetAddress> list) {
        InetAddress[] ret = new InetAddress[list.size()];
        return list.toArray(ret);
    }

    private InetAddress[] searchInHosts(String domain) {
        if (domain.equals("localhost")) {
            return LOCALHOST;
        }
        if (hosts.containsKey(domain)) {
            return new InetAddress[]{hosts.get(domain)};
        }
        return null;
    }

    @Override
    protected void getAllByName(String domain, Callback<InetAddress[], UnknownHostException> cb) {
        {
            InetAddress[] result = searchInHosts(domain);
            if (result != null) {
                cb.succeeded(result);
                return;
            }
        }

        List<InetAddress> addresses = new ArrayList<>();
        final int MAX_STEP = 2;
        int[] step = {0};
        class TmpCB extends Callback<List<InetAddress>, UnknownHostException> {
            @Override
            protected void onSucceeded(List<InetAddress> value) {
                addresses.addAll(value);
                ++step[0];
                if (step[0] == MAX_STEP) {
                    // should end the process
                    cb.succeeded(listToArray(addresses));
                }
            }

            @Override
            protected void onFailed(UnknownHostException err) {
                ++step[0];
                if (step[0] == MAX_STEP) {
                    // should end the process
                    if (addresses.isEmpty()) { // no process found address, so raise the exception
                        cb.failed(err);
                    } else {
                        cb.succeeded(listToArray(addresses));
                    }
                }
            }
        }
        getAllByName0(domain, true, new TmpCB());
        getAllByName0(domain, false, new TmpCB());
    }

    private void getAllByName0(String domain, boolean ipv4, Callback<List<InetAddress>, UnknownHostException> cb) {
        DNSPacket reqPacket = new DNSPacket();
        int id = getNextId();
        reqPacket.id = id;
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

        ByteBuffer buf = ByteBuffer.wrap(reqPacket.toByteArray().toJavaArray());
        PeriodicEvent timer = loop.getSelectorEventLoop().period(dnsReqTimeout, () -> {
            Request r = requests.get(id);
            if (r == null) {
                assert Logger.lowLevelDebug("the request is already handled in another event");
                return;
            }
            r.request();
        });
        Request req = new Request(buf, id, domain, timer, cb);
        requests.put(id, req);
        req.request();
    }

    @Override
    public void stop() throws IOException {
        super.stop();
        sock.close();
    }
}
