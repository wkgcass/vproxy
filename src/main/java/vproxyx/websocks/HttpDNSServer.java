package vproxyx.websocks;

import vclient.HttpClient;
import vclient.impl.Http1ClientImpl;
import vjson.JSON;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.svrgroup.ServerGroup;
import vproxy.component.svrgroup.Upstream;
import vproxy.connection.NetEventLoop;
import vproxy.dns.*;
import vproxy.dns.rdata.A;
import vproxy.dns.rdata.AAAA;
import vproxy.util.Callback;
import vproxy.util.LogType;
import vproxy.util.Logger;
import vproxy.util.Utils;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;

public class HttpDNSServer extends DNSServer {
    private final Map<String, ServerGroup> serverGroups;
    private final Map<String, List<DomainChecker>> resolves;
    private final Map<String, InetAddress> cache = new HashMap<>();
    private final List<DomainChecker> directDomains = new LinkedList<>();
    private final ConfigProcessor config;

    public HttpDNSServer(String alias, InetSocketAddress bindAddress, EventLoopGroup eventLoopGroup, ConfigProcessor config) {
        super(alias, bindAddress, eventLoopGroup, new Upstream("not-used"), 0);
        this.serverGroups = config.getServers();
        this.resolves = config.getProxyResolves();
        boolean directRelay = config.isDirectRelay();
        if (directRelay) {
            this.directDomains.addAll(config.getHTTPSRelayDomains());
            this.directDomains.addAll(config.getProxyHTTPSRelayDomains());
        }
        this.config = config;
    }

    @Override
    public void start() throws IOException {
        super.start();

        clearCacheTask();
    }

    private void clearCacheTask() {
        if (loop == null) {
            return;
        }
        NetEventLoop thisLoop = loop;
        loop.getSelectorEventLoop().delay(5 * 60_000, () -> {
            if (loop == thisLoop) {
                cache.clear();
            }
            clearCacheTask();
        });
    }

    private InetAddress getFromCache(String domain) {
        InetAddress l3addr = cache.get(domain);
        if (l3addr == null) {
            String tmp;
            if (domain.endsWith(".")) {
                tmp = domain.substring(0, domain.length() - 1);
            } else {
                tmp = domain + ".";
            }
            l3addr = cache.get(tmp);
        }
        return l3addr;
    }

    @Override
    protected List<Record> runInternal(String domain, InetSocketAddress remote) {
        var res = super.runInternal(domain, remote);
        if (res != null && !res.isEmpty()) {
            return res;
        }
        List<Record> ret = new ArrayList<>();
        InetAddress localAddr = getLocalAddressFor(remote);
        switch (domain) {
            case "socks5.agent":
                Record r = getSocks5Record(localAddr);
                if (r != null) {
                    ret.add(r);
                }
                break;
            case "httpconnect.agent":
                r = getHttpConnectRecord(localAddr);
                if (r != null) {
                    ret.add(r);
                }
                break;
            case "ss.agent":
                r = getSsRecord(localAddr);
                if (r != null) {
                    ret.add(r);
                }
                break;
            case "pac.agent":
                r = getPacServerRecord(localAddr);
                if (r != null) {
                    ret.add(r);
                }
                break;
            case "dns.agent":
                r = getDNSServerRecord(localAddr);
                if (r != null) {
                    ret.add(r);
                }
                break;
            case "agent":
                r = getSocks5Record(localAddr);
                if (r != null) ret.add(r);
                r = getHttpConnectRecord(localAddr);
                if (r != null) ret.add(r);
                r = getSsRecord(localAddr);
                if (r != null) ret.add(r);
                r = getPacServerRecord(localAddr);
                if (r != null) ret.add(r);
                r = getDNSServerRecord(localAddr);
                if (r != null) ret.add(r);
                break;
        }
        return ret;
    }

    private Record getSocks5Record(InetAddress localAddr) {
        if (config.getSocks5ListenPort() == 0) {
            return null;
        }
        return new Record(localAddr, config.getSocks5ListenPort(), "socks5.agent.vproxy.local");
    }

    private Record getHttpConnectRecord(InetAddress localAddr) {
        if (config.getHttpConnectListenPort() == 0) {
            return null;
        }
        return new Record(localAddr, config.getHttpConnectListenPort(), "httpconnect.agent.vproxy.local");
    }

    private Record getSsRecord(InetAddress localAddr) {
        if (config.getSsListenPort() == 0) {
            return null;
        }
        return new Record(localAddr, config.getSsListenPort(), "ss.agent.vproxy.local");
    }

    private Record getPacServerRecord(InetAddress localAddr) {
        if (config.getPacServerPort() == 0) {
            return null;
        }
        return new Record(localAddr, config.getPacServerPort(), "pac.agent.vproxy.local");
    }

    private Record getDNSServerRecord(InetAddress localAddr) {
        if (config.getDnsListenPort() == 0) {
            return null;
        }
        return new Record(localAddr, config.getDnsListenPort(), "dns.agent.vproxy.local");
    }

    @Override
    protected void runRecursive(DNSPacket p, InetSocketAddress remote) {
        String domain = null;
        for (DNSQuestion q : p.questions) {
            if (q.qtype == DNSType.ANY || q.qtype == DNSType.A || q.qtype == DNSType.AAAA) {
                if (domain == null) {
                    domain = q.qname;
                } else {
                    if (!domain.equals(q.qname)) {
                        Logger.warn(LogType.INVALID_EXTERNAL_DATA, "currently we do not support to retrieve multiple domains for a single dns request packet: " + p.questions.stream().map(qq -> qq.qname).collect(Collectors.joining()));
                        super.runRecursive(p, remote);
                        return;
                    }
                }
            }
        }
        if (domain != null) {
            if (domain.endsWith(".")) {
                domain = domain.substring(0, domain.length() - 1);
            }
            // check direct domains
            for (DomainChecker chk : directDomains) {
                if (chk.needProxy(domain, 0)) {
                    Logger.alert("[DNS] resolve to local ip for " + domain);
                    respondWithSelfIp(p, domain, remote);
                    return;
                }
            }
            // try cache
            {
                InetAddress l3addr = getFromCache(domain);
                if (l3addr != null) {
                    Logger.alert("[DNS] use cache for " + domain + " -> " + Utils.ipStr(l3addr.getAddress()));
                    respond(p, domain, l3addr, remote);
                    return;
                }
            }
            for (Map.Entry<String, List<DomainChecker>> entry : resolves.entrySet()) {
                var servers = entry.getKey();
                var ls = entry.getValue();
                for (DomainChecker chk : ls) {
                    if (chk.needProxy(domain, 0)) {
                        Logger.alert("[DNS] dispatch resolving query for " + domain + " via " + servers);
                        requestAndResponse(p, serverGroups.get(servers), domain, remote);
                        return;
                    }
                }
            }
        }
        Logger.alert("[DNS] directly resolve for " + domain);
        super.runRecursive(p, remote);
    }

    public void resolve(String domain, Callback<InetAddress, UnknownHostException> cb) {
        // try cache first
        {
            InetAddress l3addr = getFromCache(domain);
            if (l3addr != null) {
                Logger.alert("[DNS] use cache for " + domain + " -> " + Utils.ipStr(l3addr.getAddress()));
                cb.succeeded(l3addr);
                return;
            }
        }
        if (domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        for (Map.Entry<String, List<DomainChecker>> entry : resolves.entrySet()) {
            var servers = entry.getKey();
            var ls = entry.getValue();
            for (DomainChecker chk : ls) {
                if (chk.needProxy(domain, 0)) {
                    Logger.alert("[DNS] dispatch resolving query for " + domain + " via " + servers);
                    requestAndGetResult(serverGroups.get(servers), domain, cb);
                    return;
                }
            }
        }
        // otherwise no need to do resolve proxy
        Resolver.getDefault().resolve(domain, cb);
    }

    private void requestAndGetResult(ServerGroup serverGroup, String domain, Callback<InetAddress, UnknownHostException> cb) {
        var svr = serverGroup.next(null /*will not use serverGroup*/);
        if (svr == null) {
            cb.failed(new UnknownHostException("cannot find server to send the dns request - " + domain));
            return;
        }
        // see ConfigProcessor.java
        SharedData data = (SharedData) svr.getData();
        HttpClient cli = new Http1ClientImpl(svr.remote, eventLoopGroup.next(), 5_000, new HttpClient.Options().setSSLContext(
            data.useSSL ? WebSocksUtils.getSslContext() : null
        ));
        cli.get("/tools/resolve?domain=" + domain).send((err, resp) -> {
            cli.close(); // close the cli on response
            if (err != null) {
                Logger.error(LogType.CONN_ERROR, "request " + svr.remote + " to resolve " + domain + "failed", err);
                cb.failed(new UnknownHostException(Utils.formatErr(err) + " - " + domain));
                return;
            }
            if (resp.status() != 200) {
                String msg = "http dns response status is not 200 for resolving " + domain + " via " + svr.remote;
                Logger.error(LogType.INVALID_EXTERNAL_DATA, msg);
                cb.failed(new UnknownHostException(msg));
                return;
            }
            String ip;
            byte[] ipBytes;
            try {
                JSON.Object ins = (JSON.Object) resp.bodyAsJson();
                JSON.Array arr = ins.getArray("addresses");
                ip = arr.getString(0);
                ipBytes = Utils.parseIpString(ip);
                if (ipBytes.length != 4 && ipBytes.length != 16) {
                    throw new RuntimeException();
                }
            } catch (RuntimeException e) {
                String msg = "http dns response format is not valid for resolving " + domain + " via " + svr.remote;
                Logger.error(LogType.INVALID_EXTERNAL_DATA, msg);
                cb.failed(new UnknownHostException(msg));
                return;
            }

            Logger.alert("[DNS] resolve: " + domain + " -> " + ip);
            InetAddress l3addr = Utils.l3addr(ipBytes);
            cache.put(domain, l3addr);
            cb.succeeded(l3addr);
        });
    }

    private void requestAndResponse(DNSPacket p, ServerGroup serverGroup, String domain, InetSocketAddress remote) {
        requestAndGetResult(serverGroup, domain, new Callback<>() {
            @Override
            protected void onSucceeded(InetAddress value) {
                respond(p, domain, value, remote);
            }

            @Override
            protected void onFailed(UnknownHostException err) {
                sendError(p, remote, err);
            }
        });
    }

    private void respondWithSelfIp(DNSPacket p, String domain, InetSocketAddress remote) {
        InetAddress result = getLocalAddressFor(remote);

        if (result == null) {
            String msg = "cannot find ip address to return for remote " + Utils.l4addrStr(remote);
            Logger.error(LogType.SYS_ERROR, msg);
            sendError(p, remote, new IOException(msg));
        } else {
            Logger.alert("[DMS] choose local ip " + Utils.ipStr(result.getAddress()) + " for remote " + Utils.l4addrStr(remote));
            respond(p, domain, result, remote);
        }
    }

    private void respond(DNSPacket p, String domain, InetAddress result, InetSocketAddress remote) {
        Logger.alert("[DNS] respond " + domain + " -> " + Utils.ipStr(result.getAddress()) + " to " + Utils.l4addrStr(remote));

        DNSPacket dnsResp = new DNSPacket();
        dnsResp.id = p.id;
        dnsResp.isResponse = true;
        dnsResp.opcode = p.opcode;
        dnsResp.aa = p.aa;
        dnsResp.tc = false;
        dnsResp.rd = p.rd;
        dnsResp.ra = true;
        dnsResp.rcode = DNSPacket.RCode.NoError;
        dnsResp.questions.addAll(p.questions);
        DNSResource res = new DNSResource();
        res.name = domain;
        res.clazz = DNSClass.IN;
        res.ttl = ttl;
        if (result instanceof Inet4Address) {
            res.type = DNSType.A;
            A a = new A();
            a.address = (Inet4Address) result;
            res.rdata = a;
        } else {
            res.type = DNSType.AAAA;
            AAAA aaaa = new AAAA();
            aaaa.address = (Inet6Address) result;
            res.rdata = aaaa;
        }
        dnsResp.answers.add(res);
        sendPacket(p.id, remote, dnsResp);
    }
}
