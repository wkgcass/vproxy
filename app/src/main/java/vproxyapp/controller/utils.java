package vproxyapp.controller;

import vfd.IPPort;
import vjson.JSON;
import vjson.simple.SimpleArray;
import vjson.simple.SimpleNull;
import vjson.util.ArrayBuilder;
import vjson.util.ObjectBuilder;
import vproxy.component.app.Socks5Server;
import vproxy.component.app.TcpLB;
import vproxy.component.proxy.Session;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.secure.SecurityGroupRule;
import vproxy.component.ssl.CertKey;
import vproxy.component.svrgroup.Upstream;
import vproxy.dns.DNSServer;
import vproxyapp.app.Application;
import vproxyapp.app.cmd.CmdResult;
import vproxyapp.app.cmd.Command;
import vproxybase.Config;
import vproxybase.component.elgroup.EventLoopGroup;
import vproxybase.component.elgroup.EventLoopWrapper;
import vproxybase.component.svrgroup.ServerGroup;
import vproxybase.connection.Connection;
import vproxybase.connection.ServerSock;
import vproxybase.util.Callback;
import vproxybase.util.LogType;
import vproxybase.util.Logger;
import vproxybase.util.Utils;
import vproxybase.util.exception.NotFoundException;
import vproxybase.util.exception.XException;
import vserver.RoutingContext;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class utils {
    private utils() {
    }

    static String validateName(String name) {
        // only a-zA-Z0-9_$:\-\.\(\)
        // length > 0
        char[] cs = name.toCharArray();
        if (cs.length == 0) {
            return "input name is invalid: the name string is empty";
        }
        for (char c : cs) {
            if (('a' <= c && c <= 'z')
                || ('A' <= c && c <= 'Z')
                || ('0' <= c && c <= '9')
                || c == '-' || c == '_' || c == '$' || c == '.'
                || c == ':' || c == '(' || c == ')') {
                continue;
            }
            return "input name is invalid: " + name + ", can only be a-zA-Z0-9_$:\\-\\.\\(\\)";
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
        IPPort l4addr = tl.bindAddress;
        if (l4addr.formatToIPPortString().equals(l4addrStr)) {
            long sum = tl.servers.keySet().stream().mapToLong(ServerSock::getFromRemoteBytes).sum();
            respondWithTotal(sum, cb);
        } else {
            throw new NotFoundException("l4addr in " + nameOfTl(tl), l4addrStr);
        }
    }

    static void respondBytesOutFromL4AddrTl(String l4addrStr, TcpLB tl, Callback<JSON.Instance, Throwable> cb) throws NotFoundException {
        IPPort l4addr = tl.bindAddress;
        if (l4addr.formatToIPPortString().equals(l4addrStr)) {
            long sum = tl.servers.keySet().stream().mapToLong(ServerSock::getToRemoteBytes).sum();
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
        IPPort l4addr = tl.bindAddress;
        if (l4addr.formatToIPPortString().equals(l4addrStr)) {
            long sum = tl.servers.keySet().stream().mapToLong(ServerSock::getHistoryAcceptedConnectionCount).sum();
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
                .put("remote", conn.remote.formatToIPPortString())
                .put("local", conn.getLocal() == null ? null : conn.getLocal().formatToIPPortString())
                .build()).collect(Collectors.toList())
        ));
    }

    public static void respondServerSockListInTl(TcpLB tl, Callback<JSON.Instance, Throwable> cb) {
        cb.succeeded(new SimpleArray(
            tl.servers.keySet().stream().map(s -> new ObjectBuilder()
                .put("bind", s.bind.formatToIPPortString())
                .build()).collect(Collectors.toList())
        ));
    }

    static void respondServerSockList(Collection<ServerSock> list, Callback<JSON.Instance, Throwable> cb) {
        cb.succeeded(new SimpleArray(
            list.stream().map(s -> new ObjectBuilder()
                .put("bind", s.bind.formatToIPPortString())
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
                    if (!requiredKeys.contains(key)) {
                        if (actual instanceof JSON.Null) {
                            continue;
                        }
                    }
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
                if (expected instanceof JSON.Object) {
                    // we only check one depth into this
                    JSON.Instance expectedElem = ((JSON.Object) expected).get(((JSON.Object) expected).keyList().get(0));
                    JSON.Object actualObj = (JSON.Object) actual;
                    for (String k : actualObj.keySet()) {
                        if (typeNe(actualObj.get(k).getClass(), expectedElem.getClass())) {
                            return "value type is wrong for " + key + "[" + k + "], expecting " + typeName(expectedElem.getClass());
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

    static JSON.Object formatCertKeyDetail(CertKey certKey) {
        byte[] bytes = Utils.sha1(certKey.key.getBytes());
        return new ObjectBuilder()
            .put("name", certKey.alias)
            .putArray("certs", arr -> Arrays.asList(certKey.certPaths).forEach(arr::add))
            .putArray("certPemList", arr -> Arrays.asList(certKey.certs).forEach(arr::add))
            .put("key", certKey.keyPath)
            .put("keySHA1", Base64.getEncoder().encodeToString(bytes))
            .build();
    }

    static JSON.Object formatAnnotations(Map<String, String> map) {
        if (map.isEmpty()) {
            return new ObjectBuilder().build();
        } else {
            ObjectBuilder ob = new ObjectBuilder();
            map.forEach(ob::put);
            return ob.build();
        }
    }

    static JSON.Object formatServerGroup(ServerGroup sg) {
        return new ObjectBuilder()
            .put("name", sg.alias)
            .put("timeout", sg.getHealthCheckConfig().timeout)
            .put("period", sg.getHealthCheckConfig().period)
            .put("up", sg.getHealthCheckConfig().up)
            .put("down", sg.getHealthCheckConfig().down)
            .put("protocol", sg.getHealthCheckConfig().checkProtocol.name())
            .put("method", sg.getMethod().toString())
            .putInst("annotations", formatAnnotations(sg.getAnnotations()))
            .put("eventLoopGroup", sg.eventLoopGroup.alias)
            .build();
    }

    static JSON.Object formatServerGroupDetail(ServerGroup sg) {
        return new ObjectBuilder()
            .put("name", sg.alias)
            .put("timeout", sg.getHealthCheckConfig().timeout)
            .put("period", sg.getHealthCheckConfig().period)
            .put("up", sg.getHealthCheckConfig().up)
            .put("down", sg.getHealthCheckConfig().down)
            .put("protocol", sg.getHealthCheckConfig().checkProtocol.name())
            .put("method", sg.getMethod().toString())
            .putInst("annotations", formatAnnotations(sg.getAnnotations()))
            .putInst("eventLoopGroup", formatEventLoopGroupDetail(sg.eventLoopGroup))
            .putArray("serverList", arr -> sg.getServerHandles().forEach(svr -> arr.addInst(utils.formatServer(svr))))
            .build();
    }

    static JSON.Object formatServer(ServerGroup.ServerHandle svr) {
        return new ObjectBuilder()
            .put("name", svr.alias)
            .put("address", svr.hostName == null ? svr.server.formatToIPPortString() : (svr.hostName + ":" + svr.server.getPort()))
            .put("weight", svr.getWeight())
            .put("currentIp", svr.server.getAddress().formatToIPString())
            .put("status", svr.healthy ? "UP" : "DOWN")
            .put("cost", svr.getHcCost())
            .put("downReason", svr.getHcDownReason())
            .build();
    }

    static JSON.Object formatUpstream(Upstream ups) {
        return new ObjectBuilder()
            .put("name", ups.alias)
            .build();
    }

    static JSON.Object formatUpstreamDetail(Upstream ups) {
        return new ObjectBuilder()
            .put("name", ups.alias)
            .putArray("serverGroupList", arr -> ups.getServerGroupHandles().forEach(sg -> arr.addInst(formatServerGroupInUpstreamDetail(sg))))
            .build();
    }

    static JSON.Object formatServerGroupInUpstream(Upstream.ServerGroupHandle sg) {
        return new ObjectBuilder()
            .put("name", sg.alias)
            .put("weight", sg.getWeight())
            .putInst("annotations", formatAnnotations(sg.getAnnotations()))
            .build();
    }

    static JSON.Object formatServerGroupInUpstreamDetail(Upstream.ServerGroupHandle sg) {
        return new ObjectBuilder()
            .put("name", sg.alias)
            .put("weight", sg.getWeight())
            .putInst("annotations", formatAnnotations(sg.getAnnotations()))
            .putInst("serverGroup", formatServerGroupDetail(sg.group))
            .build();
    }

    static JSON.Object formatEventLoopGroup(EventLoopGroup elg) {
        return new ObjectBuilder()
            .put("name", elg.alias)
            .build();
    }

    static JSON.Object formatEventLoopGroupDetail(EventLoopGroup elg) {
        return new ObjectBuilder()
            .put("name", elg.alias)
            .putArray("eventLoopList", arr -> elg.list().forEach(el -> arr.addInst(formatEventLoop(el))))
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
            .put("address", socks5.bindAddress.formatToIPPortString())
            .put("backend", socks5.backend.alias)
            .put("acceptorLoopGroup", socks5.acceptorGroup.alias)
            .put("workerLoopGroup", socks5.workerGroup.alias)
            .put("inBufferSize", socks5.getInBufferSize())
            .put("outBufferSize", socks5.getOutBufferSize())
            .put("securityGroup", socks5.securityGroup.alias)
            .put("allowNonBackend", socks5.allowNonBackend)
            .build();
    }

    static JSON.Object formatSocks5ServerDetail(Socks5Server socks5) {
        return new ObjectBuilder()
            .put("name", socks5.alias)
            .put("address", socks5.bindAddress.formatToIPPortString())
            .putInst("backend", formatUpstreamDetail(socks5.backend))
            .putInst("acceptorLoopGroup", formatEventLoopGroupDetail(socks5.acceptorGroup))
            .putInst("workerLoopGroup", formatEventLoopGroupDetail(socks5.workerGroup))
            .put("inBufferSize", socks5.getInBufferSize())
            .put("outBufferSize", socks5.getOutBufferSize())
            .putInst("securityGroup", formatSecurityGroupDetail(socks5.securityGroup))
            .put("allowNonBackend", socks5.allowNonBackend)
            .build();
    }

    static JSON.Object formatDNSServer(DNSServer dns) {
        return new ObjectBuilder()
            .put("name", dns.alias)
            .put("address", dns.bindAddress.formatToIPPortString())
            .put("ttl", dns.ttl)
            .put("rrsets", dns.rrsets.alias)
            .put("eventLoopGroup", dns.eventLoopGroup.alias)
            .put("securityGroup", dns.securityGroup.alias)
            .build();
    }

    static JSON.Object formatDNSServerDetail(DNSServer dns) {
        return new ObjectBuilder()
            .put("name", dns.alias)
            .put("address", dns.bindAddress.formatToIPPortString())
            .put("ttl", dns.ttl)
            .putInst("rrsets", formatUpstreamDetail(dns.rrsets))
            .putInst("eventLoopGroup", formatEventLoopGroupDetail(dns.eventLoopGroup))
            .putInst("securityGroup", formatSecurityGroupDetail(dns.securityGroup))
            .build();
    }

    static JSON.Object formatTcpLb(TcpLB tl) {
        JSON.Instance listOfCertKey;
        if (tl.getCertKeys() == null) {
            listOfCertKey = new SimpleNull();
        } else {
            var arr = new ArrayBuilder();
            for (var ck : tl.getCertKeys()) {
                arr.add(ck.alias);
            }
            listOfCertKey = arr.build();
        }
        return new ObjectBuilder()
            .put("name", tl.alias)
            .put("address", tl.bindAddress.formatToIPPortString())
            .put("backend", tl.backend.alias)
            .put("protocol", tl.protocol)
            .put("acceptorLoopGroup", tl.acceptorGroup.alias)
            .put("workerLoopGroup", tl.workerGroup.alias)
            .put("inBufferSize", tl.getInBufferSize())
            .put("outBufferSize", tl.getOutBufferSize())
            .putInst("listOfCertKey", listOfCertKey)
            .put("securityGroup", tl.securityGroup.alias)
            .build();
    }

    static JSON.Object formatTcpLbDetail(TcpLB tl) {
        JSON.Instance listOfCertKey;
        if (tl.getCertKeys() == null) {
            listOfCertKey = new SimpleNull();
        } else {
            var arr = new ArrayBuilder();
            for (var ck : tl.getCertKeys()) {
                arr.addInst(formatCertKeyDetail(ck));
            }
            listOfCertKey = arr.build();
        }
        return new ObjectBuilder()
            .put("name", tl.alias)
            .put("address", tl.bindAddress.formatToIPPortString())
            .put("protocol", tl.protocol)
            .putInst("backend", formatUpstreamDetail(tl.backend))
            .putInst("acceptorLoopGroup", formatEventLoopGroupDetail(tl.acceptorGroup))
            .putInst("workerLoopGroup", formatEventLoopGroupDetail(tl.workerGroup))
            .put("inBufferSize", tl.getInBufferSize())
            .put("outBufferSize", tl.getOutBufferSize())
            .putInst("listOfCertKey", listOfCertKey)
            .putInst("securityGroup", formatSecurityGroupDetail(tl.securityGroup))
            .build();
    }

    static JSON.Object formatSecurityGroup(SecurityGroup secg) {
        return new ObjectBuilder()
            .put("name", secg.alias)
            .put("defaultRule", secg.defaultAllow ? "allow" : "deny")
            .build();
    }

    static JSON.Object formatSecurityGroupDetail(SecurityGroup secg) {
        return new ObjectBuilder()
            .put("name", secg.alias)
            .put("defaultRule", secg.defaultAllow ? "allow" : "deny")
            .putArray("ruleList", arr -> secg.getRules().forEach(r -> arr.addInst(utils.formatSecurityGroupRule(r))))
            .build();
    }

    static JSON.Object formatSecurityGroupRule(SecurityGroupRule secgr) {
        return new ObjectBuilder()
            .put("name", secgr.alias)
            .put("clientNetwork", secgr.network.toString())
            .put("protocol", secgr.protocol.toString())
            .put("serverPortMin", secgr.minPort)
            .put("serverPortMax", secgr.maxPort)
            .put("rule", secgr.allow ? "allow" : "deny")
            .build();
    }

    static JSON.Object all() throws NotFoundException {
        var ret = new ObjectBuilder();
        {
            var arr = new ArrayBuilder();
            var tlHolder = Application.get().tcpLBHolder;
            for (var name : tlHolder.names()) {
                arr.addInst(utils.formatTcpLbDetail(tlHolder.get(name)));
            }
            ret.putInst("tcpLbList", arr.build());
        }
        {
            var arr = new ArrayBuilder();
            var socks5Holder = Application.get().socks5ServerHolder;
            for (var name : socks5Holder.names()) {
                arr.addInst(utils.formatSocks5ServerDetail(socks5Holder.get(name)));
            }
            ret.putInst("socks5ServerList", arr.build());
        }
        {
            var arr = new ArrayBuilder();
            var dnsHolder = Application.get().dnsServerHolder;
            for (var name : dnsHolder.names()) {
                arr.addInst(utils.formatDNSServerDetail(dnsHolder.get(name)));
            }
            ret.putInst("dnsServerList", arr.build());
        }
        {
            var arr = new ArrayBuilder();
            var elgHolder = Application.get().eventLoopGroupHolder;
            for (var name : elgHolder.names()) {
                arr.addInst(utils.formatEventLoopGroupDetail(elgHolder.get(name)));
            }
            ret.putInst("eventLoopGroupList", arr.build());
        }
        {
            var arr = new ArrayBuilder();
            var upstreamHolder = Application.get().upstreamHolder;
            for (var name : upstreamHolder.names()) {
                arr.addInst(utils.formatUpstreamDetail(upstreamHolder.get(name)));
            }
            ret.putInst("upstreamList", arr.build());
        }
        {
            var arr = new ArrayBuilder();
            var sgHolder = Application.get().serverGroupHolder;
            for (var name : sgHolder.names()) {
                arr.addInst(utils.formatServerGroupDetail(sgHolder.get(name)));
            }
            ret.putInst("serverGroupList", arr.build());
        }
        {
            var arr = new ArrayBuilder();
            var secgHolder = Application.get().securityGroupHolder;
            for (var name : secgHolder.names()) {
                arr.addInst(utils.formatSecurityGroupDetail(secgHolder.get(name)));
            }
            ret.putInst("securityGroupList", arr.build());
        }
        {
            var arr = new ArrayBuilder();
            var ckHolder = Application.get().certKeyHolder;
            for (var name : ckHolder.names()) {
                arr.addInst(utils.formatCertKeyDetail(ckHolder.get(name)));
            }
            ret.putInst("certKeyHolder", arr.build());
        }
        return ret.build();
    }

    static void ensurePemDirectory() throws XException {
        String workDirPath = Config.getWorkingDirectory();
        File workDir = new File(workDirPath);
        if (workDir.exists()) {
            if (!workDir.isDirectory()) {
                Logger.error(LogType.IMPROPER_USE, "working directory " + workDirPath + " is not a directory");
                throw new XException("vproxy working directory is not a directory");
            }
        } else {
            if (!workDir.mkdirs()) {
                Logger.error(LogType.IMPROPER_USE, "working directory " + workDirPath + " creation failed");
                throw new XException("failed when creating the vproxy working directory");
            }
        }
        String pemDirPath = workDirPath + File.separator + "temp-pem";
        File pemDir = new File(pemDirPath);
        if (pemDir.exists()) {
            if (!pemDir.isDirectory()) {
                Logger.error(LogType.IMPROPER_USE, "pem directory " + pemDirPath + " is not a directory");
                throw new XException("vproxy pem dir is not a directory");
            }
        } else {
            if (!pemDir.mkdirs()) {
                Logger.error(LogType.IMPROPER_USE, "pem directory " + pemDirPath + " creation failed");
                throw new XException("failed when creating the vproxy pem directory");
            }
        }
    }

    static synchronized String savePem(String pem) throws XException {
        ensurePemDirectory();
        pem = pem.trim();
        String sha1 = Utils.bytesToHex(Utils.sha1(pem.getBytes()));
        File newPem = new File(Config.getWorkingDirectory() + File.separator + "temp-pem" + File.separator + sha1);
        if (newPem.exists()) {
            return newPem.getAbsolutePath();
        }
        try {
            //noinspection ResultOfMethodCallIgnored
            newPem.createNewFile();
        } catch (IOException e) {
            Logger.shouldNotHappen("creating pem file failed: " + newPem, e);
            throw new XException("creating pem file failed");
        }
        try (FileOutputStream fos = new FileOutputStream(newPem)) {
            fos.write(pem.getBytes());
            fos.flush();
        } catch (Exception e) {
            Logger.shouldNotHappen("writing pem file failed: " + newPem, e);
            throw new XException("writing pem file failed");
        }
        return newPem.getAbsolutePath();
    }
}
