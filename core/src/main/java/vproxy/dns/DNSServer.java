package vproxy.dns;

import vproxy.base.Config;
import vproxy.base.component.elgroup.EventLoopAttach;
import vproxy.base.component.elgroup.EventLoopGroup;
import vproxy.base.component.elgroup.EventLoopGroupAttach;
import vproxy.base.component.elgroup.EventLoopWrapper;
import vproxy.base.connection.Connector;
import vproxy.base.connection.NetEventLoop;
import vproxy.base.connection.Protocol;
import vproxy.base.connection.ServerSock;
import vproxy.base.dns.Formatter;
import vproxy.base.dns.*;
import vproxy.base.dns.rdata.A;
import vproxy.base.dns.rdata.AAAA;
import vproxy.base.dns.rdata.RData;
import vproxy.base.dns.rdata.SRV;
import vproxy.base.processor.Hint;
import vproxy.base.selector.Handler;
import vproxy.base.selector.HandlerContext;
import vproxy.base.util.*;
import vproxy.base.util.exception.AlreadyExistException;
import vproxy.base.util.exception.ClosedException;
import vproxy.base.util.exception.NotFoundException;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.svrgroup.Upstream;
import vproxy.vfd.*;

import java.io.IOException;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.util.*;

public class DNSServer {
    public final String alias;
    public final IPPort bindAddress;
    public final EventLoopGroup eventLoopGroup;
    public final Upstream rrsets;
    public final DNSClient client;
    private Map<String, IP> hosts;
    private final ByteBuffer buffer = Utils.allocateByteBuffer(Config.udpMtu);
    private final ConcurrentHashSet<ResolvingInfoRecorder> resolvingInfoRecorders = new ConcurrentHashSet<>();

    private final ELGAttach elgAttach = new ELGAttach();
    private final ELAttach elAttach = new ELAttach();
    protected NetEventLoop loop = null;
    private DatagramFD sock = null;
    private boolean needToStart = false;
    public int ttl;
    public SecurityGroup securityGroup;

    public DNSServer(String alias, IPPort bindAddress, EventLoopGroup eventLoopGroup, Upstream rrsets, int ttl, SecurityGroup securityGroup) {
        this.alias = alias;
        this.bindAddress = bindAddress;
        this.eventLoopGroup = eventLoopGroup;
        this.rrsets = rrsets;
        this.hosts = Resolver.getHosts();
        this.client = DNSClient.getDefault();
        this.ttl = ttl;
        this.securityGroup = securityGroup;
    }

    class ELGAttach implements EventLoopGroupAttach {
        @Override
        public String id() {
            return "DNSServer:" + alias;
        }

        @Override
        public void onEventLoopAdd() {
            if (needToStart) {
                try {
                    start();
                } catch (IOException e) {
                    Logger.error(LogType.IMPROPER_USE, "start the dns-server " + alias + " failed", e);
                }
            }
        }

        @Override
        public void onClose() {
            stop();
        }
    }

    class ELAttach implements EventLoopAttach {
        @Override
        public String id() {
            return "DNSServer:" + alias;
        }

        @Override
        public void onClose() {
            Logger.error(LogType.IMPROPER_USE, "closing event loop which runs dns-server " + alias + ", the dns-server will be stopped");
            stop();
            // try to start again
            try {
                start();
            } catch (IOException e) {
                Logger.error(LogType.ALERT, "restarting dns server failed, wait for further operation", e);
            }
        }
    }

    public void addResolvingInfoRecorder(ResolvingInfoRecorder r) {
        resolvingInfoRecorders.add(r);
    }

    public void removeResolvingInfoRecorder(ResolvingInfoRecorder r) {
        resolvingInfoRecorders.remove(r);
    }

    private void handleRequest(DNSPacket p, IPPort remote) {
        Map<String, Map<DNSType, List<DNSRecord>>> addresses = new LinkedHashMap<>();
        for (DNSQuestion q : p.questions) {
            String domain = q.qname;
            Map<DNSType, List<DNSRecord>> domainMap = addresses.computeIfAbsent(domain, k -> new LinkedHashMap<>());
            switch (q.qtype) {
                case AAAA:
                case A:
                case SRV:
                    List<DNSRecord> records = domainMap.computeIfAbsent(q.qtype, k -> new ArrayList<>());

                    IP hostResult = hosts.get(domain);
                    if (hostResult != null) {
                        records.add(new DNSRecord(hostResult));
                        break;
                    }

                    if (domain.endsWith(".")) { // remove tailing dot by convention
                        domain = domain.substring(0, domain.length() - 1);
                    }
                    Upstream.ServerGroupHandle gh = rrsets.searchForGroup(Hint.ofHost(domain));
                    if (gh == null) {
                        // not found in user defined rrsets
                        // try some internal queries
                        if (IP.isIpLiteral(domain)) {
                            IP l3addr = IP.from(domain);
                            if ((q.qtype == DNSType.A && l3addr instanceof IPv4)
                                ||
                                (q.qtype == DNSType.AAAA && l3addr instanceof IPv6)
                                ||
                                q.qtype == DNSType.SRV) {
                                records.add(new DNSRecord(l3addr));
                            }
                            continue;
                        } else if (domain.endsWith(".vproxy.local")) {
                            List<DNSRecord> res = runInternal(domain.substring(0, domain.length() - ".vproxy.local".length()), remote);
                            if (res != null && !res.isEmpty()) {
                                records.addAll(res);
                            }
                            // .vproxy.local. should not be requested from outside
                            continue;
                        }

                        // all not found, run recursive lookup

                        // usually one dns request only contain one question
                        // we currently do not consider the request of multiple domains and some of them do not require recursion
                        // keep the logic simple
                        runRecursive(p, remote);
                        return;
                    }
                    if (q.qtype == DNSType.SRV) {
                        var servers = gh.group.getServerHandles();
                        for (var svr : servers) {
                            if (!svr.healthy) {
                                continue;
                            }
                            records.add(new DNSRecord(svr.server.getAddress(), svr.server.getPort(), svr.getWeight(), svr.hostName));
                        }
                    } else {
                        Connector connector;
                        if (q.qtype == DNSType.A) {
                            connector = gh.group.nextIPv4(remote);
                        } else if (q.qtype == DNSType.AAAA) {
                            connector = gh.group.nextIPv6(remote);
                        } else {
                            connector = gh.group.next(remote);
                        }
                        if (connector == null) {
                            assert Logger.lowLevelDebug("no active server for " + domain);
                            continue;
                        }
                        records.add(new DNSRecord(connector.remote));
                    }
                    break;
                default:
                    runRecursive(p, remote);
                    return;
            }
        }
        // it means we can directly respond when reaches here

        // a map of additional A/AAAA records for srv records
        Map<String, List<IP>> additional = new LinkedHashMap<>();

        DNSPacket resp = new DNSPacket();
        resp.id = p.id;
        resp.isResponse = true;
        resp.opcode = DNSPacket.Opcode.QUERY;
        resp.aa = p.aa;
        resp.tc = false;
        resp.rd = p.rd;
        resp.ra = true;
        resp.rcode = DNSPacket.RCode.NoError;
        resp.questions.addAll(p.questions);
        for (Map.Entry<String, Map<DNSType, List<DNSRecord>>> entry : addresses.entrySet()) {
            Map<DNSType, List<DNSRecord>> map = entry.getValue();
            for (Map.Entry<DNSType, List<DNSRecord>> entry2 : map.entrySet()) {
                DNSType type = entry2.getKey();
                for (DNSRecord record : entry2.getValue()) {
                    DNSResource r = new DNSResource();
                    r.name = entry.getKey();
                    r.clazz = DNSClass.IN;
                    if (ttl < 0) {
                        ttl = 0;
                    }
                    r.ttl = ttl;

                    RData rdata;
                    if (type == DNSType.SRV) {
                        SRV srv = new SRV();
                        srv.priority = 0; // this value not used for now
                        srv.port = record.port;
                        srv.weight = record.weight;
                        srv.target =
                            record.name == null
                                ? IP.ipStr(record.target.getAddress())
                                : record.name;
                        if (record.name != null) {
                            additional.computeIfAbsent(record.name, n -> new ArrayList<>()).add(record.target);
                        }
                        rdata = srv;
                    } else if (record.target instanceof IPv4) {
                        A a = new A();
                        a.address = (IPv4) record.target;
                        rdata = a;
                    } else {
                        assert record.target instanceof IPv6;
                        AAAA aaaa = new AAAA();
                        aaaa.address = (IPv6) record.target;
                        rdata = aaaa;
                    }

                    r.type = type;
                    r.rdata = rdata;

                    resp.answers.add(r);
                }
            }
        }
        for (Map.Entry<String, List<IP>> entry : additional.entrySet()) {
            for (IP l3addr : entry.getValue()) {
                DNSResource r = new DNSResource();
                r.name = entry.getKey();
                r.clazz = DNSClass.IN;
                if (ttl < 0) {
                    ttl = 0;
                }
                r.ttl = ttl;

                DNSType type;
                RData rdata;
                if (l3addr instanceof IPv4) {
                    type = DNSType.A;
                    A a = new A();
                    a.address = (IPv4) l3addr;
                    rdata = a;
                } else {
                    assert l3addr instanceof IPv6;
                    type = DNSType.AAAA;
                    AAAA aaaa = new AAAA();
                    aaaa.address = (IPv6) l3addr;
                    rdata = aaaa;
                }

                r.type = type;
                r.rdata = rdata;

                resp.additionalResources.add(r);
            }
        }
        sendPacket(p.id, remote, resp);
    }

    protected IP getLocalAddressFor(IPPort remote) {
        // we may create a new sock to respond to the remote
        {
            try (DatagramFD channel = FDProvider.get().openDatagramFD()) {
                channel.connect(remote);
                IP addr = channel.getLocalAddress().getAddress();
                if (!addr.isAnyLocalAddress()) {
                    return addr;
                }
            } catch (IOException e) {
                Logger.shouldNotHappen("got error when trying to retrieve local address of udp packet from " + remote);
            }
        }

        // try to fetch from interfaces
        Enumeration<NetworkInterface> nics;
        try {
            nics = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            Logger.error(LogType.SYS_ERROR, "cannot get local interfaces", e);
            return null;
        }

        List<InterfaceAddress> candidates = new LinkedList<>();
        while (nics.hasMoreElements()) {
            NetworkInterface nic = nics.nextElement();
            candidates.addAll(nic.getInterfaceAddresses());
        }
        if (candidates.isEmpty()) {
            Logger.error(LogType.SYS_ERROR, "cannot find ip address to return");
            return null;
        }

        // find ip in the same network
        IP result = null;
        for (InterfaceAddress a : candidates) {
            IP addr = IP.from(a.getAddress());
            byte[] rule = addr.getAddress();
            int mask = a.getNetworkPrefixLength();
            byte[] maskBytes = Network.parseMask(mask);
            Network.eraseToNetwork(rule, maskBytes);
            if (Network.maskMatch(remote.getAddress().getAddress(), rule, maskBytes)) {
                result = addr;
                break;
            }
        }
        return result;
    }

    protected List<DNSRecord> runInternal(String domain, IPPort remote) {
        if (domain.equals("who.am.i")) {
            return Collections.singletonList(new DNSRecord(remote.getAddress()));
        } else if (domain.equals("who.are.you")) {
            IP l3addr = getLocalAddressFor(remote);
            if (l3addr != null) {
                return Collections.singletonList(new DNSRecord(getLocalAddressFor(remote)));
            }
        }
        return null;
    }

    protected void runRecursive(DNSPacket p, IPPort remote) {
        client.request(p, new Callback<>() {
            @Override
            protected void onSucceeded(DNSPacket value) {
                sendPacket(p.id, remote, value);
            }

            @Override
            protected void onFailed(IOException err) {
                sendError(p, remote, err);
            }
        });
    }

    protected void sendPacket(int id, IPPort remote, DNSPacket p) {
        p.id = id;
        ByteBuffer buf = ByteBuffer.wrap(p.toByteArray().toJavaArray());
        int len = buf.limit();
        int sent;
        try {
            sent = sock.send(buf, remote);
        } catch (IOException e) {
            Logger.error(LogType.CONN_ERROR, "sending dns response packet to " + remote + " failed", e);
            return;
        }
        if (len != sent) {
            Logger.error(LogType.CONN_ERROR, "sending dns response packet to " + remote + " failed, sent len = " + sent);
            return;
        }
        for (var r : resolvingInfoRecorders) {
            r.incSuccessfulResolvingCount();
        }
    }

    @SuppressWarnings("DuplicatedCode")
    protected void sendError(DNSPacket requestPacket, IPPort remote, IOException err) {
        assert Logger.lowLevelDebug("sending error to " + remote + ": " + err);
        DNSPacket p = new DNSPacket();
        p.id = requestPacket.id;
        p.isResponse = true;
        p.opcode = requestPacket.opcode;
        p.aa = requestPacket.aa;
        p.tc = false;
        p.rd = requestPacket.rd;
        p.ra = true;
        p.rcode = DNSPacket.RCode.ServerFailure;
        sendPacket(p.id, remote, p);
    }

    public void start() throws IOException {
        if (sock == null) { // udp sock not created yet
            // need to check whether it's bond
            if (Config.checkBind) {
                ServerSock.checkBind(bindAddress);
            }
        }
        if (!needToStart) {
            try {
                eventLoopGroup.attachResource(elgAttach);
            } catch (AlreadyExistException e) {
                Logger.shouldNotHappen("adding attachment to event loop group failed", e);
                throw new IOException("adding attachment to event loop group failed, should not happen, it's a bug");
            } catch (ClosedException e) {
                throw new IOException("the event loop group is already closed");
            }
        }

        needToStart = true;
        loop = eventLoopGroup.next();
        if (loop == null) {
            assert Logger.lowLevelDebug("no event loop in the group for now, will start later when loop available");
            return;
        }
        if (sock != null) { // already started
            return;
        }

        try {
            ((EventLoopWrapper) loop).attachResource(elAttach);
        } catch (AlreadyExistException e) {
            Logger.shouldNotHappen("adding attachment to event loop failed", e);
            throw new IOException("adding attachment to event loop failed, should not happen, it's a bug");
        } catch (ClosedException e) {
            throw new IOException("the event loop is already closed");
        }

        Logger.alert("dns server " + alias + " " + bindAddress + " starts on loop " + ((EventLoopWrapper) loop).alias);

        sock = FDProvider.get().openDatagramFD();
        sock.configureBlocking(false);
        if (ServerSock.supportReusePort()) {
            sock.setOption(StandardSocketOptions.SO_REUSEPORT, true);
        }
        sock.bind(bindAddress);
        loop.getSelectorEventLoop().add(sock, EventSet.read(), null, new Handler<>() {
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
                    IPPort remote;
                    try {
                        remote = ctx.getChannel().receive(buffer);
                    } catch (IOException e) {
                        Logger.error(LogType.CONN_ERROR, "reading data from dns sock " + ctx.getChannel() + " failed", e);
                        return;
                    }
                    if (remote == null) {
                        return; // nothing received
                    }
                    if (!securityGroup.allow(Protocol.UDP, remote.getAddress(), remote.getPort())) {
                        assert Logger.lowLevelDebug("remote " + remote + " rejected by security-group " + securityGroup.alias);
                        continue;
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
                        if (p.isResponse) {
                            Logger.error(LogType.INVALID_EXTERNAL_DATA, "received dns packet response from " + remote);
                            continue;
                        }
                        if (p.opcode != DNSPacket.Opcode.QUERY) {
                            runRecursive(p, remote);
                            continue;
                        }
                        handleRequest(p, remote);
                    }
                }
            }

            @Override
            public void writable(HandlerContext<DatagramFD> ctx) {
                // will not fire
            }

            @Override
            public void removed(HandlerContext<DatagramFD> ctx) {
                Logger.alert("the current dns server (" + alias + ") is removed from event loop");
                stop();
                try {
                    start();
                } catch (IOException e) {
                    Logger.error(LogType.IMPROPER_USE, "starting dns-server (" + alias + ") failed");
                }
            }
        });

        // start reloading hosts
        loop.getSelectorEventLoop().period(30_000, () -> hosts = Resolver.getHosts());
    }

    public void stop() {
        if (!needToStart) {
            return;
        }
        needToStart = false;

        try {
            eventLoopGroup.detachResource(elgAttach);
        } catch (NotFoundException ignore) {
        }
        if (loop instanceof EventLoopWrapper) {
            try {
                ((EventLoopWrapper) loop).detachResource(elAttach);
            } catch (NotFoundException ignore) {
            }
        }

        if (sock != null) {
            if (loop != null) {
                try {
                    loop.getSelectorEventLoop().remove(sock);
                } catch (Throwable ignore) {
                }
            }
            try {
                sock.close();
            } catch (IOException ignore) {
            }
        }
        loop = null;
        sock = null;
        assert Logger.lowLevelDebug("dns server " + alias + " stopped");
    }
}
