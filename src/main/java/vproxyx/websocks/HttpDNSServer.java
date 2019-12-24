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
import vproxy.util.LogType;
import vproxy.util.Logger;
import vproxy.util.Utils;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HttpDNSServer extends DNSServer {
    private final Map<String, ServerGroup> serverGroups;
    private final Map<String, List<DomainChecker>> resolves;

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
                        Logger.alert("dispatch resolving query for " + domain + " via " + servers);
                        requestAndResponse(p, serverGroups.get(servers), domain, remote);
                        return;
                    }
                }
            }
        }
        Logger.alert("directly resolve for " + domain);
        super.runRecursive(p, remote);
    }

    private void requestAndResponse(DNSPacket p, ServerGroup serverGroup, String domain, InetSocketAddress remote) {
        var svr = serverGroup.next(null /*will not use serverGroup*/);
        if (svr == null) {
            sendError(p, remote, new IOException("cannot find server to send the dns request"));
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
                sendError(p, remote, err);
                return;
            }
            if (resp.status() != 200) {
                String msg = "http dns response status is not 200 for resolving " + domain + " via " + svr.remote;
                Logger.error(LogType.INVALID_EXTERNAL_DATA, msg);
                sendError(p, remote, new IOException(msg));
                return;
            }
            int ttl = 10 * 60;
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
                sendError(p, remote, new IOException(msg));
                return;
            }

            Logger.alert("resolve: " + domain + " -> " + ip + ", ttl = " + ttl);

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
            res.ttl = ttl; // set to 10 minutes, which would cause no trouble in most cases
            if (ipBytes.length == 4) {
                res.type = DNSType.A;
                A a = new A();
                a.address = (Inet4Address) Utils.l3addr(ipBytes);
                res.rdata = a;
            } else {
                res.type = DNSType.AAAA;
                AAAA aaaa = new AAAA();
                aaaa.address = (Inet6Address) Utils.l3addr(ipBytes);
                res.rdata = aaaa;
            }
            dnsResp.answers.add(res);
            sendPacket(p.id, remote, dnsResp);
        });
    }
}
