package vproxy.component.app;

import vproxy.app.*;
import vproxy.app.cmd.CmdResult;
import vproxy.app.cmd.Command;
import vproxy.component.check.HealthCheckConfig;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.elgroup.EventLoopWrapper;
import vproxy.component.exception.NotFoundException;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.secure.SecurityGroupRule;
import vproxy.component.ssl.CertKey;
import vproxy.component.svrgroup.ServerGroup;
import vproxy.component.svrgroup.Upstream;
import vproxy.dns.DNSServer;
import vproxy.util.*;
import vswitch.RouteTable;
import vswitch.Switch;
import vswitch.iface.UserClientIface;
import vswitch.util.Consts;
import vswitch.util.UserInfo;
import vswitch.iface.RemoteSwitchIface;

import java.io.*;
import java.util.*;

public class Shutdown {
    private Shutdown() {
    }

    private static boolean initiated = false;
    private static int sigIntTimes = 0;
    public static int sigIntBeforeTerminate = 3;

    public static void initSignal() {
        if (initiated)
            return;
        initiated = true;
        try {
            SignalHook.getInstance().sigInt(() -> {
                ++sigIntTimes;
                if (sigIntTimes >= sigIntBeforeTerminate) {
                    sigIntTimes = -10000; // set to a very small number to prevent triggered multiple times
                    endSaveAndQuit(128 + 2);
                } else {
                    System.out.println("press ctrl-c more times to quit");
                }
            });
            assert Logger.lowLevelDebug("SIGINT handled");
        } catch (Exception e) {
            System.err.println("SIGINT not handled");
        }
        try {
            SignalHook.getInstance().sigHup(() -> endSaveAndQuit(128 + 1));
            assert Logger.lowLevelDebug("SIGHUP handled");
        } catch (Exception e) {
            System.err.println("SIGHUP not handled");
        }
        try {
            SignalHook.getInstance().sigTerm(() -> endSaveAndQuit(128 + 1));
            assert Logger.lowLevelDebug("SIGTERM handled");
        } catch (Exception e) {
            System.err.println("SIGTERM not handled");
        }
        try {
            SignalHook.getInstance().sigUsr1(() -> {
                try {
                    autoSave();
                } catch (Exception e) {
                    Logger.shouldNotHappen("save failed", e);
                }
            });
        } catch (Exception e) {
            System.err.println("SIGUSR1 not handled");
        }
        try {
            SignalHook.getInstance().sigUsr2(() -> endSaveAndSoftQuit(128 + 12));
        } catch (Exception e) {
            System.err.println("SIGUSR2 not handled");
        }
        new Thread(() -> {
            while (true) {
                sigIntTimes = 0;
                try {
                    Thread.sleep(1000); // reset the counter every 1 second
                } catch (InterruptedException ignore) {
                }
            }
        }, "ClearSigIntTimesThread").start();
    }

    public static void shutdown() {
        System.err.println("bye");
        endSaveAndQuit(0);
    }

    private static void endSaveAndQuit(int exitCode) {
        end();
        try {
            autoSave();
        } catch (Exception e) {
            Logger.shouldNotHappen("save failed", e);
        }
        System.exit(exitCode);
    }

    private static void endSaveAndSoftQuit(@SuppressWarnings("SameParameterValue") int exitCode) {
        end();
        try {
            autoSave();
        } catch (Exception e) {
            Logger.shouldNotHappen("save failed", e);
        }
        Config.willStop = true;
        var app = Application.get();
        Logger.alert("Stopping controllers: resp-controller, http-controller");
        try {
            var resp = app.respControllerHolder;
            for (var name : resp.names()) {
                resp.removeAndStop(name);
            }
            var http = app.httpControllerHolder;
            for (var name : http.names()) {
                http.removeAndStop(name);
            }
        } catch (Exception e) {
            Logger.shouldNotHappen("removing controllers failed", e);
        }
        Logger.alert("Stopping servers: tcp-lb, socks5-server");
        try {
            var tlHolder = app.tcpLBHolder;
            for (var name : tlHolder.names()) {
                tlHolder.removeAndStop(name);
            }
            var socks5Holder = app.socks5ServerHolder;
            for (var name : socks5Holder.names()) {
                socks5Holder.removeAndStop(name);
            }
        } catch (Exception e) {
            Logger.shouldNotHappen("removing servers failed", e);
        }
        Logger.alert("Waiting for connections to close");
        new Thread(() -> {
            var elgHolder = app.eventLoopGroupHolder;
            var elgList = new ArrayList<EventLoopGroup>(elgHolder.names().size());
            for (var name : elgHolder.names()) {
                try {
                    elgList.add(elgHolder.get(name));
                } catch (NotFoundException ignore) {
                    // ignore if not found
                }
            }
            while (true) {
                boolean noConnection = true;
                try {
                    loopElg:
                    for (var elg : elgList) {
                        for (var el : elg.list()) {
                            if (el.connectionCount() != 0) {
                                noConnection = false;
                                break loopElg;
                            }
                        }
                    }
                } catch (Exception e) {
                    Logger.warn(LogType.ALERT, "got exception when checking event loop groups", e);
                }
                if (noConnection) {
                    break;
                }
                try {
                    Thread.sleep(1_000);
                } catch (InterruptedException e) {
                    // ignore exception
                }
            }
            // use error to make log more obvious
            Logger.error(LogType.ALERT, "No connections, shutdown now");
            System.exit(exitCode);
        }).start();
    }

    private static void end() {
        // do nothing for now
    }

    public static String defaultFilePath() {
        return Utils.homefile(".vproxy.last");
    }

    private static void backupAndRemove(String filepath) throws Exception {
        File f = new File(filepath);
        File bakF = new File(filepath + ".bak");

        if (!f.exists())
            return; // do nothing if no need to backup
        if (bakF.exists() && !bakF.delete()) // remove old backup file
            throw new Exception("remove old backup file failed: " + bakF.getPath());
        if (f.exists() && !f.renameTo(bakF)) // do rename (backup)
            throw new Exception("backup the file failed: " + bakF.getPath());
    }

    public static void writePid(String filepath) throws Exception {
        if (filepath == null) {
            filepath = Utils.homefile(".vproxy.pid");
        }
        filepath = Utils.filename(filepath);

        backupAndRemove(filepath);
        File f = new File(filepath);
        if (!f.createNewFile()) {
            throw new Exception("create new file failed");
        }
        FileOutputStream fos = new FileOutputStream(f);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));

        long pid = ProcessHandle.current().pid();
        bw.write(pid + "\n");
        bw.flush();

        fos.close();
    }

    @Blocking
    public static void autoSave() throws Exception {
        save(Config.autoSaveFilePath);
    }

    @Blocking // writing file is blocking
    public static void save(String filepath) throws Exception {
        if (Config.configSavingDisabled) {
            throw new UnsupportedOperationException("saving is disabled");
        }
        if (Config.willStop) {
            Logger.warn(LogType.ALERT, "the current program is going to stop, saving is disabled");
            return;
        }
        if (filepath == null) {
            filepath = defaultFilePath();
        }
        filepath = Utils.filename(filepath);
        Logger.alert("Trying to save config into file: " + filepath);
        backupAndRemove(filepath);
        File f = new File(filepath);
        if (!f.createNewFile()) {
            throw new Exception("create new file failed");
        }
        FileOutputStream fos = new FileOutputStream(f);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));

        String fileContent = currentConfig();
        bw.write(fileContent);
        bw.flush();

        fos.close();
        Logger.alert("Saving config into file done: " + filepath);
    }

    public static String currentConfig() {
        List<String> commands = new LinkedList<>();

        Application app = Application.get();

        Set<String> certKeyNames = new HashSet<>();

        List<EventLoopGroup> eventLoopGroups = new LinkedList<>();
        Set<String> eventLoopGroupNames = new HashSet<>();

        List<ServerGroup> serverGroupList = new LinkedList<>();

        List<Upstream> upstreams = new LinkedList<>();
        Set<String> upstreamNames = new HashSet<>();

        List<SecurityGroup> securityGroups = new LinkedList<>();
        Set<String> securityGroupNames = new HashSet<>();

        {
            // create cert-key
            CertKeyHolder ckh = app.certKeyHolder;
            List<String> names = ckh.names();
            for (String name : names) {
                CertKey ck;
                try {
                    ck = ckh.get(name);
                } catch (NotFoundException e) {
                    assert Logger.lowLevelDebug("ck not found " + name);
                    assert Logger.printStackTrace(e);
                    continue;
                }

                if (ck.keyPath == null) {
                    Logger.shouldNotHappen("cert-key keyPath is null: " + ck);
                    continue;
                }
                if (ck.certPaths == null) {
                    Logger.shouldNotHappen("cert-key certPath is null: " + ck);
                    continue;
                }
                if (ck.certPaths.length == 0) {
                    Logger.shouldNotHappen("cert-key certPath is empty: " + ck);
                    continue;
                }
                certKeyNames.add(ck.alias);

                StringBuilder cmd = new StringBuilder();
                cmd.append("add cert-key ").append(ck.alias).append(" cert ").append(ck.certPaths[0]);
                for (int i = 1; i < ck.certPaths.length; ++i) {
                    cmd.append(",").append(ck.certPaths[i]);
                }
                cmd.append(" ").append("key ").append(ck.keyPath);
                commands.add(cmd.toString());
            }
        }
        {
            // create event-loop-group
            EventLoopGroupHolder elgh = app.eventLoopGroupHolder;
            List<String> names = elgh.names();
            for (String name : names) {
                EventLoopGroup elg;
                try {
                    elg = elgh.get(name);
                } catch (NotFoundException e) {
                    assert Logger.lowLevelDebug("elg not found " + name);
                    assert Logger.printStackTrace(e);
                    continue;
                }

                eventLoopGroups.add(elg);
                eventLoopGroupNames.add(name);

                if (Application.isDefaultEventLoopGroupName(name)) {
                    continue;
                }

                String cmd = "add event-loop-group " + elg.alias;
                commands.add(cmd);
            }
        }
        {
            // create event-loop
            for (EventLoopGroup elg : eventLoopGroups) {
                if (Application.isDefaultEventLoopGroupName(elg.alias)) {
                    continue;
                }

                List<String> names = elg.names();
                for (String name : names) {
                    EventLoopWrapper eventLoopWrapper;
                    try {
                        eventLoopWrapper = elg.get(name);
                    } catch (NotFoundException e) {
                        assert Logger.lowLevelDebug("el not found " + name);
                        assert Logger.printStackTrace(e);
                        continue;
                    }

                    String cmd = "add event-loop " + eventLoopWrapper.alias + " to event-loop-group " + elg.alias;
                    commands.add(cmd);
                }
            }
        }
        {
            // create server-group
            ServerGroupHolder sgh = app.serverGroupHolder;
            List<String> names = sgh.names();
            for (String name : names) {
                ServerGroup sg;
                try {
                    sg = sgh.get(name);
                } catch (NotFoundException e) {
                    assert Logger.lowLevelDebug("sg not found " + name);
                    assert Logger.printStackTrace(e);
                    continue;
                }
                if (!eventLoopGroupNames.contains(sg.eventLoopGroup.alias)) {
                    Logger.warn(LogType.IMPROPER_USE, "the elg " + sg.eventLoopGroup.alias + " already removed");
                    continue;
                }
                HealthCheckConfig c = sg.getHealthCheckConfig();

                String cmd = "add server-group " + sg.alias +
                    " timeout " + c.timeout + " period " + c.period + " up " + c.up + " down " + c.down + " protocol " + c.checkProtocol.name() +
                    " method " + sg.getMethod() + " event-loop-group " + sg.eventLoopGroup.alias;
                commands.add(cmd);
                serverGroupList.add(sg);
                upstreamNames.add(name);
            }
        }
        {
            // create upstream
            UpstreamHolder sgh = app.upstreamHolder;
            List<String> names = sgh.names();
            for (String name : names) {
                Upstream ups;
                try {
                    ups = sgh.get(name);
                } catch (NotFoundException e) {
                    assert Logger.lowLevelDebug("ups not found " + name);
                    assert Logger.printStackTrace(e);
                    continue;
                }

                String cmd = "add upstream " + ups.alias;
                commands.add(cmd);
                upstreams.add(ups);
                upstreamNames.add(name);
            }
        }
        {
            // attach group into groups
            for (Upstream ups : upstreams) {
                for (Upstream.ServerGroupHandle sg : ups.getServerGroupHandles()) {
                    if (!upstreamNames.contains(sg.alias)) {
                        Logger.warn(LogType.IMPROPER_USE, "the sg " + sg.alias + " already removed");
                        continue;
                    }
                    String cmd = "add server-group " + sg.alias + " to upstream " + ups.alias + " weight " + sg.getWeight();
                    commands.add(cmd);
                }
            }
        }
        {
            // create security group
            List<String> names = app.securityGroupHolder.names();
            for (String name : names) {
                SecurityGroup securityGroup;
                try {
                    securityGroup = app.securityGroupHolder.get(name);
                } catch (NotFoundException e) {
                    assert Logger.lowLevelDebug("secg not found " + name);
                    assert Logger.printStackTrace(e);
                    continue;
                }
                securityGroupNames.add(securityGroup.alias);
                securityGroups.add(securityGroup);
                commands.add("add security-group " + securityGroup.alias +
                    " default " + (securityGroup.defaultAllow ? "allow" : "deny"));
            }
        }
        {
            // create security group rule
            for (SecurityGroup g : securityGroups) {
                for (SecurityGroupRule r : g.getRules()) {
                    commands.add("add security-group-rule " + r.alias + " to security-group " + g.alias +
                        " network " + r.network +
                        " protocol " + r.protocol +
                        " port-range " + r.minPort + "," + r.maxPort +
                        " default " + (r.allow ? "allow" : "deny"));
                }
            }
        }
        {
            // create tcp-lb
            TcpLBHolder tlh = app.tcpLBHolder;
            List<String> names = tlh.names();
            tl:
            for (String name : names) {
                TcpLB tl;
                try {
                    tl = tlh.get(name);
                } catch (NotFoundException e) {
                    assert Logger.lowLevelDebug("tl not found " + name);
                    assert Logger.printStackTrace(e);
                    continue;
                }
                if (!eventLoopGroupNames.contains(tl.acceptorGroup.alias)) {
                    Logger.warn(LogType.IMPROPER_USE, "the elg " + tl.acceptorGroup.alias + " already removed");
                    continue;
                }
                if (!eventLoopGroupNames.contains(tl.workerGroup.alias)) {
                    Logger.warn(LogType.IMPROPER_USE, "the elg " + tl.workerGroup.alias + " already removed");
                    continue;
                }
                if (!upstreamNames.contains(tl.backend.alias)) {
                    Logger.warn(LogType.IMPROPER_USE, "the ups " + tl.backend.alias + " already removed");
                    continue;
                }
                if (!securityGroupNames.contains(tl.securityGroup.alias) && !tl.securityGroup.alias.equals(SecurityGroup.defaultName)) {
                    Logger.warn(LogType.IMPROPER_USE, "the secg " + tl.securityGroup.alias + " already removed");
                    continue;
                }
                if (tl.getCertKeys() != null) {
                    for (CertKey ck : tl.getCertKeys()) {
                        if (!certKeyNames.contains(ck.alias)) {
                            Logger.warn(LogType.IMPROPER_USE, "the cert-key " + ck.alias + " already removed");
                            continue tl;
                        }
                    }
                }
                StringBuilder cmd = new StringBuilder("add tcp-lb " + tl.alias + " acceptor-elg " + tl.acceptorGroup.alias +
                    " event-loop-group " + tl.workerGroup.alias +
                    " address " + Utils.ipport(tl.bindAddress) + " upstream " + tl.backend.alias +
                    " timeout " + tl.getTimeout() +
                    " in-buffer-size " + tl.getInBufferSize() + " out-buffer-size " + tl.getOutBufferSize() +
                    " protocol " + tl.protocol);
                if (!tl.securityGroup.alias.equals(SecurityGroup.defaultName)) {
                    cmd.append(" security-group ").append(tl.securityGroup.alias);
                }
                if (tl.getCertKeys() != null) {
                    cmd.append(" cert-key ").append(tl.getCertKeys()[0].alias);
                    for (int i = 1; i < tl.getCertKeys().length; ++i) {
                        cmd.append(",").append(tl.getCertKeys()[i].alias);
                    }
                }
                commands.add(cmd.toString());
            }
        }
        {
            // create socks5 server
            Socks5ServerHolder socks5h = app.socks5ServerHolder;
            List<String> names = socks5h.names();
            for (String name : names) {
                Socks5Server socks5;
                try {
                    socks5 = socks5h.get(name);
                } catch (NotFoundException e) {
                    assert Logger.lowLevelDebug("socks5 not found " + name);
                    assert Logger.printStackTrace(e);
                    continue;
                }
                if (!eventLoopGroupNames.contains(socks5.acceptorGroup.alias)) {
                    Logger.warn(LogType.IMPROPER_USE, "the elg " + socks5.acceptorGroup.alias + " already removed");
                    continue;
                }
                if (!eventLoopGroupNames.contains(socks5.workerGroup.alias)) {
                    Logger.warn(LogType.IMPROPER_USE, "the elg " + socks5.workerGroup.alias + " already removed");
                    continue;
                }
                if (!upstreamNames.contains(socks5.backend.alias)) {
                    Logger.warn(LogType.IMPROPER_USE, "the ups " + socks5.backend.alias + " already removed");
                    continue;
                }
                if (!securityGroupNames.contains(socks5.securityGroup.alias) && !socks5.securityGroup.alias.equals(SecurityGroup.defaultName)) {
                    Logger.warn(LogType.IMPROPER_USE, "the secg " + socks5.securityGroup.alias + " already removed");
                    continue;
                }
                String cmd = "add socks5-server " + socks5.alias + " acceptor-elg " + socks5.acceptorGroup.alias +
                    " event-loop-group " + socks5.workerGroup.alias +
                    " address " + Utils.ipport(socks5.bindAddress) + " upstream " + socks5.backend.alias +
                    " timeout " + socks5.getTimeout() +
                    " in-buffer-size " + socks5.getInBufferSize() + " out-buffer-size " + socks5.getOutBufferSize() +
                    " " + (socks5.allowNonBackend ? "allow-non-backend" : "deny-non-backend");
                if (!socks5.securityGroup.alias.equals(SecurityGroup.defaultName)) {
                    cmd += " security-group " + socks5.securityGroup.alias;
                }
                commands.add(cmd);
            }
        }
        {
            // create dns server
            DNSServerHolder dnsh = app.dnsServerHolder;
            List<String> names = dnsh.names();
            for (String name : names) {
                DNSServer dns;
                try {
                    dns = dnsh.get(name);
                } catch (NotFoundException e) {
                    assert Logger.lowLevelDebug("dns not found " + name);
                    assert Logger.printStackTrace(e);
                    continue;
                }
                if (!eventLoopGroupNames.contains(dns.eventLoopGroup.alias)) {
                    Logger.warn(LogType.IMPROPER_USE, "the elg " + dns.eventLoopGroup.alias + " already removed");
                    continue;
                }
                if (!upstreamNames.contains(dns.rrsets.alias)) {
                    Logger.warn(LogType.IMPROPER_USE, "the ups " + dns.rrsets.alias + " already removed");
                    continue;
                }
                String cmd = "add dns-server " + dns.alias +
                    " event-loop-group " + dns.eventLoopGroup.alias +
                    " address " + Utils.ipport(dns.bindAddress) + " upstream " + dns.rrsets.alias;
                commands.add(cmd);
            }
        }
        {
            // create server
            for (ServerGroup sg : serverGroupList) {
                for (ServerGroup.ServerHandle sh : sg.getServerHandles()) {
                    if (sh.isLogicDelete()) // ignore logic deleted servers
                        continue;
                    String cmd = "add server " + sh.alias + " to server-group " + sg.alias +
                        " address "
                        + (sh.hostName == null
                        ? Utils.ipStr(sh.server.getAddress().getAddress())
                        : sh.hostName)
                        + ":" + sh.server.getPort()
                        + " weight " + sh.getWeight();
                    commands.add(cmd);
                }
            }
        }
        {
            // create switch
            SwitchHolder swh = app.switchHolder;
            List<String> names = swh.names();
            for (String name : names) {
                Switch sw;
                try {
                    sw = swh.get(name);
                } catch (NotFoundException e) {
                    assert Logger.lowLevelDebug("switch not found " + name);
                    assert Logger.printStackTrace(e);
                    continue;
                }

                // check depended resources
                if (!securityGroupNames.contains(sw.bareVXLanAccess.alias) && !sw.bareVXLanAccess.alias.equals(SecurityGroup.defaultName)) {
                    Logger.warn(LogType.IMPROPER_USE, "the secg " + sw.bareVXLanAccess.alias + " already removed");
                    continue;
                }

                String cmd = "add switch " + sw.alias
                    + " address " + Utils.l4addrStr(sw.vxlanBindingAddress)
                    + " mac-table-timeout " + sw.getMacTableTimeout()
                    + " arp-table-timeout " + sw.getArpTableTimeout()
                    + " event-loop-group " + sw.eventLoopGroup.alias;
                if (!sw.bareVXLanAccess.alias.equals(SecurityGroup.defaultName)) {
                    cmd += " security-group " + sw.bareVXLanAccess.alias;
                }
                commands.add(cmd);

                // create users
                Map<String, UserInfo> users = sw.getUsers();
                for (var entry : users.entrySet()) {
                    cmd = "add user " + entry.getKey() + " to switch " + sw.alias + " password " + entry.getValue().pass + " vni " + entry.getValue().vni;
                    commands.add(cmd);
                }
                // create remote sw
                for (var iface : sw.getIfaces()) {
                    if (!(iface instanceof RemoteSwitchIface)) {
                        continue;
                    }
                    var rsi = (RemoteSwitchIface) iface;
                    cmd = "add switch " + rsi.alias + " to switch " + sw.alias + " address " + Utils.l4addrStr(rsi.udpSockAddress);
                    commands.add(cmd);
                }
                // create user-cli
                for (var iface : sw.getIfaces()) {
                    if (!(iface instanceof UserClientIface)) {
                        continue;
                    }
                    var ucliIface = (UserClientIface) iface;
                    cmd = "add user-client " + ucliIface.user.user.replace(Consts.USER_PADDING, "") + " to switch " + sw.alias
                        + " password " + ucliIface.user.pass + " vni " + ucliIface.user.vni + " address " + Utils.l4addrStr(ucliIface.remoteAddress);
                    commands.add(cmd);
                }
                // create vpc
                for (var entry : sw.getTables().entrySet()) {
                    int vpc = entry.getKey();
                    cmd = "add vpc " + vpc + " to switch " + sw.alias + " v4network " + entry.getValue().v4network;
                    if (entry.getValue().v6network != null) {
                        cmd += " v6network " + entry.getValue().v6network;
                    }
                    commands.add(cmd);

                    var table = entry.getValue();
                    // create ips
                    for (var ip : table.ips.entries()) {
                        cmd = "add ip " + Utils.ipStr(ip.getKey()) + " to vpc " + vpc + " in switch " + sw.alias + " mac " + ip.getValue();
                        commands.add(cmd);
                    }
                    // create|remove routes
                    boolean hasDefaultV4 = false;
                    boolean hasDefaultV6 = false;
                    for (var r : table.routeTable.getRules()) {
                        if (r.alias.equals(RouteTable.defaultRuleName)) {
                            hasDefaultV4 = true;
                        }
                        if (r.alias.equals(RouteTable.defaultRuleV6Name)) {
                            hasDefaultV6 = true;
                        }
                    }
                    if (!hasDefaultV4) {
                        cmd = "remove route " + RouteTable.defaultRuleName + " from vpc " + vpc + " in switch " + sw.alias;
                        commands.add(cmd);
                    }
                    if (!hasDefaultV6 && table.v6network != null) {
                        cmd = "remove route " + RouteTable.defaultRuleV6Name + " from vpc " + vpc + " in switch " + sw.alias;
                        commands.add(cmd);
                    }
                    for (var r : table.routeTable.getRules()) {
                        if (r.alias.equals(RouteTable.defaultRuleName) || r.alias.equals(RouteTable.defaultRuleV6Name)) {
                            continue;
                        }
                        cmd = "add route " + r.alias + " to vpc " + vpc + " in switch " + sw.alias + " network " + r.rule;
                        if (r.ip == null) {
                            cmd += " vni " + r.toVni;
                        } else {
                            cmd += " via " + Utils.ipStr(r.ip);
                        }
                        commands.add(cmd);
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String cmd : commands) {
            sb.append(cmd).append("\n");
        }
        return "# Generated by vproxy " + Application.VERSION + "\n" +
            sb.toString();
    }

    @Blocking // the reading file process is blocking
    public static void load(String filepath, Callback<String, Throwable> cb) throws Exception {
        if (Config.configLoadingDisabled) {
            throw new UnsupportedOperationException("loading is disabled");
        }
        if (filepath == null) {
            filepath = defaultFilePath();
        }
        filepath = Utils.filename(filepath);
        File f = new File(filepath);
        FileInputStream fis = new FileInputStream(f);
        BufferedReader br = new BufferedReader(new InputStreamReader(fis));
        List<String> lines = new ArrayList<>();
        String l;
        while ((l = br.readLine()) != null) {
            lines.add(l);
        }
        List<Command> commands = new ArrayList<>();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) { // skip empty lines
                continue;
            }
            if (line.startsWith("#")) { // comment
                continue;
            }

            Logger.info(LogType.BEFORE_PARSING_CMD, line);
            Command cmd;
            try {
                cmd = Command.parseStrCmd(line);
            } catch (Exception e) {
                Logger.warn(LogType.AFTER_PARSING_CMD, "parse command `" + line + "` failed");
                throw e;
            }
            Logger.info(LogType.AFTER_PARSING_CMD, cmd.toString());
            commands.add(cmd);
        }
        runCommandsOnLoading(commands, 0, cb);
    }

    private static void runCommandsOnLoading(List<Command> commands, int idx, Callback<String, Throwable> cb) {
        if (idx >= commands.size()) {
            // done
            cb.succeeded("");
            return;
        }
        Command cmd = commands.get(idx);
        cmd.run(new Callback<>() {
            @Override
            protected void onSucceeded(CmdResult value) {
                runCommandsOnLoading(commands, idx + 1, cb);
            }

            @Override
            protected void onFailed(Throwable err) {
                cb.failed(err);
            }
        });
    }
}
