package io.vproxy.vproxyx.websocks;

import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.component.svrgroup.ServerGroup;
import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.dns.*;
import io.vproxy.base.dns.rdata.A;
import io.vproxy.base.dns.rdata.AAAA;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.component.secure.SecurityGroup;
import io.vproxy.component.svrgroup.Upstream;
import io.vproxy.dns.DNSServer;
import io.vproxy.lib.http1.CoroutineHttp1ClientConnection;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.IPv6;
import io.vproxy.vproxyx.websocks.relay.DomainBinder;
import io.vproxy.dep.vjson.JSON;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.stream.Collectors;

public class AgentDNSServer extends DNSServer {
    private final Map<String, ServerGroup> serverGroups;
    private final Map<String, List<DomainChecker>> resolves;
    private final Map<String, IP> cache = new HashMap<>();
    private final List<DomainChecker> selfDomains = new LinkedList<>();
    private final List<DomainChecker> bondDomains = new LinkedList<>();
    private final ConfigProcessor config;
    private final DomainBinder domainBinder;

    public AgentDNSServer(String alias, IPPort bindAddress, EventLoopGroup eventLoopGroup, ConfigProcessor config,
                          // domainBinder is optional, respond managed domains with self ip if null
                          DomainBinder domainBinder) {
        super(alias, bindAddress, eventLoopGroup, new Upstream("not-used"), 0, SecurityGroup.allowAll());
        this.serverGroups = config.getServers();
        this.resolves = config.getProxyResolves();
        boolean directRelay = config.isDirectRelay();
        if (directRelay) {
            this.selfDomains.addAll(config.getHttpsSniErasureDomains());
            for (List<DomainChecker> domains : config.getDomains().values()) {
                if (domainBinder == null) {
                    this.selfDomains.addAll(domains);
                } else {
                    this.bondDomains.addAll(domains);
                }
            }
        }
        this.config = config;
        this.domainBinder = domainBinder;
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

    private IP getFromCache(String domain) {
        IP l3addr = cache.get(domain);
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
    protected List<DNSRecord> runInternal(String domain, IPPort remote) {
        var res = super.runInternal(domain, remote);
        if (res != null && !res.isEmpty()) {
            return res;
        }
        List<DNSRecord> ret = new ArrayList<>();
        IP localAddr = getLocalAddressFor(remote);
        switch (domain) {
            case "socks5.agent":
                DNSRecord r = getSocks5Record(localAddr);
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

    private DNSRecord getSocks5Record(IP localAddr) {
        if (config.getSocks5ListenPort() == 0) {
            return null;
        }
        return new DNSRecord(localAddr, config.getSocks5ListenPort(), "socks5.agent.vproxy.local");
    }

    private DNSRecord getHttpConnectRecord(IP localAddr) {
        if (config.getHttpConnectListenPort() == 0) {
            return null;
        }
        return new DNSRecord(localAddr, config.getHttpConnectListenPort(), "httpconnect.agent.vproxy.local");
    }

    private DNSRecord getSsRecord(IP localAddr) {
        if (config.getSsListenPort() == 0) {
            return null;
        }
        return new DNSRecord(localAddr, config.getSsListenPort(), "ss.agent.vproxy.local");
    }

    private DNSRecord getPacServerRecord(IP localAddr) {
        if (config.getPacServerPort() == 0) {
            return null;
        }
        return new DNSRecord(localAddr, config.getPacServerPort(), "pac.agent.vproxy.local");
    }

    private DNSRecord getDNSServerRecord(IP localAddr) {
        if (config.getDnsListenPort() == 0) {
            return null;
        }
        return new DNSRecord(localAddr, config.getDnsListenPort(), "dns.agent.vproxy.local");
    }

    @Override
    protected void runRecursive(DNSPacket p, IPPort remote) {
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
            // check self domains
            for (DomainChecker chk : selfDomains) {
                if (chk.needProxy(domain, 0)) {
                    Logger.alert("[DNS] resolve to self ip for " + domain);
                    respondWithSelfIp(p, domain, remote);
                    return;
                }
            }
            // check bond domains
            for (DomainChecker chk : bondDomains) {
                if (chk.needProxy(domain, 0)) {
                    Logger.alert("[DNS] resolve to bond ip for " + domain);
                    respondWithBondIp(p, domain, remote);
                    return;
                }
            }
            // try cache
            {
                IP l3addr = getFromCache(domain);
                if (l3addr != null) {
                    Logger.alert("[DNS] use cache for " + domain + " -> " + l3addr.formatToIPString());
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

    public void resolve(String domain, Callback<IP, UnknownHostException> cb) {
        // try cache first
        {
            IP l3addr = getFromCache(domain);
            if (l3addr != null) {
                Logger.alert("[DNS] use cache for " + domain + " -> " + l3addr.formatToIPString());
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

    private void requestAndGetResult(ServerGroup serverGroup, String domain, Callback<IP, UnknownHostException> cb) {
        var svr = serverGroup.next(null /*will not use serverGroup*/);
        if (svr == null) {
            cb.failed(new UnknownHostException("cannot find server to send the dns request - " + domain));
            return;
        }
        // see ConfigProcessor.java
        SharedData data = (SharedData) svr.getData();
        CoroutineHttp1ClientConnection.simpleGet((data.svr.useSSL() ? "https" : "http") + "://"
            + svr.remote.formatToIPPortString() + "/tools/resolve?domain=" + domain).setHandler((bytes, err) -> {
            if (err != null) {
                Logger.error(LogType.CONN_ERROR, "request " + svr.remote + " to resolve " + domain + " failed", err);
                cb.failed(new UnknownHostException(Utils.formatErr(err) + " - " + domain));
                return;
            }
            String ip;
            byte[] ipBytes;
            try {
                var ins = (JSON.Object) JSON.parse(new String(bytes.toJavaArray()));
                JSON.Array arr = ins.getArray("addresses");
                ip = arr.getString(0);
                ipBytes = IP.parseIpString(ip);
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
            IP l3addr = IP.from(ipBytes);
            cache.put(domain, l3addr);
            cb.succeeded(l3addr);
        });
    }

    private void requestAndResponse(DNSPacket p, ServerGroup serverGroup, String domain, IPPort remote) {
        requestAndGetResult(serverGroup, domain, new Callback<>() {
            @Override
            protected void onSucceeded(IP value) {
                respond(p, domain, value, remote);
            }

            @Override
            protected void onFailed(UnknownHostException err) {
                sendError(p, remote, err);
            }
        });
    }

    private void respondWithSelfIp(DNSPacket p, String domain, IPPort remote) {
        IP result = getLocalAddressFor(remote);

        if (result == null) {
            String msg = "cannot find ip address to return for remote " + remote.formatToIPPortString();
            Logger.error(LogType.SYS_ERROR, msg);
            sendError(p, remote, new IOException(msg));
        } else {
            Logger.alert("[DNS] choose local ip " + result.formatToIPString() + " for remote " + remote.formatToIPPortString() + " and domain " + domain);
            respond(p, domain, result, remote);
        }
    }

    private void respondWithBondIp(DNSPacket p, String domain, IPPort remote) {
        IP l3addr = domainBinder.assignForDomain(domain, config.getDirectRelayIpBondTimeout());
        if (l3addr == null) {
            String msg = "[DNS] cannot assign ip for domain " + domain;
            Logger.error(LogType.SYS_ERROR, msg);
            sendError(p, remote, new IOException(msg));
        } else {
            Logger.alert("[DNS] assigned ip " + l3addr.formatToIPString() + " for domain " + domain);
            respond(p, domain, l3addr, remote);
        }
    }

    @SuppressWarnings("DuplicatedCode")
    private void respond(DNSPacket p, String domain, IP result, IPPort remote) {
        Logger.alert("[DNS] respond " + domain + " -> " + result.formatToIPString() + " to " + remote.formatToIPPortString());

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
        if (result instanceof IPv4) {
            res.type = DNSType.A;
            A a = new A();
            a.address = (IPv4) result;
            res.rdata = a;
        } else {
            res.type = DNSType.AAAA;
            AAAA aaaa = new AAAA();
            aaaa.address = (IPv6) result;
            res.rdata = aaaa;
        }
        dnsResp.answers.add(res);
        sendPacket(p.id, remote, dnsResp);
    }
}
