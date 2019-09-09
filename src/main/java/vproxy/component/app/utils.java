package vproxy.component.app;

import vjson.JSON;
import vjson.simple.SimpleArray;
import vjson.util.ObjectBuilder;
import vproxy.app.Application;
import vproxy.app.cmd.CmdResult;
import vproxy.app.cmd.Command;
import vproxy.component.auto.SmartGroupDelegate;
import vproxy.component.auto.SmartServiceDelegate;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.elgroup.EventLoopWrapper;
import vproxy.component.exception.NotFoundException;
import vproxy.component.proxy.Session;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.secure.SecurityGroupRule;
import vproxy.component.ssl.CertKey;
import vproxy.component.svrgroup.ServerGroup;
import vproxy.component.svrgroup.ServerGroups;
import vproxy.connection.BindServer;
import vproxy.connection.Connection;
import vproxy.util.Callback;
import vproxy.util.Logger;
import vproxy.util.Utils;
import vserver.RoutingContext;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class utils {
    private utils() {
    }

    static String validateName(String name) {
        // only a-zA-Z0-9_$
        // length > 0
        char[] cs = name.toCharArray();
        if (cs.length == 0) {
            return "input name is invalid: the name string is empty";
        }
        for (char c : cs) {
            if (('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || ('0' <= c && c <= '9') || c == '-' || c == '_' || c == '$') {
                continue;
            }
            return "input name is invalid: " + name + ", can only be a-zA-Z0-9_-$";
        }
        return null;
    }

    static void listSessionsInTl(TcpLB tl, Callback<JSON.Instance, Throwable> cb) {
        var list = listSessionFromTl(tl);
        var ret = list.stream().map(sess -> new ObjectBuilder()
            .put("frontend", sess.active.id())
            .put("backend", sess.passive.id())
            .build()).collect(Collectors.toList());
        cb.succeeded(new SimpleArray(ret));
    }

    static void execute(Callback<JSON.Instance, Throwable> cb, String... args) {
        execute(cb, Arrays.asList(args));
    }

    static void execute(Callback<JSON.Instance, Throwable> cb, List<String> args) {
        Command cmd;
        try {
            cmd = Command.parseStrCmd(args);
        } catch (Exception e) {
            Logger.shouldNotHappen("parsing command failed: " + args);
            cb.failed(e);
            return;
        }
        cmd.run(new Callback<>() {
            @Override
            protected void onSucceeded(CmdResult value) {
                cb.succeeded(null);
            }

            @Override
            protected void onFailed(Throwable err) {
                cb.failed(err);
            }
        });
    }

    private static String nameOfTl(TcpLB tl) {
        return (tl instanceof Socks5Server ? "socks5-server" : "tcp-lb") + " " + tl.alias;
    }

    static void respondBytesInFromL4AddrTl(String l4addrStr, TcpLB tl, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        InetSocketAddress l4addr = tl.bindAddress;
        if (Utils.l4addrStr(l4addr).equals(l4addrStr)) {
            long sum = tl.servers.keySet().stream().mapToLong(BindServer::getFromRemoteBytes).sum();
            respondWithTotal(sum, cb);
        } else {
            throw new NotFoundException("l4addr in " + nameOfTl(tl), l4addrStr);
        }
    }

    static void respondBytesOutFromL4AddrTl(String l4addrStr, TcpLB tl, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        InetSocketAddress l4addr = tl.bindAddress;
        if (Utils.l4addrStr(l4addr).equals(l4addrStr)) {
            long sum = tl.servers.keySet().stream().mapToLong(BindServer::getToRemoteBytes).sum();
            respondWithTotal(sum, cb);
        } else {
            throw new NotFoundException("l4addr in " + nameOfTl(tl), l4addrStr);
        }
    }

    static EventLoopWrapper getEventLoop(RoutingContext rctx) throws NotFoundException {
        return Application.get().eventLoopGroupHolder.get(rctx.param("elg")).get(rctx.param("el"));
    }

    static List<Connection> listConnectionFromEl(RoutingContext rctx) throws NotFoundException {
        var conns = new LinkedList<Connection>();
        getEventLoop(rctx).copyConnections(conns);
        return conns;
    }

    static Connection getConnectionFromEl(RoutingContext rctx) throws NotFoundException {
        var conns = listConnectionFromEl(rctx);
        return getConnectionFromList(rctx, conns);
    }

    static List<Session> listSessionFromTl(TcpLB tl) {
        var sessions = new LinkedList<Session>();
        tl.copySessions(sessions);
        return sessions;
    }

    static List<Connection> listConnectionFromTl(TcpLB tl) {
        return listSessionFromTl(tl).stream().flatMap(s -> Stream.of(s.active, s.passive)).collect(Collectors.toList());
    }

    static Connection getConnectionFromTl(RoutingContext rctx, TcpLB tl) throws NotFoundException {
        var conns = listConnectionFromTl(tl);
        return getConnectionFromList(rctx, conns);
    }

    static List<Connection> listConnectionFromServer(RoutingContext rctx) throws NotFoundException {
        var conns = new LinkedList<Connection>();
        var svr = getServer(rctx);
        svr.copyConnections(conns);
        return conns;
    }

    static Connection getConnectionFromServer(RoutingContext rctx) throws NotFoundException {
        var conns = listConnectionFromServer(rctx);
        return getConnectionFromList(rctx, conns);
    }

    static Connection getConnectionFromList(RoutingContext rctx, List<Connection> conns) throws NotFoundException {
        String requested = rctx.param("l4addr-act") + "/" + rctx.param("l4addr-pas");
        var connOpt = conns.stream().filter(c -> c.id().equals(requested)).findAny();
        if (connOpt.isEmpty())
            throw new NotFoundException("session", requested);
        return connOpt.get();
    }

    static ServerGroup.ServerHandle getServer(RoutingContext rctx) throws NotFoundException {
        var sgName = rctx.param("sg");
        var svrName = rctx.param("svr");
        Optional<ServerGroup.ServerHandle> svr = Application.get().serverGroupHolder.get(sgName).getServerHandles().stream()
            .filter(s -> !s.isLogicDelete() && s.alias.equals(svrName)).findAny();
        if (svr.isEmpty())
            throw new NotFoundException("server in server-group " + sgName, svrName);
        return svr.get();
    }

    static void respondAcceptedConnFromL4AddrTl(String l4addrStr, TcpLB tl, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        InetSocketAddress l4addr = tl.bindAddress;
        if (Utils.l4addrStr(l4addr).equals(l4addrStr)) {
            long sum = tl.servers.keySet().stream().mapToLong(BindServer::getHistoryAcceptedConnectionCount).sum();
            respondWithTotal(sum, cb);
        } else {
            throw new NotFoundException("l4addr " + nameOfTl(tl), l4addrStr);
        }
    }

    static void respondWithTotal(long total, Callback<JSON.Instance, Throwable> cb) {
        cb.succeeded(new ObjectBuilder().put("total", total).build());
    }

    public static void respondConnectionList(List<Connection> conns, Callback<JSON.Instance, Throwable> cb) {
        cb.succeeded(new SimpleArray(
            conns.stream().map(conn -> new ObjectBuilder()
                .put("remote", Utils.l4addrStr(conn.remote))
                .put("local", conn.getLocal() == null ? null : Utils.l4addrStr(conn.getLocal()))
                .build()).collect(Collectors.toList())
        ));
    }

    public static void respondBindServerListInTl(TcpLB tl, Callback<JSON.Instance, Throwable> cb) {
        cb.succeeded(new SimpleArray(
            tl.servers.keySet().stream().map(s -> new ObjectBuilder()
                .put("bind", Utils.l4addrStr(s.bind))
                .build()).collect(Collectors.toList())
        ));
    }

    static void respondBindServerList(Collection<BindServer> list, Callback<JSON.Instance, Throwable> cb) {
        cb.succeeded(new SimpleArray(
            list.stream().map(s -> new ObjectBuilder()
                .put("bind", Utils.l4addrStr(s.bind))
                .build()).collect(Collectors.toList())
        ));
    }

    private static String typeName(Class<?> type) {
        Class[] interfaces = type.getInterfaces();
        for (Class c : interfaces) {
            if (JSON.Instance.class.isAssignableFrom(c)) {
                return "JSON." + c.getSimpleName();
            }
        }
        return typeName(type.getSuperclass());
    }

    private static boolean typeNe(Class<?> expected, Class<?> actual) {
        return !typeName(expected).equals(typeName(actual));
    }

    public static String validateBody(JSON.Object bodyTemplate, List<String> requiredKeys, JSON.Object input) {
        Set<String> checkKeys = bodyTemplate.keySet();
        for (String key : checkKeys) {
            JSON.Instance expected = bodyTemplate.get(key);
            if (input.containsKey(key)) {
                JSON.Instance actual = input.get(key);
                if (typeNe(expected.getClass(), actual.getClass())) {
                    return "value type is wrong for " + key + ", expecting " + typeName(expected.getClass());
                }
                if (key.equals("name") && expected instanceof JSON.String) {
                    String nameValue = ((JSON.String) actual).toJavaObject();
                    String err = validateName(nameValue);
                    if (err != null) {
                        return err;
                    }
                }
                if (expected instanceof JSON.Array) {
                    // we only check one depth into this
                    JSON.Instance expectedElem = ((JSON.Array) expected).get(0);
                    JSON.Array actualArr = (JSON.Array) actual;
                    for (int i = 0; i < actualArr.length(); ++i) {
                        if (typeNe(actualArr.get(i).getClass(), expectedElem.getClass())) {
                            return "value type is wrong for " + key + "[" + i + "], expecting " + typeName(expectedElem.getClass());
                        }
                    }
                }
            } else {
                if (requiredKeys.contains(key)) {
                    return key + " is required by this api, you may refer to: " + bodyTemplate.stringify();
                }
            }
        }
        Set<String> inputKeys = input.keySet();
        for (String key : inputKeys) {
            if (!checkKeys.contains(key)) {
                return "input " + key + " is not recognized, you may refer to: ";
            }
        }
        return null;
    }

    static JSON.Object formatCertKey(CertKey certKey) {
        return new ObjectBuilder()
            .put("name", certKey.alias)
            .putArray("certs", arr -> Arrays.asList(certKey.certPaths).forEach(arr::add))
            .put("key", certKey.keyPath)
            .build();
    }

    static JSON.Object formatServerGroup(ServerGroup sg) {
        return new ObjectBuilder()
            .put("name", sg.alias)
            .put("timeout", sg.getHealthCheckConfig().timeout)
            .put("period", sg.getHealthCheckConfig().period)
            .put("up", sg.getHealthCheckConfig().up)
            .put("down", sg.getHealthCheckConfig().down)
            .put("method", sg.getMethod().toString())
            .put("eventLoopGroup", sg.eventLoopGroup.alias)
            .build();
    }

    static JSON.Object formatServer(ServerGroup.ServerHandle svr) {
        return new ObjectBuilder()
            .put("name", svr.alias)
            .put("address", svr.hostName == null ? Utils.l4addrStr(svr.server) : (svr.hostName + ":" + svr.server.getPort()))
            .put("weight", svr.getWeight())
            .put("currentIp", Utils.ipStr(svr.server.getAddress().getAddress()))
            .put("status", svr.healthy ? "UP" : "DOWN")
            .build();
    }

    static JSON.Object formatServerGroups(ServerGroups sgs) {
        return new ObjectBuilder()
            .put("name", sgs.alias)
            .build();
    }

    static JSON.Object formatServerGroupInGroups(ServerGroups.ServerGroupHandle sg) {
        return new ObjectBuilder()
            .put("name", sg.alias)
            .put("weight", sg.getWeight())
            .build();
    }

    static JSON.Object formatEventLoopGroup(EventLoopGroup elg) {
        return new ObjectBuilder()
            .put("name", elg.alias)
            .build();
    }

    static JSON.Object formatEventLoop(EventLoopWrapper el) {
        return new ObjectBuilder()
            .put("name", el.alias)
            .build();
    }

    static JSON.Object formatSocks5Server(Socks5Server socks5) {
        return new ObjectBuilder()
            .put("name", socks5.alias)
            .put("address", Utils.l4addrStr(socks5.bindAddress))
            .put("backend", socks5.backends.alias)
            .put("acceptorLoopGroup", socks5.acceptorGroup.alias)
            .put("workerLoopGroup", socks5.workerGroup.alias)
            .put("inBufferSize", socks5.getInBufferSize())
            .put("outBufferSize", socks5.getOutBufferSize())
            .put("securityGroup", socks5.securityGroup.alias)
            .put("allowNonBackend", socks5.allowNonBackend)
            .build();
    }

    static JSON.Object formatTcpLb(TcpLB tl) {
        return new ObjectBuilder()
            .put("name", tl.alias)
            .put("address", Utils.l4addrStr(tl.bindAddress))
            .put("backend", tl.backends.alias)
            .put("protocol", tl.protocol)
            .put("acceptorLoopGroup", tl.acceptorGroup.alias)
            .put("workerLoopGroup", tl.workerGroup.alias)
            .put("inBufferSize", tl.getInBufferSize())
            .put("outBufferSize", tl.getOutBufferSize())
            .put("securityGroup", tl.securityGroup.alias)
            .build();
    }

    static JSON.Object formatSecurityGroup(SecurityGroup secg) {
        return new ObjectBuilder()
            .put("name", secg.alias)
            .put("defaultRule", secg.defaultAllow ? "allow" : "deny")
            .build();
    }

    static JSON.Object formatSecurityGroupRule(SecurityGroupRule secgr) {
        return new ObjectBuilder()
            .put("name", secgr.alias)
            .put("clientNetwork", Utils.ipStr(secgr.ip) + "/" + Utils.maskInt(secgr.mask))
            .put("protocol", secgr.protocol.toString())
            .put("serverPortMin", secgr.minPort)
            .put("serverPortMax", secgr.maxPort)
            .put("rule", secgr.allow ? "allow" : "deny")
            .build();
    }

    static JSON.Object formatSmartGroupDelegate(SmartGroupDelegate sgd) {
        return new ObjectBuilder()
            .put("name", sgd.alias)
            .put("service", sgd.service)
            .put("zone", sgd.zone)
            .put("handledGroup", sgd.handledGroup.alias)
            .build();
    }

    static JSON.Object formatSmartServiceDelegate(SmartServiceDelegate ssd) {
        return new ObjectBuilder()
            .put("name", ssd.alias)
            .put("service", ssd.service)
            .put("zone", ssd.zone)
            .put("nic", ssd.nic)
            .put("ipType", ssd.ipType.name())
            .put("exposedPort", ssd.exposedPort)
            .build();
    }
}
