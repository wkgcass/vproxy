package vproxy.component.app;

import vjson.JSON;
import vjson.simple.SimpleArray;
import vjson.util.ArrayBuilder;
import vjson.util.ObjectBuilder;
import vproxy.app.Application;
import vproxy.app.Config;
import vproxy.app.GlobalEvents;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.NotFoundException;
import vproxy.component.exception.XException;
import vproxy.connection.ServerSock;
import vproxy.dns.Cache;
import vproxy.dns.Resolver;
import vproxy.util.*;
import vserver.HttpServer;
import vserver.RoutingContext;
import vserver.RoutingHandler;
import vserver.Tool;
import vserver.server.Http1ServerImpl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class HttpController {
    private static final String apiBase = "/api";
    private static final String apiV1Base = apiBase + "/v1";
    private static final String moduleBase = apiV1Base + "/module";
    private static final String channelBase = apiV1Base + "/channel";
    private static final String stateBase = apiV1Base + "/state";
    private static final String statistics = apiV1Base + "/statistics";
    private static final String watch = apiV1Base + "/watch";

    public final String alias;
    public final InetSocketAddress address;
    private final HttpServer server;

    public HttpController(String alias, InetSocketAddress address) throws IOException {
        this.alias = alias;
        this.address = address;
        var loop = Application.get().controlEventLoop;
        server = new Http1ServerImpl(loop);

        // json
        server.all(apiBase + "/*", Tool.bodyJsonHandler());
        // all
        server.get(moduleBase + "/all", wrapAsync(this::getAll));
        // tcp-lb
        server.get(moduleBase + "/tcp-lb/:tl/detail", wrapAsync(this::getTcpLbDetail));
        server.get(moduleBase + "/tcp-lb/:tl", wrapAsync(this::getTcpLb));
        server.get(moduleBase + "/tcp-lb", wrapAsync(this::listTcpLb));
        server.pst(moduleBase + "/tcp-lb", wrapAsync(this::createTcpLb, new ObjectBuilder()
                .put("name", "alias of the socks5 server")
                .put("address", "the bind address")
                .put("backend", "used as the backend servers")
                .put("protocol", "the protocol used by tcp-lb")
                .put("acceptorLoopGroup", "the acceptor event loop")
                .put("workerLoopGroup", "the worker event loop")
                .put("inBufferSize", 16384)
                .put("outBufferSize", 16384)
                .putArray("listOfCertKey", arr -> arr.add("alias of the cert-key to be used"))
                .put("securityGroup", "alias of the security group, default: (allow-all)")
                .build(),
            "name", "address", "backend"));
        server.put(moduleBase + "/tcp-lb/:tl", wrapAsync(this::updateTcpLb, new ObjectBuilder()
            .put("inBufferSize", 16384)
            .put("outBufferSize", 16384)
            .putArray("listOfCertKey", arr -> arr.add("alias of the cert-key to be used"))
            .put("securityGroup", "alias of the security group")
            .build()));
        server.del(moduleBase + "/tcp-lb/:tl", wrapAsync(this::deleteTcpLb));
        // socks5-server
        server.get(moduleBase + "/socks5-server/:socks5/detail", wrapAsync(this::getSocks5ServerDetail));
        server.get(moduleBase + "/socks5-server/:socks5", wrapAsync(this::getSocks5Server));
        server.get(moduleBase + "/socks5-server", wrapAsync(this::listSocks5Server));
        server.pst(moduleBase + "/socks5-server", wrapAsync(this::createSocks5Server, new ObjectBuilder()
                .put("name", "alias of the socks5 server")
                .put("address", "the bind address")
                .put("backend", "used as backend, the socks5 only supports servers added into this group")
                .put("acceptorLoopGroup", "the acceptor event loop")
                .put("workerLoopGroup", "the worker event loop")
                .put("inBufferSize", 16384)
                .put("outBufferSize", 16384)
                .put("securityGroup", "alias of the security group, default: (allow-all)")
                .put("allowNonBackend", false)
                .build(),
            "name", "address", "backend"));
        server.put(moduleBase + "/socks5-server/:socks5", wrapAsync(this::updateSocks5Server, new ObjectBuilder()
            .put("inBufferSize", 16384)
            .put("outBufferSize", 16384)
            .put("securityGroup", "alias of the security group")
            .put("allowNonBackend", false)
            .build()));
        server.del(moduleBase + "/socks5-server/:socks5", wrapAsync(this::deleteSocks5Server));
        // dns-server
        server.get(moduleBase + "/dns-server/:dns/detail", wrapAsync(this::getDNSServerDetail));
        server.get(moduleBase + "/dns-server/:dns", wrapAsync(this::getDNSServer));
        server.get(moduleBase + "/dns-server", wrapAsync(this::listDNSServer));
        server.pst(moduleBase + "/dns-server", wrapAsync(this::createDNSServer, new ObjectBuilder()
                .put("name", "alias of the dns server")
                .put("address", "the bind address")
                .put("rrsets", "the servers to be resolved")
                .put("eventLoopGroup", "the event loop group to run the dns server")
                .put("ttl", 0)
                .put("securityGroup", "alias of the security group, default: (allow-all)")
                .build(),
            "name", "address", "backend"));
        server.put(moduleBase + "/dns-server/:dns", wrapAsync(this::updateDNSServer, new ObjectBuilder()
            .put("ttl", 0)
            .put("securityGroup", "alias of the security group")
            .build()));
        server.del(moduleBase + "/dns-server/:dns", wrapAsync(this::deleteDNSServer));
        // event-loop
        server.get(moduleBase + "/event-loop-group/:elg/event-loop/:el/detail", wrapAsync(this::getEventLoop));
        server.get(moduleBase + "/event-loop-group/:elg/event-loop/:el", wrapAsync(this::getEventLoop));
        server.get(moduleBase + "/event-loop-group/:elg/event-loop", wrapAsync(this::listEventLoop));
        server.pst(moduleBase + "/event-loop-group/:elg/event-loop", wrapAsync(this::createEventLoop, new ObjectBuilder()
                .put("name", "alias of the event loop")
                .build(),
            "name"));
        server.del(moduleBase + "/event-loop-group/:elg/event-loop/:el", wrapAsync(this::deleteEventLoop));
        // event-loop-group
        server.get(moduleBase + "/event-loop-group/:elg/detail", wrapAsync(this::getEventLoopGroupDetail));
        server.get(moduleBase + "/event-loop-group/:elg", wrapAsync(this::getEventLoopGroup));
        server.get(moduleBase + "/event-loop-group", wrapAsync(this::listEventLoopGroup));
        server.pst(moduleBase + "/event-loop-group", wrapAsync(this::createEventLoopGroup, new ObjectBuilder()
                .put("name", "alias of the event loop group")
                .build(),
            "name"));
        server.del(moduleBase + "/event-loop-group/:elg", wrapAsync(this::deleteEventLoopGroup));
        // server-group in upstream
        server.get(moduleBase + "/upstream/:ups/server-group/:sg/detail", wrapAsync(this::getServerGroupInUpstreamDetail));
        server.get(moduleBase + "/upstream/:ups/server-group/:sg", wrapAsync(this::getServerGroupInUpstream));
        server.get(moduleBase + "/upstream/:ups/server-group", wrapAsync(this::listServerGroupInUpstream));
        server.pst(moduleBase + "/upstream/:ups/server-group", wrapAsync(this::createServerGroupInUpstream, new ObjectBuilder()
                .put("name", "alias of the server group to be added")
                .put("weight", 10)
                .putInst("annotations", new ObjectBuilder().put("key", "value").build())
                .build(),
            "name"));
        server.put(moduleBase + "/upstream/:ups/server-group/:sg", wrapAsync(this::updateServerGroupInUpstream, new ObjectBuilder()
            .put("weight", 10)
            .putInst("annotations", new ObjectBuilder().put("key", "value").build())
            .build()));
        server.del(moduleBase + "/upstream/:ups/server-group/:sg", wrapAsync(this::deleteServerGroupInUpstream));
        // upstream
        server.get(moduleBase + "/upstream/:ups/detail", wrapAsync(this::getUpstreamDetail));
        server.get(moduleBase + "/upstream/:ups", wrapAsync(this::getUpstream));
        server.get(moduleBase + "/upstream", wrapAsync(this::listUpstream));
        server.pst(moduleBase + "/upstream", wrapAsync(this::createUpstream, new ObjectBuilder()
                .put("name", "alias of the upstream")
                .build(),
            "name"));
        server.del(moduleBase + "/upstream/:ups", wrapAsync(this::deleteUpstream));
        // server
        server.get(moduleBase + "/server-group/:sg/server/:svr/detail", wrapAsync(this::getServer));
        server.get(moduleBase + "/server-group/:sg/server/:svr", wrapAsync(this::getServer));
        server.get(moduleBase + "/server-group/:sg/server", wrapAsync(this::listServer));
        server.pst(moduleBase + "/server-group/:sg/server", wrapAsync(this::createServer, new ObjectBuilder()
                .put("name", "alias of the server")
                .put("address", "remote address, host:port or ip:port")
                .put("weight", 10)
                .build(),
            "name", "address"));
        server.put(moduleBase + "/server-group/:sg/server/:svr", wrapAsync(this::updateServer, new ObjectBuilder()
            .put("weight", 10)
            .build()));
        server.del(moduleBase + "/server-group/:sg/server/:svr", wrapAsync(this::deleteServer));
        // server-group
        server.get(moduleBase + "/server-group/:sg/detail", wrapAsync(this::getServerGroupDetail));
        server.get(moduleBase + "/server-group/:sg", wrapAsync(this::getServerGroup));
        server.get(moduleBase + "/server-group", wrapAsync(this::listServerGroup));
        server.pst(moduleBase + "/server-group", wrapAsync(this::createServerGroup, new ObjectBuilder()
                .put("name", "alias of the server-group")
                .put("timeout", 1000)
                .put("period", 5000)
                .put("up", 2)
                .put("down", 3)
                .put("protocol", "the protocol used to do health check")
                .put("method", "load balancing method")
                .putInst("annotations", new ObjectBuilder().put("key", "value").build())
                .put("eventLoopGroup", "choose a event-loop-group for the server group. health check operations will be performed on the event loop group")
                .build(),
            "name", "timeout", "period", "up", "down"));
        server.put(moduleBase + "/server-group/:sg", wrapAsync(this::updateServerGroup, new ObjectBuilder()
            .put("timeout", 1000)
            .put("period", 5000)
            .put("up", 2)
            .put("down", 3)
            .put("protocol", "the protocol used to do health check")
            .put("method", "load balancing method")
            .putInst("annotations", new ObjectBuilder().put("key", "value").build())
            .build()));
        server.del(moduleBase + "/server-group/:sg", wrapAsync(this::deleteServerGroup));
        // security-group-rule
        server.get(moduleBase + "/security-group/:secg/security-group-rule/:secgr/detail", wrapAsync(this::getSecurityGroupRule));
        server.get(moduleBase + "/security-group/:secg/security-group-rule/:secgr", wrapAsync(this::getSecurityGroupRule));
        server.get(moduleBase + "/security-group/:secg/security-group-rule", wrapAsync(this::listSecurityGroupRule));
        server.pst(moduleBase + "/security-group/:secg/security-group-rule", wrapAsync(this::createSecurityGroupRule, new ObjectBuilder()
                .put("name", "alias of the security group rule")
                .put("clientNetwork", "a cidr string for checking client ip")
                .put("protocol", "protocol of the rule")
                .put("serverPortMin", 0)
                .put("serverPortMax", 65536)
                .put("rule", "allow or deny the request")
                .build(),
            "name", "clientNetwork", "protocol", "serverPortMin", "serverPortMax", "rule"));
        server.del(moduleBase + "/security-group/:secg/security-group-rule/:secgr", wrapAsync(this::deleteSecurityGroupRule));
        // security-group
        server.get(moduleBase + "/security-group/:secg/detail", wrapAsync(this::getSecurityGroupDetail));
        server.get(moduleBase + "/security-group/:secg", wrapAsync(this::getSecurityGroup));
        server.get(moduleBase + "/security-group", wrapAsync(this::listSecurityGroup));
        server.pst(moduleBase + "/security-group", wrapAsync(this::createSecurityGroup, new ObjectBuilder()
                .put("name", "alias of the security group")
                .put("defaultRule", "allow or deny access if no match in the rule list")
                .build(),
            "name", "defaultRule"));
        server.put(moduleBase + "/security-group/:secg", wrapAsync(this::updateSecurityGroup, new ObjectBuilder()
            .put("defaultRule", "allow or deny access if no match in the rule list")
            .build()));
        server.del(moduleBase + "/security-group/:secg", wrapAsync(this::deleteSecurityGroup));
        // cert-key
        server.get(moduleBase + "/cert-key/:ck/detail", wrapAsync(this::getCertKeyDetail));
        server.get(moduleBase + "/cert-key/:ck", wrapAsync(this::getCertKey));
        server.get(moduleBase + "/cert-key", wrapAsync(this::listCertKey));
        server.pst(moduleBase + "/cert-key", wrapAsync(this::createCertKey, new ObjectBuilder()
                .put("name", "alias of the cert-key")
                .putArray("certs", arr -> arr.add("path to certificate pem file"))
                .put("key", "path to private key pem file")
                .build(),
            "name", "certs", "key"));
        server.del(moduleBase + "/cert-key/:ck", wrapAsync(this::deleteCertKey));
        // server-sock
        server.get(channelBase + "/event-loop-groups/:elgs/event-loop/:el/server-sock", wrapAsync(this::listServerSocksInEl));
        server.get(channelBase + "/tcp-lb/:tl/server-sock", wrapAsync(this::listServerSocksInTl));
        server.get(channelBase + "/socks5-server/:socks5/server-sock", wrapAsync(this::listServerSocksInSocks5));
        // connection
        server.get(channelBase + "/event-loop-groups/:elgs/event-loop/:el/conn", wrapAsync(this::listConnInEl));
        server.get(channelBase + "/tcp-lb/:tl/conn", wrapAsync(this::listConnInTl));
        server.get(channelBase + "/socks5-server/:socks5/conn", wrapAsync(this::listConnInSocks5));
        server.get(channelBase + "/server-group/:sg/server/:svr/conn", wrapAsync(this::listConnInServer));
        server.del(channelBase + "/event-loop-groups/:elgs/event-loop/:el/conn/:l4addr-act/:l4addr-pas", wrapAsync(this::deleteConnFromEl));
        server.del(channelBase + "/tcp-lb/:tl/conn/:l4addr-act/:l4addr-pas", wrapAsync(this::deleteConnFromTl));
        server.del(channelBase + "/socks5-server/:socks5/conn/:l4addr-act/:l4addr-pas", wrapAsync(this::deleteConnFromSocks5));
        server.del(channelBase + "/server-group/:sg/server/:svr/conn/:l4addr-act/:l4addr-pas", wrapAsync(this::deleteConnFromServer));
        server.del(channelBase + "/event-loop-groups/:elgs/event-loop/:el/conn/:regexp", wrapAsync(this::deleteConnFromElRegexp));
        server.del(channelBase + "/tcp-lb/:tl/conn/:regexp", wrapAsync(this::deleteConnFromTlRegexp));
        server.del(channelBase + "/socks5-server/:socks5/conn/:regexp", wrapAsync(this::deleteConnFromSocks5Regexp));
        server.del(channelBase + "/server-group/:sg/server/:svr/conn/:regexp", wrapAsync(this::deleteConnFromServerRegexp));
        // session
        server.get(channelBase + "/tcp-lb/:tl/session", wrapAsync(this::listSessionInTl));
        server.get(channelBase + "/socks5-server/:socks5/session", wrapAsync(this::listSessionInSocks5));
        server.del(channelBase + "/tcp-lb/:tl/session/:front-act/:front-pas/:back-act/:back-pas", wrapAsync(this::deleteSessionInTl));
        server.del(channelBase + "/socks5-server/:socks5/session/:front-act/:front-pas/:back-act/:back-pas", wrapAsync(this::deleteSessionInSocks5));
        server.del(channelBase + "/tcp-lb/:tl/session/:regexp", wrapAsync(this::deleteSessionInTlRegexp));
        server.del(channelBase + "/socks5-server/:socks5/session/:regexp", wrapAsync(this::deleteSessionInSocks5Regexp));
        // dns-cache
        server.get(stateBase + "/dns-cache", wrapAsync(this::listDnsCache));
        // bytes-in
        server.get(statistics + "/tcp-lb/:tl/server-sock/:l4addr/bytes-in", wrapAsync(this::getBytesInFromL4AddrTl));
        server.get(statistics + "/socks5-server/:socks5/server-sock/:l4addr/bytes-in", wrapAsync(this::getBytesInFromL4AddrSocks5));
        server.get(statistics + "/event-loop-group/:elg/event-loop/:el/conn/:l4addr-act/:l4addr-pas/bytes-in", wrapAsync(this::getBytesInFromConnectionOfEl));
        server.get(statistics + "/tcp-lb/:tl/conn/:l4addr-act/:l4addr-pas/bytes-in", wrapAsync(this::getBytesInFromConnectionOfTl));
        server.get(statistics + "/socks5-server/:socks5/conn/:l4addr-act/:l4addr-pas/bytes-in", wrapAsync(this::getBytesInFromConnectionOfSocks5));
        server.get(statistics + "/server-group/:sg/server/:svr/conn/:l4addr-act/:l4addr-pas/bytes-in", wrapAsync(this::getBytesInFromConnectionOfServer));
        server.get(statistics + "/server-group/:sg/server/:svr/bytes-in", wrapAsync(this::getBytesInFromServer));
        // bytes-out
        server.get(statistics + "/tcp-lb/:tl/server-sock/:l4addr/bytes-out", wrapAsync(this::getBytesOutFromL4AddrTl));
        server.get(statistics + "/socks5-server/:socks5/server-sock/:l4addr/bytes-out", wrapAsync(this::getBytesOutFromL4AddrSocks5));
        server.get(statistics + "/event-loop-group/:elg/event-loop/:el/conn/:l4addr-act/:l4addr-pas/bytes-out", wrapAsync(this::getBytesOutFromConnectionOfEl));
        server.get(statistics + "/tcp-lb/:tl/conn/:l4addr-act/:l4addr-pas/bytes-out", wrapAsync(this::getBytesOutFromConnectionOfTl));
        server.get(statistics + "/socks5-server/:socks5/conn/:l4addr-act/:l4addr-pas/bytes-out", wrapAsync(this::getBytesOutFromConnectionOfSocks5));
        server.get(statistics + "/server-group/:sg/server/:svr/conn/:l4addr-act/:l4addr-pas/bytes-out", wrapAsync(this::getBytesOutFromConnectionOfServer));
        server.get(statistics + "/server-group/:sg/server/:svr/bytes-out", wrapAsync(this::getBytesOutFromServer));
        // accepted-conn-count
        server.get(statistics + "/tcp-lb/:tl/server-sock/:l4addr/accepted-conn", wrapAsync(this::getAcceptedConnFromL4AddrTl));
        server.get(statistics + "/socks5-server/:socks5/server-sock/:l4addr/accepted-conn", wrapAsync(this::getAcceptedConnFromL4AddrSocks5));

        // watch
        server.get(watch + "/server-group/-/server/-/health-check", this::watchHealthCheck);

        // start
        if (Config.checkBind) {
            ServerSock.checkBind(address);
        }
        server.listen(address);
    }

    public void stop() {
        server.close();
    }

    private void getAll(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        cb.succeeded(utils.all());
    }

    private void getTcpLbDetail(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var tl = Application.get().tcpLBHolder.get(rctx.param("tl"));
        cb.succeeded(utils.formatTcpLbDetail(tl));
    }

    private void getTcpLb(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var tl = Application.get().tcpLBHolder.get(rctx.param("tl"));
        cb.succeeded(utils.formatTcpLb(tl));
    }

    private void listTcpLb(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var holder = Application.get().tcpLBHolder;
        var names = holder.names();
        var arr = new ArrayBuilder();
        for (String name : names) {
            var tl = holder.get(name);
            arr.addInst(utils.formatTcpLb(tl));
        }
        cb.succeeded(arr.build());
    }

    private boolean bodyContainsKey(JSON.Object body, String key) {
        if (!body.containsKey(key)) {
            return false;
        }
        return !(body.get(key) instanceof JSON.Null);
    }

    private void createTcpLb(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var body = (JSON.Object) rctx.get(Tool.bodyJson);
        var name = body.getString("name");
        var address = body.getString("address");
        var backend = body.getString("backend");
        var options = new LinkedList<>(Arrays.asList(
            "add", "tcp-lb", name, "address", address, "upstream", backend
        ));
        if (bodyContainsKey(body, "protocol")) {
            options.add("protocol");
            options.add(body.getString("protocol"));
        }
        if (bodyContainsKey(body, "acceptorLoopGroup")) {
            options.add("acceptor-elg");
            options.add(body.getString("acceptorLoopGroup"));
        }
        if (bodyContainsKey(body, "workerLoopGroup")) {
            options.add("event-loop-group");
            options.add(body.getString("workerLoopGroup"));
        }
        if (bodyContainsKey(body, "inBufferSize")) {
            options.add("in-buffer-size");
            options.add("" + body.getInt("inBufferSize"));
        }
        if (bodyContainsKey(body, "outBufferSize")) {
            options.add("out-buffer-size");
            options.add("" + body.getInt("outBufferSize"));
        }
        if (bodyContainsKey(body, "listOfCertKey")) {
            var arr = body.getArray("listOfCertKey");
            if (arr.length() > 0) {
                options.add("cert-key");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < arr.length(); ++i) {
                    if (i != 0) {
                        sb.append(",");
                    }
                    sb.append(arr.getString(i));
                }
                options.add(sb.toString());
            }
        }
        if (bodyContainsKey(body, "securityGroup")) {
            options.add("security-group");
            options.add(body.getString("securityGroup"));
        }
        utils.execute(cb, options);
    }

    private void updateTcpLb(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var options = new LinkedList<>(Arrays.asList(
            "update", "tcp-lb", rctx.param("tl")
        ));
        var body = (JSON.Object) rctx.get(Tool.bodyJson);
        if (bodyContainsKey(body, "inBufferSize")) {
            options.add("in-buffer-size");
            options.add("" + body.getInt("inBufferSize"));
        }
        if (bodyContainsKey(body, "outBufferSize")) {
            options.add("out-buffer-size");
            options.add("" + body.getInt("outBufferSize"));
        }
        if (bodyContainsKey(body, "listOfCertKey")) {
            var arr = body.getArray("listOfCertKey");
            if (arr.length() > 0) {
                options.add("cert-key");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < arr.length(); ++i) {
                    if (i != 0) {
                        sb.append(",");
                    }
                    sb.append(arr.getString(i));
                }
                options.add(sb.toString());
            } else {
                // additional check
                TcpLB tl;
                try {
                    tl = Application.get().tcpLBHolder.get(rctx.param("tl"));
                } catch (NotFoundException e) {
                    cb.failed(e);
                    return;
                }
                if (tl.getCertKeys() != null && tl.getCertKeys().length > 0) {
                    cb.failed(new Err(400, "Cannot configure the tcp-lb to use plain TCP when it's originally using TLS"));
                    return;
                }
            }
        }
        if (bodyContainsKey(body, "securityGroup")) {
            options.add("security-group");
            options.add(body.getString("securityGroup"));
        }
        utils.execute(cb, options);
    }

    private void deleteTcpLb(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb,
            "remove", "tcp-lb", rctx.param("tl"));
    }

    private void getSocks5ServerDetail(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var s = Application.get().socks5ServerHolder.get(rctx.param("socks5"));
        cb.succeeded(utils.formatSocks5ServerDetail(s));
    }

    private void getSocks5Server(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var s = Application.get().socks5ServerHolder.get(rctx.param("socks5"));
        cb.succeeded(utils.formatSocks5Server(s));
    }

    private void listSocks5Server(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var holder = Application.get().socks5ServerHolder;
        var names = holder.names();
        var arr = new ArrayBuilder();
        for (String name : names) {
            var s = holder.get(name);
            arr.addInst(utils.formatSocks5Server(s));
        }
        cb.succeeded(arr.build());
    }

    private void createSocks5Server(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var body = (JSON.Object) rctx.get(Tool.bodyJson);
        var name = body.getString("name");
        var address = body.getString("address");
        var backend = body.getString("backend");
        var options = new LinkedList<>(Arrays.asList(
            "add", "socks5-server", name, "address", address, "upstream", backend
        ));
        if (bodyContainsKey(body, "acceptorLoopGroup")) {
            options.add("acceptor-elg");
            options.add(body.getString("acceptorLoopGroup"));
        }
        if (bodyContainsKey(body, "workerLoopGroup")) {
            options.add("event-loop-group");
            options.add(body.getString("workerLoopGroup"));
        }
        if (bodyContainsKey(body, "inBufferSize")) {
            options.add("in-buffer-size");
            options.add("" + body.getInt("inBufferSize"));
        }
        if (bodyContainsKey(body, "outBufferSize")) {
            options.add("out-buffer-size");
            options.add("" + body.getInt("outBufferSize"));
        }
        if (bodyContainsKey(body, "securityGroup")) {
            options.add("security-group");
            options.add(body.getString("securityGroup"));
        }
        if (bodyContainsKey(body, "allowNonBackend")) {
            boolean allowNonBackend = body.getBool("allowNonBackend");
            if (allowNonBackend) {
                options.add("allow-non-backend");
            } else {
                options.add("deny-non-backend");
            }
        }
        utils.execute(cb, options);
    }

    private void updateSocks5Server(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var options = new LinkedList<>(Arrays.asList(
            "update", "socks5-server", rctx.param("socks5")
        ));
        var body = (JSON.Object) rctx.get(Tool.bodyJson);
        if (bodyContainsKey(body, "inBufferSize")) {
            options.add("in-buffer-size");
            options.add("" + body.getInt("inBufferSize"));
        }
        if (bodyContainsKey(body, "outBufferSize")) {
            options.add("out-buffer-size");
            options.add("" + body.getInt("outBufferSize"));
        }
        if (bodyContainsKey(body, "securityGroup")) {
            options.add("security-group");
            options.add(body.getString("securityGroup"));
        }
        if (bodyContainsKey(body, "allowNonBackend")) {
            boolean allowNonBackend = body.getBool("allowNonBackend");
            if (allowNonBackend) {
                options.add("allow-non-backend");
            } else {
                options.add("deny-non-backend");
            }
        }
        utils.execute(cb, options);
    }

    private void deleteSocks5Server(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb,
            "remove", "socks5-server", rctx.param("socks5"));
    }

    private void getDNSServerDetail(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var dns = Application.get().dnsServerHolder.get(rctx.param("dns"));
        cb.succeeded(utils.formatDNSServerDetail(dns));
    }

    private void getDNSServer(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var dns = Application.get().dnsServerHolder.get(rctx.param("dns"));
        cb.succeeded(utils.formatDNSServer(dns));
    }

    private void listDNSServer(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var holder = Application.get().dnsServerHolder;
        var names = holder.names();
        var arr = new ArrayBuilder();
        for (String name : names) {
            var d = holder.get(name);
            arr.addInst(utils.formatDNSServer(d));
        }
        cb.succeeded(arr.build());
    }

    private void createDNSServer(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var body = (JSON.Object) rctx.get(Tool.bodyJson);
        var name = body.getString("name");
        var address = body.getString("address");
        var rrsets = body.getString("rrsets");
        var options = new LinkedList<>(Arrays.asList(
            "add", "dns-server", name, "address", address, "upstream", rrsets
        ));
        if (bodyContainsKey(body, "eventLoopGroup")) {
            options.add("event-loop-group");
            options.add(body.getString("eventLoopGroup"));
        }
        if (bodyContainsKey(body, "ttl")) {
            options.add("ttl");
            options.add("" + body.getInt("ttl"));
        }
        if (bodyContainsKey(body, "securityGroup")) {
            options.add("security-group");
            options.add(body.getString("securityGroup"));
        }
        utils.execute(cb, options);
    }

    private void updateDNSServer(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var options = new LinkedList<>(Arrays.asList(
            "update", "dns-server", rctx.param("dns")
        ));
        var body = (JSON.Object) rctx.get(Tool.bodyJson);
        if (bodyContainsKey(body, "ttl")) {
            options.add("ttl");
            options.add("" + body.getInt("ttl"));
        }
        if (bodyContainsKey(body, "securityGroup")) {
            options.add("security-group");
            options.add(body.getString("securityGroup"));
        }
        utils.execute(cb, options);
    }

    private void deleteDNSServer(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb,
            "remove", "dns-server", rctx.param("dns"));
    }

    private void getEventLoop(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var el = Application.get().eventLoopGroupHolder.get(rctx.param("elg")).get(rctx.param("el"));
        cb.succeeded(utils.formatEventLoop(el));
    }

    private void listEventLoop(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var elg = Application.get().eventLoopGroupHolder.get(rctx.param("elg"));
        var list = elg.list();
        var arr = new ArrayBuilder();
        list.forEach(el -> arr.addInst(utils.formatEventLoop(el)));
        cb.succeeded(arr.build());
    }

    private void createEventLoop(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var body = (JSON.Object) rctx.get(Tool.bodyJson);
        var name = body.getString("name");
        utils.execute(cb,
            "add", "event-loop", name, "to", "event-loop-group", rctx.param("elg"));
    }

    private void deleteEventLoop(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb,
            "remove", "event-loop", rctx.param("el"), "from", "event-loop-group", rctx.param("elg"));
    }

    private void getEventLoopGroupDetail(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var elg = Application.get().eventLoopGroupHolder.get(rctx.param("elg"));
        cb.succeeded(utils.formatEventLoopGroupDetail(elg));
    }

    private void getEventLoopGroup(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var elg = Application.get().eventLoopGroupHolder.get(rctx.param("elg"));
        cb.succeeded(utils.formatEventLoopGroup(elg));
    }

    private void listEventLoopGroup(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var arr = new ArrayBuilder();
        var holder = Application.get().eventLoopGroupHolder;
        var names = holder.names();
        for (var name : names) {
            EventLoopGroup elg = holder.get(name);
            arr.addInst(utils.formatEventLoopGroup(elg));
        }
        cb.succeeded(arr.build());
    }

    private void createEventLoopGroup(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var body = (JSON.Object) rctx.get(Tool.bodyJson);
        var name = body.getString("name");
        utils.execute(cb,
            "add", "event-loop-group", name);
    }

    private void deleteEventLoopGroup(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb, "remove", "event-loop-group", rctx.param("elg"));
    }

    private void getServerGroupInUpstreamDetail(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var upsName = rctx.param("ups");
        var ups = Application.get().upstreamHolder.get(upsName);
        var sgName = rctx.param("sg");
        var opt = ups.getServerGroupHandles().stream().filter(sg -> sg.alias.equals(sgName)).findAny();
        if (opt.isEmpty()) {
            throw new NotFoundException("server-group in upstream " + upsName, sgName);
        } else {
            cb.succeeded(utils.formatServerGroupInUpstreamDetail(opt.get()));
        }
    }

    private void getServerGroupInUpstream(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var upsName = rctx.param("ups");
        var ups = Application.get().upstreamHolder.get(upsName);
        var sgName = rctx.param("sg");
        var opt = ups.getServerGroupHandles().stream().filter(sg -> sg.alias.equals(sgName)).findAny();
        if (opt.isEmpty()) {
            throw new NotFoundException("server-group in upstream " + upsName, sgName);
        } else {
            cb.succeeded(utils.formatServerGroupInUpstream(opt.get()));
        }
    }

    private void listServerGroupInUpstream(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var ups = Application.get().upstreamHolder.get(rctx.param("ups"));
        var arr = new ArrayBuilder();
        ups.getServerGroupHandles().forEach(sg -> arr.addInst(utils.formatServerGroupInUpstream(sg)));
        cb.succeeded(arr.build());
    }

    private void createServerGroupInUpstream(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var ups = rctx.param("ups");
        var body = (JSON.Object) rctx.get(Tool.bodyJson);
        var sg = body.getString("name");
        List<String> options = new LinkedList<>(Arrays.asList(
            "add", "server-group", sg, "to", "upstream", ups
        ));
        if (bodyContainsKey(body, "weight")) {
            options.add("weight");
            options.add("" + body.getInt("weight"));
        }
        if (bodyContainsKey(body, "annotations")) {
            options.add("annotations");
            options.add(body.getObject("annotations").stringify());
        }
        utils.execute(cb, options);
    }

    private void updateServerGroupInUpstream(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var ups = rctx.param("ups");
        var sg = rctx.param("sg");
        var body = (JSON.Object) rctx.get(Tool.bodyJson);
        List<String> options = new LinkedList<>(Arrays.asList(
            "update", "server-group", sg, "in", "upstream", ups
        ));
        if (bodyContainsKey(body, "weight")) {
            options.add("weight");
            options.add("" + body.getInt("weight"));
        }
        if (bodyContainsKey(body, "annotations")) {
            options.add("annotations");
            options.add(body.getObject("annotations").stringify());
        }
        utils.execute(cb, options);
    }

    private void deleteServerGroupInUpstream(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var ups = rctx.param("ups");
        var sg = rctx.param("sg");
        utils.execute(cb,
            "remove", "server-group", sg, "from", "upstream", ups);
    }

    private void getUpstreamDetail(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var ups = Application.get().upstreamHolder.get(rctx.param("ups"));
        cb.succeeded(utils.formatUpstreamDetail(ups));
    }

    private void getUpstream(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var ups = Application.get().upstreamHolder.get(rctx.param("ups"));
        cb.succeeded(utils.formatUpstream(ups));
    }

    private void listUpstream(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var holder = Application.get().upstreamHolder;
        var names = holder.names();
        var arr = new ArrayBuilder();
        for (var name : names) {
            var ups = holder.get(name);
            arr.addInst(utils.formatUpstream(ups));
        }
        cb.succeeded(arr.build());
    }

    private void createUpstream(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var body = (JSON.Object) rctx.get(Tool.bodyJson);
        utils.execute(cb,
            "add", "upstream", body.getString("name"));
    }

    private void deleteUpstream(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb,
            "remove", "upstream", rctx.param("ups"));
    }

    private void getServer(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var sgName = rctx.param("sg");
        var sg = Application.get().serverGroupHolder.get(sgName);
        var alias = rctx.param("svr");
        var opt = sg.getServerHandles().stream().filter(h -> h.alias.equals(alias)).findAny();
        if (opt.isEmpty()) {
            throw new NotFoundException("server in server-group " + sgName, alias);
        } else {
            cb.succeeded(utils.formatServer(opt.get()));
        }
    }

    private void listServer(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var sg = Application.get().serverGroupHolder.get(rctx.param("sg"));
        var arr = new ArrayBuilder();
        sg.getServerHandles().forEach(h -> arr.addInst(utils.formatServer(h)));
        cb.succeeded(arr.build());
    }

    private void createServer(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var body = (JSON.Object) rctx.get(Tool.bodyJson);
        List<String> options = new LinkedList<>(Arrays.asList(
            "add", "server", body.getString("name"), "to", "server-group", rctx.param("sg"),
            "address", body.getString("address")
        ));
        if (bodyContainsKey(body, "weight")) {
            options.add("weight");
            options.add("" + body.getInt("weight"));
        }
        utils.execute(cb, options);
    }

    private void updateServer(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var body = (JSON.Object) rctx.get(Tool.bodyJson);
        List<String> options = new LinkedList<>();
        options.add("update");
        options.add("server");
        options.add(rctx.param("svr"));
        options.add("in");
        options.add("server-group");
        options.add(rctx.param("sg"));
        if (bodyContainsKey(body, "weight")) {
            options.add("weight");
            options.add("" + body.getInt("weight"));
        }
        utils.execute(cb, options);
    }

    private void deleteServer(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb,
            "remove", "server", rctx.param("svr"), "from", "server-group", rctx.param("sg"));
    }

    private void getServerGroupDetail(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var sg = Application.get().serverGroupHolder.get(rctx.param("sg"));
        cb.succeeded(utils.formatServerGroupDetail(sg));
    }

    private void getServerGroup(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var sg = Application.get().serverGroupHolder.get(rctx.param("sg"));
        cb.succeeded(utils.formatServerGroup(sg));
    }

    private void listServerGroup(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var holder = Application.get().serverGroupHolder;
        var names = holder.names();
        var arr = new ArrayBuilder();
        for (String name : names) {
            arr.addInst(utils.formatServerGroup(holder.get(name)));
        }
        cb.succeeded(arr.build());
    }

    private void createServerGroup(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var body = (JSON.Object) rctx.get(Tool.bodyJson);
        List<String> options = new LinkedList<>();
        options.add("add");
        options.add("server-group");
        options.add(body.getString("name"));
        options.add("timeout");
        options.add("" + body.getInt("timeout"));
        options.add("period");
        options.add("" + body.getInt("period"));
        options.add("up");
        options.add("" + body.getInt("up"));
        options.add("down");
        options.add("" + body.getInt("down"));
        if (bodyContainsKey(body, "protocol")) {
            options.add("protocol");
            options.add(body.getString("protocol"));
        }
        if (bodyContainsKey(body, "method")) {
            options.add("method");
            options.add(body.getString("method"));
        }
        if (bodyContainsKey(body, "eventLoopGroup")) {
            options.add("event-loop-group");
            options.add(body.getString("eventLoopGroup"));
        }
        if (bodyContainsKey(body, "annotations")) {
            options.add("annotations");
            options.add(body.getObject("annotations").stringify());
        }
        utils.execute(cb, options);
    }

    private void updateServerGroup(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var body = (JSON.Object) rctx.get(Tool.bodyJson);
        List<String> options = new LinkedList<>();
        options.add("update");
        options.add("server-group");
        options.add(rctx.param("sg"));

        if (bodyContainsKey(body, "timeout") || bodyContainsKey(body, "period") || bodyContainsKey(body, "up") || bodyContainsKey(body, "down") || bodyContainsKey(body, "protocol")) {
            // health check options should be set all together
            if (!bodyContainsKey(body, "timeout")
                || !bodyContainsKey(body, "period")
                || !bodyContainsKey(body, "up")
                || !bodyContainsKey(body, "down")) {
                cb.failed(new Err(400, "health check options should be set together"));
                return;
            }
            int timeout = body.getInt("timeout");
            int period = body.getInt("period");
            int up = body.getInt("up");
            int down = body.getInt("down");
            options.add("timeout");
            options.add("" + timeout);
            options.add("period");
            options.add("" + period);
            options.add("up");
            options.add("" + up);
            options.add("down");
            options.add("" + down);
            if (bodyContainsKey(body, "protocol")) {
                options.add("protocol");
                options.add(body.getString("protocol"));
            }
        }
        if (bodyContainsKey(body, "method")) {
            options.add("method");
            options.add(body.getString("method"));
        }
        if (bodyContainsKey(body, "annotations")) {
            options.add("annotations");
            options.add(body.getObject("annotations").stringify());
        }

        utils.execute(cb, options);
    }

    private void deleteServerGroup(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb,
            "remove", "server-group", rctx.param("sg"));
    }

    private void getSecurityGroupRule(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var secgName = rctx.param("secg");
        var secg = Application.get().securityGroupHolder.get(secgName);
        var rName = rctx.param("secgr");
        var opt = secg.getRules().stream().filter(r -> r.alias.equals(rName)).findAny();
        if (opt.isEmpty()) {
            throw new NotFoundException("security-group-rule in security-group " + secgName, rName);
        } else {
            cb.succeeded(utils.formatSecurityGroupRule(opt.get()));
        }
    }

    private void listSecurityGroupRule(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var secg = Application.get().securityGroupHolder.get(rctx.param("secg"));
        var rules = secg.getRules();
        var arr = new ArrayBuilder();
        for (var rule : rules) {
            arr.addInst(utils.formatSecurityGroupRule(rule));
        }
        cb.succeeded(arr.build());
    }

    private void createSecurityGroupRule(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var secg = rctx.param("secg");
        var body = (JSON.Object) rctx.get(Tool.bodyJson);
        utils.execute(cb,
            "add", "security-group-rule", body.getString("name"), "to", "security-group", secg,
            "network", body.getString("clientNetwork"),
            "protocol", body.getString("protocol"),
            "port-range", body.getInt("serverPortMin") + "," + body.getInt("serverPortMax"),
            "default", body.getString("rule"));
    }

    private void deleteSecurityGroupRule(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var secgr = rctx.param("secgr");
        var secg = rctx.param("secg");
        utils.execute(cb,
            "remove", "security-group-rule", secgr, "from", "security-group", secg);
    }

    private void getSecurityGroupDetail(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var secg = Application.get().securityGroupHolder.get(rctx.param("secg"));
        cb.succeeded(utils.formatSecurityGroupDetail(secg));
    }

    private void getSecurityGroup(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var secg = Application.get().securityGroupHolder.get(rctx.param("secg"));
        cb.succeeded(utils.formatSecurityGroup(secg));
    }

    private void listSecurityGroup(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var holder = Application.get().securityGroupHolder;
        var names = holder.names();
        var arr = new ArrayBuilder();
        for (var name : names) {
            arr.addInst(utils.formatSecurityGroup(holder.get(name)));
        }
        cb.succeeded(arr.build());
    }

    private void createSecurityGroup(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var body = (JSON.Object) rctx.get(Tool.bodyJson);
        utils.execute(cb,
            "add", "security-group", body.getString("name"), "default", body.getString("defaultRule"));
    }

    private void updateSecurityGroup(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var body = (JSON.Object) rctx.get(Tool.bodyJson);
        var options = new LinkedList<>(Arrays.asList(
            "update", "security-group", rctx.param("secg")
        ));
        if (bodyContainsKey(body, "defaultRule")) {
            options.add("default");
            options.add(body.getString("defaultRule"));
        }
        utils.execute(cb, options);
    }

    private void deleteSecurityGroup(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb,
            "remove", "security-group", rctx.param("secg"));
    }

    private void getCertKeyDetail(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var ck = Application.get().certKeyHolder.get(rctx.param("ck"));
        cb.succeeded(utils.formatCertKeyDetail(ck));
    }

    private void getCertKey(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var ck = Application.get().certKeyHolder.get(rctx.param("ck"));
        cb.succeeded(utils.formatCertKey(ck));
    }

    private void listCertKey(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var holder = Application.get().certKeyHolder;
        var names = holder.names();
        ArrayBuilder arr = new ArrayBuilder();
        for (String name : names) {
            var ck = holder.get(name);
            arr.addInst(utils.formatCertKey(ck));
        }
        cb.succeeded(arr.build());
    }

    private void createCertKey(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var body = (JSON.Object) rctx.get(Tool.bodyJson);
        String name = body.getString("name");
        var certs = body.getArray("certs");
        var key = body.getString("key");
        StringBuilder cert = new StringBuilder();
        for (int i = 0; i < certs.length(); ++i) {
            if (i != 0) {
                cert.append(",");
            }
            cert.append(certs.get(i).toJavaObject());
        }
        utils.execute(cb,
            "add", "cert-key", name, "cert", cert.toString(), "key", key);
    }

    private void deleteCertKey(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb,
            "remove", "cert-key", rctx.param("ck"));
    }

    private void listServerSocksInEl(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var el = utils.getEventLoop(rctx);
        List<ServerSock> servers = new LinkedList<>();
        el.copyServers(servers);
        utils.respondServerSockList(servers, cb);
    }

    private void listServerSocksInTl(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var tl = Application.get().tcpLBHolder.get(rctx.param("tl"));
        utils.respondServerSockListInTl(tl, cb);
    }

    private void listServerSocksInSocks5(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var socks5 = Application.get().socks5ServerHolder.get(rctx.param("socks5"));
        utils.respondServerSockListInTl(socks5, cb);
    }

    private void listConnInEl(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var conns = utils.listConnectionFromEl(rctx);
        utils.respondConnectionList(conns, cb);
    }

    private void listConnInTl(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var tl = Application.get().tcpLBHolder.get(rctx.param("tl"));
        var conns = utils.listConnectionFromTl(tl);
        utils.respondConnectionList(conns, cb);
    }

    private void listConnInSocks5(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var socks5 = Application.get().socks5ServerHolder.get(rctx.param("socks5"));
        var conns = utils.listConnectionFromTl(socks5);
        utils.respondConnectionList(conns, cb);
    }

    private void listConnInServer(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var conns = utils.listConnectionFromServer(rctx);
        utils.respondConnectionList(conns, cb);
    }

    private void deleteConnFromEl(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb, "force-remove", "connection",
            rctx.param("l4addr-act") + "/" + rctx.param("l4addr-pas"),
            "in", "event-loop", rctx.param("el"), "in", "event-loop-group", rctx.param("elg"));
    }

    private void deleteConnFromTl(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb, "force-remove", "connection",
            rctx.param("l4addr-act") + "/" + rctx.param("l4addr-pas"),
            "in", "tcp-lb", rctx.param("tl"));
    }

    private void deleteConnFromSocks5(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb, "force-remove", "connection",
            rctx.param("l4addr-act") + "/" + rctx.param("l4addr-pas"),
            "in", "socks5-server", rctx.param("socks5"));
    }

    private void deleteConnFromServer(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb, "force-remove", "connection",
            rctx.param("l4addr-act") + "/" + rctx.param("l4addr-pas"),
            "in", "server", rctx.param("svr"), "in", "server-group", rctx.param("sg"));
    }

    private void deleteConnFromElRegexp(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb, "force-remove", "connection",
            "/" + rctx.param("regexp") + "/",
            "in", "event-loop", rctx.param("el"), "in", "event-loop-group", rctx.param("elg"));
    }

    private void deleteConnFromTlRegexp(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb, "force-remove", "connection",
            "/" + rctx.param("regexp") + "/",
            "in", "tcp-lb", rctx.param("tl"));
    }

    private void deleteConnFromSocks5Regexp(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb, "force-remove", "connection",
            "/" + rctx.param("regexp") + "/",
            "in", "socks5-server", rctx.param("socks5"));
    }

    private void deleteConnFromServerRegexp(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb, "force-remove", "connection",
            "/" + rctx.param("regexp") + "/",
            "in", "server", rctx.param("svr"), "in", "server-group", rctx.param("sg"));
    }

    private void listSessionInTl(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var tl = Application.get().tcpLBHolder.get(rctx.param("tl"));
        utils.listSessionsInTl(tl, cb);
    }

    private void listSessionInSocks5(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var socks5 = Application.get().socks5ServerHolder.get(rctx.param("socks5"));
        utils.listSessionsInTl(socks5, cb);
    }

    private void deleteSessionInTl(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb, "force-remove", "session",
            rctx.param("front-act") + "/" + rctx.param("front-pas") + "->" + rctx.param("back-act") + "/" + rctx.param("back-pas"),
            "in", "tcp-lb", rctx.param("tl"));
    }

    private void deleteSessionInSocks5(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb, "force-remove", "session",
            rctx.param("front-act") + "/" + rctx.param("front-pas") + "->" + rctx.param("back-act") + "/" + rctx.param("back-pas"),
            "in", "socks5-server", rctx.param("socks5"));
    }

    private void deleteSessionInTlRegexp(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb, "force-remove", "session",
            "/" + rctx.param("regexp") + "/",
            "in", "tcp-lb", rctx.param("tl"));
    }

    private void deleteSessionInSocks5Regexp(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        utils.execute(cb, "force-remove", "session",
            "/" + rctx.param("regexp") + "/",
            "in", "socks5-server", rctx.param("socks5"));
    }

    private void listDnsCache(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) {
        var list = new LinkedList<Cache>();
        Resolver.getDefault().copyCache(list);
        var ret = list.stream().map(c -> new ObjectBuilder()
            .put("host", c.host)
            .putArray("ipv4", arr -> c.ipv4.forEach(i -> arr.add(Utils.ipStr(i.getAddress()))))
            .putArray("ipv6", arr -> c.ipv6.forEach(i -> arr.add(Utils.ipStr(i.getAddress()))))
            .put("timestamp", c.timestamp)
            .build()).collect(Collectors.toList());
        cb.succeeded(new SimpleArray(ret));
    }

    private void getBytesInFromL4AddrTl(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var tl = Application.get().tcpLBHolder.get(rctx.param("tl"));
        utils.respondBytesInFromL4AddrTl(rctx.param("l4addr"), tl, cb);
    }

    private void getBytesInFromL4AddrSocks5(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var socks5 = Application.get().socks5ServerHolder.get(rctx.param("socks5"));
        utils.respondBytesInFromL4AddrTl(rctx.param("l4addr"), socks5, cb);
    }

    private void getBytesInFromConnectionOfEl(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var conn = utils.getConnectionFromEl(rctx);
        utils.respondWithTotal(conn.getFromRemoteBytes(), cb);
    }

    private void getBytesInFromConnectionOfTl(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var tl = Application.get().tcpLBHolder.get(rctx.param("tl"));
        var conn = utils.getConnectionFromTl(rctx, tl);
        utils.respondWithTotal(conn.getFromRemoteBytes(), cb);
    }

    private void getBytesInFromConnectionOfSocks5(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var socks5 = Application.get().socks5ServerHolder.get(rctx.param("socks5"));
        var conn = utils.getConnectionFromTl(rctx, socks5);
        utils.respondWithTotal(conn.getFromRemoteBytes(), cb);
    }

    private void getBytesInFromConnectionOfServer(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var conn = utils.getConnectionFromServer(rctx);
        utils.respondWithTotal(conn.getFromRemoteBytes(), cb);
    }

    private void getBytesInFromServer(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var svr = utils.getServer(rctx);
        utils.respondWithTotal(svr.getFromRemoteBytes(), cb);
    }

    private void getBytesOutFromL4AddrTl(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var tl = Application.get().tcpLBHolder.get(rctx.param("tl"));
        utils.respondBytesOutFromL4AddrTl(rctx.param("l4addr"), tl, cb);
    }

    private void getBytesOutFromL4AddrSocks5(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var socks5 = Application.get().socks5ServerHolder.get(rctx.param("socks5"));
        utils.respondBytesOutFromL4AddrTl(rctx.param("l4addr"), socks5, cb);
    }

    private void getBytesOutFromConnectionOfEl(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var conn = utils.getConnectionFromEl(rctx);
        utils.respondWithTotal(conn.getToRemoteBytes(), cb);
    }

    private void getBytesOutFromConnectionOfTl(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var tl = Application.get().tcpLBHolder.get(rctx.param("tl"));
        var conn = utils.getConnectionFromTl(rctx, tl);
        utils.respondWithTotal(conn.getToRemoteBytes(), cb);
    }

    private void getBytesOutFromConnectionOfSocks5(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var socks5 = Application.get().socks5ServerHolder.get(rctx.param("socks5"));
        var conn = utils.getConnectionFromTl(rctx, socks5);
        utils.respondWithTotal(conn.getToRemoteBytes(), cb);
    }

    private void getBytesOutFromConnectionOfServer(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var conn = utils.getConnectionFromServer(rctx);
        utils.respondWithTotal(conn.getToRemoteBytes(), cb);
    }

    private void getBytesOutFromServer(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        var svr = utils.getServer(rctx);
        utils.respondWithTotal(svr.getToRemoteBytes(), cb);
    }

    private void getAcceptedConnFromL4AddrTl(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        String tlStr = rctx.param("tlStr");
        String l4addrStr = rctx.param("l4addr");
        TcpLB tl = Application.get().tcpLBHolder.get(tlStr);
        utils.respondAcceptedConnFromL4AddrTl(l4addrStr, tl, cb);
    }

    private void getAcceptedConnFromL4AddrSocks5(RoutingContext rctx, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        String socks5Str = rctx.param("socks5");
        String l4addrStr = rctx.param("l4addr");
        Socks5Server socks5 = Application.get().socks5ServerHolder.get(socks5Str);
        utils.respondAcceptedConnFromL4AddrTl(l4addrStr, socks5, cb);
    }

    private void watchHealthCheck(RoutingContext rctx) {
        rctx.response().status(200).sendHeadersWithChunked();
        //noinspection unchecked
        Consumer<GlobalEvents.Messages.HealthCheck>[] handler = new Consumer[1];
        handler[0] = msg -> {
            try {
                var svr = msg.server;
                var sg = msg.serverGroup;
                var builder = new ObjectBuilder()
                    .putInst("server", utils.formatServer(svr))
                    .putInst("serverGroup", utils.formatServerGroup(sg));
                rctx.response().sendChunk(ByteArray.from((builder.build().stringify() + "\r\n").getBytes()));
            } catch (Exception e) {
                if ("connection closed".equals(e.getMessage())) {
                    assert Logger.lowLevelDebug("connection closed while sending data, should deregister the handler");
                    GlobalEvents.getInstance().deregister(GlobalEvents.HEALTH_CHECK, handler[0]);
                } else {
                    Logger.error(LogType.IMPROPER_USE, "", e);
                }
            }
        };
        GlobalEvents.getInstance().register(GlobalEvents.HEALTH_CHECK, handler[0]);
    }

    static class Err extends RuntimeException {
        final int code;
        final String message;

        Err(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    private static void handleResult(RoutingContext rctx, Throwable err, JSON.Instance ret) {
        if (err != null) {
            if (err instanceof Err) {
                Err e = (Err) err;
                rctx.response().status(e.code).end(new ObjectBuilder()
                    .put("code", e.code)
                    .put("message", e.message)
                    .build());
            } else if (err instanceof NotFoundException) {
                rctx.response().status(404).end(new ObjectBuilder()
                    .put("code", 404)
                    .put("message", err.getMessage())
                    .build());
            } else if (err instanceof AlreadyExistException) {
                rctx.response().status(409).end(new ObjectBuilder()
                    .put("code", 409)
                    .put("message", err.getMessage())
                    .build());
            } else if (err instanceof XException) {
                rctx.response().status(400).end(new ObjectBuilder()
                    .put("code", 400)
                    .put("message", err.getMessage())
                    .build());
            } else {
                String errId = UUID.randomUUID().toString();
                Logger.error(LogType.IMPROPER_USE, "http request got error when handling in HttpController. errId=" + errId, err);
                rctx.response().status(500).end(new ObjectBuilder()
                    .put("code", 500)
                    .put("errId", errId)
                    .put("message", "something went wrong. " +
                        "please report this to the maintainer with errId=" + errId + ", details will be in the logs. " +
                        "if it's the vproxy community version, you may create an issue on http://github.com/wkgcass/vproxy/issues " +
                        "with related logs")
                    .build());
            }
            return;
        }
        if (ret == null) {
            rctx.response().status(204).end();
        } else {
            rctx.response().status(200).end(ret);
        }
    }

    interface Executor {
        void accept(RoutingContext rctx, Callback<JSON.Instance, Throwable> func) throws NotFoundException;
    }

    private static class WrappedRoutingHandler implements RoutingHandler {
        final Executor executor;
        final JSON.Object bodyTemplate;
        List<String> requiredKeys;

        private WrappedRoutingHandler(Executor executor, JSON.Object bodyTemplate, String[] requiredKeys) {
            this.executor = executor;
            this.bodyTemplate = bodyTemplate;
            this.requiredKeys = Arrays.asList(requiredKeys);
        }

        @Override
        public void accept(RoutingContext rctx) {
            JSON.Instance body = rctx.get(Tool.bodyJson);
            if (bodyTemplate != null) {
                if (body == null) {
                    rctx.response().status(400).end(new ObjectBuilder()
                        .put("code", 400)
                        .put("message", "this api must be called with http body: " + bodyTemplate.stringify())
                        .build());
                    return;
                }
                if (!(body instanceof JSON.Object)) {
                    rctx.response().status(400).end(new ObjectBuilder()
                        .put("code", 400)
                        .put("message", "this api only accepts json object from http body: " + bodyTemplate.stringify())
                        .build());
                    return;
                }
                JSON.Object input = (JSON.Object) body;
                String err = utils.validateBody(bodyTemplate, requiredKeys, input);
                if (err != null) {
                    rctx.response().status(400).end(new ObjectBuilder()
                        .put("code", 400)
                        .put("message", err)
                        .build());
                    return;
                }
            } else {
                if (body != null) {
                    rctx.response().status(400).end(new ObjectBuilder()
                        .put("code", 400)
                        .put("message", "this api should NOT be called with http body")
                        .build());
                    return;
                }
            }
            try {
                executor.accept(rctx, new Callback<>() {
                    @Override
                    protected void onFailed(Throwable err) {
                        handleResult(rctx, err, null);
                    }

                    @Override
                    protected void onSucceeded(JSON.Instance value) {
                        handleResult(rctx, null, value);
                    }
                });
            } catch (Throwable t) {
                handleResult(rctx, t, null);
            }
        }
    }

    private static WrappedRoutingHandler wrapAsync(Executor func) {
        return wrapAsync(func, null);
    }

    private static WrappedRoutingHandler wrapAsync(Executor func, JSON.Object bodyTemplate) {
        return wrapAsync(func, bodyTemplate, new String[0]);
    }

    private static WrappedRoutingHandler wrapAsync(Executor func, JSON.Object bodyTemplate, String... _requiredKeys) {
        return new WrappedRoutingHandler(func, bodyTemplate, _requiredKeys);
    }
}
