package vproxy.app.process;

import vproxy.app.app.*;
import vproxy.app.app.util.SignalHook;
import vproxy.app.controller.HttpController;
import vproxy.app.controller.RESPController;
import vproxy.app.plugin.PluginWrapper;
import vproxy.base.Config;
import vproxy.base.component.check.HealthCheckConfig;
import vproxy.base.component.elgroup.EventLoopGroup;
import vproxy.base.component.elgroup.EventLoopWrapper;
import vproxy.base.component.svrgroup.ServerGroup;
import vproxy.base.dns.DNSClient;
import vproxy.base.dns.Resolver;
import vproxy.base.util.*;
import vproxy.base.util.anno.Blocking;
import vproxy.base.util.callback.Callback;
import vproxy.base.util.exception.NotFoundException;
import vproxy.base.util.thread.VProxyThread;
import vproxy.component.app.Socks5Server;
import vproxy.component.app.TcpLB;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.secure.SecurityGroupRule;
import vproxy.component.ssl.CertKey;
import vproxy.component.svrgroup.Upstream;
import vproxy.dns.DNSServer;
import vproxy.vmirror.Mirror;
import vproxy.vswitch.RouteTable;
import vproxy.vswitch.Switch;
import vproxy.vswitch.VirtualNetwork;
import vproxy.vswitch.iface.*;
import vproxy.vswitch.util.UserInfo;
import vproxy.xdp.BPFObject;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class Shutdown {
    private Shutdown() {
    }

    private static boolean initiated = false;
    private static int sigIntTimes = 0;
    public static int sigIntBeforeTerminate = 3;
    private static volatile boolean signalToShutTriggered = false;

    public static void initSignal() {
        if (initiated)
            return;
        initiated = true;
        try {
            SignalHook.getInstance().sigInt(() -> {
                if (signalToShutTriggered) return;
                ++sigIntTimes;
                if (sigIntTimes >= sigIntBeforeTerminate) {
                    sigIntTimes = -10000; // set to a very small number to prevent triggered multiple times

                    signalToShutTriggered = true;
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
            SignalHook.getInstance().sigHup(() -> {
                if (signalToShutTriggered) return;
                signalToShutTriggered = true;
                endSaveAndQuit(128 + 1);
            });
            assert Logger.lowLevelDebug("SIGHUP handled");
        } catch (Exception e) {
            System.err.println("SIGHUP not handled");
        }
        try {
            SignalHook.getInstance().sigTerm(() -> {
                if (signalToShutTriggered) return;
                signalToShutTriggered = true;
                endSaveAndQuit(128 + 15);
            });
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
            SignalHook.getInstance().sigUsr2(() -> {
                if (signalToShutTriggered) return;
                signalToShutTriggered = true;
                endSaveAndSoftQuit(128 + 12);
            });
        } catch (Exception e) {
            System.err.println("SIGUSR2 not handled");
        }
        VProxyThread.create(() -> {
            //noinspection InfiniteLoopStatement
            while (true) {
                sigIntTimes = 0;
                try {
                    //noinspection BusyWait
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
        Utils.exit(exitCode);
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
        VProxyThread.create(() -> {
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
                    //noinspection BusyWait
                    Thread.sleep(1_000);
                } catch (InterruptedException e) {
                    // ignore exception
                }
            }
            // use error to make log more obvious
            Logger.error(LogType.ALERT, "No connections, shutdown now");
            Utils.exit(exitCode);
        }, "wait-for-connections-to-close").start();
    }

    private static void end() {
        // do nothing for now
    }

    public static String defaultFilePath() {
        return Config.workingDirectoryFile("vproxy.last");
    }

    public static void writePid(String filepath) throws Exception {
        if (filepath == null) {
            filepath = Config.workingDirectoryFile("vproxy.pid");
        }
        filepath = Utils.filename(filepath);
        long pid = ProcessHandle.current().pid();
        Utils.writeFileWithBackup(filepath, pid + "\n");
    }

    @Blocking
    public static void autoSave() throws Exception {
        save(Config.autoSaveFilePath);
    }

    @Blocking // writing file is blocking
    public static synchronized void save(String filepath) throws Exception {
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
        Utils.writeFileWithBackup(filepath, currentConfig());
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

        Set<String> bpfobjectNames = new HashSet<>();

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
                if (!elg.annotations.isEmpty()) {
                    cmd += " annotations " + elg.annotations;
                }
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
                    if (!eventLoopWrapper.annotations.isEmpty()) {
                        cmd += " annotations " + eventLoopWrapper.annotations;
                    }
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
                var anno = sg.getAnnotations();
                if (!anno.isEmpty()) {
                    cmd += " annotations " + anno;
                }
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
                    if (!sg.getAnnotations().isEmpty()) {
                        cmd += " annotations " + sg.getAnnotations();
                    }
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
                    " address " + tl.bindAddress.formatToIPPortString() + " upstream " + tl.backend.alias +
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
                    " address " + socks5.bindAddress.formatToIPPortString() + " upstream " + socks5.backend.alias +
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
                    " address " + dns.bindAddress.formatToIPPortString() + " upstream " + dns.rrsets.alias;
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
                        ? sh.server.getAddress().formatToIPString()
                        : sh.hostName)
                        + ":" + sh.server.getPort()
                        + " weight " + sh.getWeight();
                    commands.add(cmd);
                }
            }
        }
        {
            // create bpf
            BPFObjectHolder bpfobjHolder = app.bpfObjectHolder;
            List<String> names = bpfobjHolder.names();
            for (String name : names) {
                BPFObject bpfobj;
                try {
                    bpfobj = bpfobjHolder.get(name);
                } catch (NotFoundException e) {
                    assert Logger.lowLevelDebug("bpf-object not found " + name);
                    assert Logger.printStackTrace(e);
                    continue;
                }

                String cmd = "add bpf-object " + bpfobj.nic
                    + (bpfobj.filename == null ? "" : (" path " + bpfobj.filename))
                    + " program " + bpfobj.prog
                    + " mode " + bpfobj.mode.name()
                    + " force";
                commands.add(cmd);

                bpfobjectNames.add(bpfobj.nic);
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
                    + " address " + sw.vxlanBindingAddress.formatToIPPortString()
                    + " mac-table-timeout " + sw.getMacTableTimeout()
                    + " arp-table-timeout " + sw.getArpTableTimeout()
                    + " event-loop-group " + sw.eventLoopGroup.alias
                    + " mtu " + sw.defaultMtu
                    + " flood " + (sw.defaultFloodAllowed ? "allow" : "deny");
                if (!sw.bareVXLanAccess.alias.equals(SecurityGroup.defaultName)) {
                    cmd += " security-group " + sw.bareVXLanAccess.alias;
                }
                commands.add(cmd);

                Set<String> umemNames = new HashSet<>();
                // create umem
                for (var umem : sw.getUMems()) {
                    cmd = "add umem " + umem.alias + " to switch " + sw.alias
                        + " chunks " + umem.chunksSize
                        + " fill-ring-size " + umem.fillRingSize
                        + " comp-ring-size " + umem.compRingSize
                        + " frame-size " + umem.frameSize;
                    commands.add(cmd);
                    umemNames.add(umem.alias);
                }
                // create vpc
                for (var key : sw.getNetworks().keySet()) {
                    int vpc = key;
                    VirtualNetwork network = sw.getNetworks().get(vpc);
                    cmd = "add vpc " + vpc + " to switch " + sw.alias + " v4network " + network.v4network;
                    if (network.v6network != null) {
                        cmd += " v6network " + network.v6network;
                    }
                    var anno = network.getAnnotations();
                    if (!anno.isEmpty()) {
                        cmd += " annotations " + anno;
                    }
                    commands.add(cmd);

                    // create ips
                    for (var ipmac : network.ips.entries()) {
                        cmd = "add ip " + ipmac.ip.formatToIPString() + " to vpc " + vpc + " in switch " + sw.alias
                            + " mac " + ipmac.mac
                            + " routing " + (ipmac.routing ? "on" : "off");
                        if (!ipmac.annotations.isEmpty()) {
                            cmd += " annotations " + ipmac.annotations;
                        }
                        commands.add(cmd);
                    }
                    // create|remove routes
                    boolean hasDefaultV4 = false;
                    boolean hasDefaultV6 = false;
                    for (var r : network.routeTable.getRules()) {
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
                    if (!hasDefaultV6 && network.v6network != null) {
                        cmd = "remove route " + RouteTable.defaultRuleV6Name + " from vpc " + vpc + " in switch " + sw.alias;
                        commands.add(cmd);
                    }
                    for (var r : network.routeTable.getRules()) {
                        if (r.alias.equals(RouteTable.defaultRuleName) || r.alias.equals(RouteTable.defaultRuleV6Name)) {
                            continue;
                        }
                        cmd = "add route " + r.alias + " to vpc " + vpc + " in switch " + sw.alias + " network " + r.rule;
                        if (r.ip == null) {
                            cmd += " vni " + r.toVni;
                        } else {
                            cmd += " via " + r.ip.formatToIPString();
                        }
                        commands.add(cmd);
                    }
                }
                // create users
                Map<String, UserInfo> users = sw.getUsers();
                for (var entry : users.entrySet()) {
                    var user = entry.getValue();
                    cmd = "add user " + entry.getKey() + " to switch " + sw.alias
                        + " password " + user.pass
                        + " vni " + user.vni
                        + " mtu " + user.defaultMtu
                        + " flood " + (user.defaultFloodAllowed ? "allow" : "deny");
                    commands.add(cmd);
                }
                // create remote sw
                for (var iface : sw.getIfaces()) {
                    if (!(iface instanceof RemoteSwitchIface)) {
                        continue;
                    }
                    var rsi = (RemoteSwitchIface) iface;
                    cmd = "add switch " + rsi.alias + " to switch " + sw.alias + " address " + rsi.udpSockAddress.formatToIPPortString();
                    if (!rsi.addSwitchFlag) {
                        cmd += " no-switch-flag";
                    }
                    commands.add(cmd);
                }
                // create user-cli
                for (var iface : sw.getIfaces()) {
                    if (!(iface instanceof UserClientIface)) {
                        continue;
                    }
                    var ucliIface = (UserClientIface) iface;
                    cmd = "add user-client " + ucliIface.user.user.replace(Consts.USER_PADDING, "") + " to switch " + sw.alias
                        + " password " + ucliIface.user.pass + " vni " + ucliIface.user.vni + " address " + ucliIface.remote.formatToIPPortString();
                    commands.add(cmd);
                }
                // create tap
                for (var iface : sw.getIfaces()) {
                    if (!(iface instanceof TapIface)) {
                        continue;
                    }
                    var tap = (TapIface) iface;
                    cmd = "add tap " + tap.dev + " to switch " + sw.alias + " vni " + tap.localSideVni;
                    if (tap.postScript != null && !tap.postScript.isBlank()) {
                        cmd += " post-script " + tap.postScript;
                    }
                    commands.add(cmd);
                }
                // create tun
                for (var iface : sw.getIfaces()) {
                    if (!(iface instanceof TunIface)) {
                        continue;
                    }
                    var tun = (TunIface) iface;
                    cmd = "add tun " + tun.dev + " to switch " + sw.alias + " vni " + tun.localSideVni
                        + " mac " + tun.mac;
                    if (tun.postScript != null && !tun.postScript.isBlank()) {
                        cmd += " post-script " + tun.postScript;
                    }
                    commands.add(cmd);
                }
                // create xdp
                for (var iface : sw.getIfaces()) {
                    if (!(iface instanceof XDPIface)) {
                        continue;
                    }
                    var xdp = (XDPIface) iface;
                    if (!bpfobjectNames.contains(xdp.bpfMap.bpfObject.nic)) {
                        Logger.warn(LogType.IMPROPER_USE, "the bpf-object " + xdp.bpfMap.bpfObject.nic + " already removed");
                        continue;
                    }
                    if (!umemNames.contains(xdp.umem.alias)) {
                        Logger.warn(LogType.IMPROPER_USE, "the umem " + xdp.umem.alias + " already removed");
                        continue;
                    }
                    cmd = "add xdp " + xdp.nic + " to switch " + sw.alias
                        + " bpf-map " + xdp.bpfMap.name
                        + " umem " + xdp.umem.alias
                        + " queue " + xdp.queueId
                        + " rx-ring-size " + xdp.rxRingSize
                        + " tx-ring-size " + xdp.txRingSize
                        + " mode " + xdp.mode.name()
                        + " busy-poll " + xdp.busyPollBudget
                        + " vni " + xdp.vni
                        + " bpf-map-key " + xdp.keySelector.alias();
                    if (xdp.zeroCopy) {
                        cmd += " zerocopy";
                    }
                    if (xdp.rxGenChecksum) {
                        cmd += " rx-gen-csum";
                    }
                    commands.add(cmd);
                }
                // create sub interfaces
                for (var iface : sw.getIfaces()) {
                    if (!(iface instanceof VLanAdaptorIface)) {
                        continue;
                    }
                    var vif = (VLanAdaptorIface) iface;
                    if (!switchInterfaceRequiresSaving(vif.getParentIface())) {
                        continue;
                    }
                    cmd = "add vlan " + vif.remoteVLan + "@" + vif.getParentIface().name() + " to switch " + sw.alias + " vni " + vif.localVni;
                    commands.add(cmd);
                }
                // set iface options
                for (var iface : sw.getIfaces()) {
                    if (!switchInterfaceRequiresSaving(iface)) {
                        continue;
                    }
                    cmd = "update iface " + iface.name() + " in switch " + sw.alias
                        + " mtu " + iface.getBaseMTU()
                        + " flood " + (iface.isFloodAllowed() ? "allow" : "deny")
                        + " " + (iface.isDisabled() ? "disable" : "enable");
                    if (!iface.getAnnotations().isEmpty()) {
                        cmd += " annotations " + iface.getAnnotations();
                    }
                    commands.add(cmd);
                }
                // add persistent arp records
                var networks = sw.getNetworks();
                for (var vni : networks.keySet()) {
                    var network = networks.get(vni);
                    var macEntries = network.macTable.listEntries();
                    for (var mac : macEntries) {
                        if (mac.getTimeout() != -1) {
                            continue;
                        }
                        cmd = "add arp " + mac.mac + " to vpc " + network.vni + " in sw " + sw.alias + " iface " + mac.iface.name();
                        commands.add(cmd);
                    }
                    var ipEntries = network.arpTable.listEntries();
                    for (var ip : ipEntries) {
                        if (ip.getTimeout() != -1) {
                            continue;
                        }
                        cmd = "add arp " + ip.mac + " to vpc " + network.vni + " in sw " + sw.alias + " ip " + ip.ip.formatToIPString();
                        commands.add(cmd);
                    }
                }
            }
        }
        {
            // System commands
            String cmd;
            for (var name : app.respControllerHolder.names()) {
                RESPController resp;
                try {
                    resp = app.respControllerHolder.get(name);
                } catch (NotFoundException e) {
                    assert Logger.lowLevelDebug("resp-controller not found " + name);
                    assert Logger.printStackTrace(e);
                    continue;
                }
                cmd = "System: add resp-controller " + resp.alias + " address " + resp.server.bind.formatToIPPortString() + " password " + new String(resp.password);
                commands.add(cmd);
            }
            for (var name : app.httpControllerHolder.names()) {
                HttpController http;
                try {
                    http = app.httpControllerHolder.get(name);
                } catch (NotFoundException e) {
                    assert Logger.lowLevelDebug("http-controller not found " + name);
                    assert Logger.printStackTrace(e);
                    continue;
                }
                cmd = "System: add http-controller " + http.getAlias() + " address " + http.getAddress().formatToIPPortString();
                commands.add(cmd);
            }
            for (var name : app.pluginHolder.names()) {
                PluginWrapper plugin;
                try {
                    plugin = app.pluginHolder.get(name);
                } catch (NotFoundException e) {
                    assert Logger.lowLevelDebug("plugin not found " + name);
                    assert Logger.printStackTrace(e);
                    continue;
                }
                cmd = "System: add plugin " + plugin.alias
                    + " url " + Arrays.stream(plugin.urls).map(URL::toString).collect(Collectors.joining(","))
                    + " class " + plugin.plugin.getClass().getName();
                if (plugin.args.length > 0) {
                    cmd += " arguments " + Utils.formatArrayToStringCompact(plugin.args);
                }
                commands.add(cmd);
                if (plugin.isEnabled()) {
                    cmd = "System: update plugin " + plugin.alias + " enable";
                    commands.add(cmd);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String cmd : commands) {
            sb.append(cmd).append("\n");
        }
        return "# Generated by vproxy " + Version.VERSION + "\n" + sb;
    }

    private static boolean switchInterfaceRequiresSaving(Iface iface) {
        return iface instanceof RemoteSwitchIface
            || iface instanceof UserClientIface
            || iface instanceof XDPIface
            || iface instanceof TapIface
            || iface instanceof TunIface
            || (iface instanceof VLanAdaptorIface && switchInterfaceRequiresSaving(((VLanAdaptorIface) iface).getParentIface()));
    }

    @Blocking // the reading file process is blocking
    public static void load(String filepath, Callback<String, Throwable> cb) throws Exception {
        if (Config.configLoadingDisabled) {
            throw new UnsupportedOperationException("loading is disabled");
        }
        if (filepath == null) {
            filepath = defaultFilePath();
        }
        Loader.INSTANCE.loadCommands(filepath, cb);
    }

    @SuppressWarnings("unused")
    public static void releaseEverything() {
        GlobalInspectionHttpServerLauncher.stop();
        DNSClient.getDefault().close();
        Resolver.stopDefault();
        Mirror.destroy();
        OOMHandler.stop();
        // TODO add more in the future
    }
}
