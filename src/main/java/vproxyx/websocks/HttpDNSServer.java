package vproxyx.websocks;

import vclient.HttpClient;
import vclient.impl.Http1ClientImpl;
import vjson.JSON;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.svrgroup.ServerGroup;
import vproxy.component.svrgroup.Upstream;
import vproxy.dns.*;
import vproxy.dns.rdata.A;
import vproxy.dns.rdata.AAAA;
import vproxy.util.Callback;
import vproxy.util.LogType;
import vproxy.util.Logger;
import vproxy.util.Utils;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HttpDNSServer extends DNSServer {
    private final Map<String, ServerGroup> serverGroups;
    private final Map<String, List<DomainChecker>> resolves;
    public boolean directRelay = false;

    public HttpDNSServer(String alias, InetSocketAddress bindAddress, EventLoopGroup eventLoopGroup, ConfigProcessor config) {
        super(alias, bindAddress, eventLoopGroup, new Upstream("not-used"), 0);
        this.serverGroups = config.getServers();
        this.resolves = config.getResolves();
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
            for (Map.Entry<String, List<DomainChecker>> entry : resolves.entrySet()) {
                var servers = entry.getKey();
                var ls = entry.getValue();
                for (DomainChecker chk : ls) {
                    if (chk.needProxy(domain, 0)) {
                        if (directRelay) {
                            Logger.alert("[DNS] resolve to local ip for " + domain);
                            respondWithSelfIp(p, domain, remote);
                        } else {
                            Logger.alert("[DNS] dispatch resolving query for " + domain + " via " + servers);
                            requestAndResponse(p, serverGroups.get(servers), domain, remote);
                        }
                        return;
                    }
                }
            }
        }
        Logger.alert("[DNS] directly resolve for " + domain);
        super.runRecursive(p, remote);
    }

    public void resolve(String domain, Callback<InetAddress, UnknownHostException> cb) {
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

            Logger.alert("resolve: " + domain + " -> " + ip);

            cb.succeeded(Utils.l3addr(ipBytes));
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
        Enumeration<NetworkInterface> nics;
        try {
            nics = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            Logger.error(LogType.SYS_ERROR, "cannot get local interfaces", e);
            sendError(p, remote, e);
            return;
        }

        List<InterfaceAddress> candidates = new LinkedList<>();
        while (nics.hasMoreElements()) {
            NetworkInterface nic = nics.nextElement();
            candidates.addAll(nic.getInterfaceAddresses());
        }
        if (candidates.isEmpty()) {
            Logger.error(LogType.SYS_ERROR, "cannot find ip address to return");
            sendError(p, remote, new IOException("cannot find ip address to return"));
            return;
        }

        // find ip in the same network
        InetAddress result = null;
        for (InterfaceAddress a : candidates) {
            InetAddress addr = a.getAddress();
            byte[] rule = addr.getAddress();
            int mask = a.getNetworkPrefixLength();
            byte[] maskBytes = Utils.parseMask(mask);
            Utils.eraseToNetwork(rule, maskBytes);
            if (Utils.maskMatch(remote.getAddress().getAddress(), rule, maskBytes)) {
                result = addr;
                break;
            }
        }

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
        final int ttl = 10 * 60; // set to 10 minutes, which would cause no trouble in most cases

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
