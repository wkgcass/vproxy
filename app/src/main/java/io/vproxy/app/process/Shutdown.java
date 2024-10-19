package io.vproxy.app.process;

import io.vproxy.app.app.*;
import io.vproxy.app.app.cmd.handle.param.OffloadHandle;
import io.vproxy.app.app.util.SignalHook;
import io.vproxy.app.controller.HttpController;
import io.vproxy.app.controller.RESPController;
import io.vproxy.app.plugin.PluginWrapper;
import io.vproxy.base.Config;
import io.vproxy.base.component.check.HealthCheckConfig;
import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.component.elgroup.EventLoopWrapper;
import io.vproxy.base.component.svrgroup.ServerGroup;
import io.vproxy.base.dns.DNSClient;
import io.vproxy.base.dns.Resolver;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.Version;
import io.vproxy.base.util.anno.Blocking;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.commons.util.IOUtils;
import io.vproxy.component.app.Socks5Server;
import io.vproxy.component.app.TcpLB;
import io.vproxy.component.secure.SecurityGroup;
import io.vproxy.component.secure.SecurityGroupRule;
import io.vproxy.component.ssl.CertKey;
import io.vproxy.component.svrgroup.Upstream;
import io.vproxy.dns.DNSServer;
import io.vproxy.vmirror.Mirror;
import io.vproxy.vswitch.RouteTable;
import io.vproxy.vswitch.Switch;
import io.vproxy.vswitch.VirtualNetwork;
import io.vproxy.vswitch.iface.*;
import vjson.simple.SimpleString;

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
        IOUtils.writeFileWithBackup(filepath, pid + "\n");
    }

    @Blocking
    public static void autoSave() throws Exception {
        if (Config.configSavingDisabled) {
            Logger.warn(LogType.ALERT, "auto saving is disabled");
            return;
        }
        save(Config.autoSaveFilePath);
    }

    @Blocking // writing file is blocking
    public static synchronized void save(String filepath) throws Exception {
        if (Config.willStop) {
            Logger.warn(LogType.ALERT, "the current program is going to stop, saving is disabled");
            return;
        }
        if (filepath == null) {
            filepath = defaultFilePath();
        }
        filepath = Utils.filename(filepath);
        Logger.alert("Trying to save config into file: " + filepath);
        IOUtils.writeFileWithBackup(filepath, currentConfig());
    }

    private static String jsonstr(String s) {
        return new SimpleString(s).stringify();
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
                cmd.append("add cert-key ").append(jsonstr(ck.alias)).append(" cert ").append(jsonstr(ck.certPaths[0]));
                for (int i = 1; i < ck.certPaths.length; ++i) {
                    cmd.append(",").append(jsonstr(ck.certPaths[i]));
                }
                cmd.append(" ").append("key ").append(jsonstr(ck.keyPath));
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

                String cmd = "add event-loop-group " + jsonstr(elg.alias);
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

                    String cmd = "add event-loop " + jsonstr(eventLoopWrapper.alias) + " to event-loop-group " + jsonstr(elg.alias);
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

                String cmd = "add server-group " + jsonstr(sg.alias) +
                    " timeout " + c.timeout + " period " + c.period + " up " + c.up + " down " + c.down + " protocol " + c.checkProtocol.name() +
                    " method " + sg.getMethod() + " event-loop-group " + jsonstr(sg.eventLoopGroup.alias);
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

                String cmd = "add upstream " + jsonstr(ups.alias);
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
                    String cmd = "add server-group " + jsonstr(sg.alias) + " to upstream " + jsonstr(ups.alias) + " weight " + sg.getWeight();
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
                commands.add("add security-group " + jsonstr(securityGroup.alias) +
                    " default " + (securityGroup.defaultAllow ? "allow" : "deny"));
            }
        }
        {
            // create security group rule
            for (SecurityGroup g : securityGroups) {
                for (SecurityGroupRule r : g.getRules()) {
                    commands.add("add security-group-rule " + jsonstr(r.alias) + " to security-group " + jsonstr(g.alias) +
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
                StringBuilder cmd = new StringBuilder("add tcp-lb " + jsonstr(tl.alias) + " acceptor-elg " + jsonstr(tl.acceptorGroup.alias) +
                    " event-loop-group " + jsonstr(tl.workerGroup.alias) +
                    " address " + tl.bindAddress.formatToIPPortString() + " upstream " + jsonstr(tl.backend.alias) +
                    " timeout " + tl.getTimeout() +
                    " in-buffer-size " + tl.getInBufferSize() + " out-buffer-size " + tl.getOutBufferSize() +
                    " protocol " + tl.protocol);
                if (!tl.securityGroup.alias.equals(SecurityGroup.defaultName)) {
                    cmd.append(" security-group ").append(jsonstr(tl.securityGroup.alias));
                }
                if (tl.getCertKeys() != null) {
                    cmd.append(" cert-key ").append(jsonstr(tl.getCertKeys()[0].alias));
                    for (int i = 1; i < tl.getCertKeys().length; ++i) {
                        cmd.append(",").append(jsonstr(tl.getCertKeys()[i].alias));
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
                String cmd = "add socks5-server " + jsonstr(socks5.alias) + " acceptor-elg " + jsonstr(socks5.acceptorGroup.alias) +
                    " event-loop-group " + jsonstr(socks5.workerGroup.alias) +
                    " address " + socks5.bindAddress.formatToIPPortString() + " upstream " + jsonstr(socks5.backend.alias) +
                    " timeout " + socks5.getTimeout() +
                    " in-buffer-size " + socks5.getInBufferSize() + " out-buffer-size " + socks5.getOutBufferSize() +
                    " " + (socks5.allowNonBackend ? "allow-non-backend" : "deny-non-backend");
                if (!socks5.securityGroup.alias.equals(SecurityGroup.defaultName)) {
                    cmd += " security-group " + jsonstr(socks5.securityGroup.alias);
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
                String cmd = "add dns-server " + jsonstr(dns.alias) +
                    " event-loop-group " + jsonstr(dns.eventLoopGroup.alias) +
                    " address " + dns.bindAddress.formatToIPPortString() + " upstream " + jsonstr(dns.rrsets.alias);
                commands.add(cmd);
            }
        }
        {
            // create server
            for (ServerGroup sg : serverGroupList) {
                for (ServerGroup.ServerHandle sh : sg.getServerHandles()) {
                    if (sh.isLogicDelete()) // ignore logic deleted servers
                        continue;
                    String cmd = "add server " + jsonstr(sh.alias) + " to server-group " + jsonstr(sg.alias) +
                        " address " + sh.formatToIPPortString()
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
                if (!securityGroupNames.contains(sw.bareVXLanAccess.alias) && !SecurityGroup.isPrebuiltSecurityGroupName(sw.bareVXLanAccess.alias)) {
                    Logger.warn(LogType.IMPROPER_USE, "the secg " + sw.bareVXLanAccess.alias + " already removed");
                    continue;
                }

                String cmd = "add switch " + jsonstr(sw.alias)
                    + " address " + sw.vxlanBindingAddress.formatToIPPortString()
                    + " mac-table-timeout " + sw.getMacTableTimeout()
                    + " arp-table-timeout " + sw.getArpTableTimeout()
                    + " event-loop-group " + jsonstr(sw.eventLoopGroup.alias)
                    + " " + sw.defaultIfaceParams.toCommand();
                if (!sw.bareVXLanAccess.alias.equals(SecurityGroup.defaultName)) {
                    cmd += " security-group " + jsonstr(sw.bareVXLanAccess.alias);
                }
                commands.add(cmd);

                Set<String> umemNames = new HashSet<>();
                // create umem
                for (var umem : sw.getUMems()) {
                    cmd = "add umem " + jsonstr(umem.alias) + " to switch " + jsonstr(sw.alias)
                        + " chunks " + umem.chunksSize
                        + " fill-ring-size " + umem.fillRingSize
                        + " comp-ring-size " + umem.compRingSize
                        + " frame-size " + umem.frameSize;
                    commands.add(cmd);
                    umemNames.add(umem.alias);
                }
                // create vrf
                for (var key : sw.getNetworks().keySet()) {
                    int vrf = key;
                    VirtualNetwork network = sw.getNetworks().get(vrf);
                    cmd = "add vrf " + vrf + " to switch " + jsonstr(sw.alias) + " v4network " + network.v4network;
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
                        if (ipmac.annotations.nosave) {
                            continue;
                        }
                        cmd = "add ip " + ipmac.ip.formatToIPString() + " to vrf " + vrf + " in switch " + jsonstr(sw.alias)
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
                        cmd = "remove route " + RouteTable.defaultRuleName + " from vrf " + vrf + " in switch " + jsonstr(sw.alias);
                        commands.add(cmd);
                    }
                    if (!hasDefaultV6 && network.v6network != null) {
                        cmd = "remove route " + RouteTable.defaultRuleV6Name + " from vrf " + vrf + " in switch " + jsonstr(sw.alias);
                        commands.add(cmd);
                    }
                    for (var r : network.routeTable.getRules()) {
                        if (r.alias.equals(RouteTable.defaultRuleName) || r.alias.equals(RouteTable.defaultRuleV6Name)) {
                            continue;
                        }
                        cmd = "add route " + jsonstr(r.alias) + " to vrf " + vrf + " in switch " + jsonstr(sw.alias) + " network " + r.rule;
                        if (r.ip == null) {
                            cmd += " vrf " + r.toVrf;
                        } else {
                            cmd += " via " + r.ip.formatToIPString();
                        }
                        commands.add(cmd);
                    }
                }
                // create remote sw
                for (var iface : sw.getIfaces()) {
                    if (!(iface instanceof RemoteSwitchIface rsi)) {
                        continue;
                    }
                    cmd = "add switch " + jsonstr(rsi.alias) + " to switch " + jsonstr(sw.alias) + " address " + rsi.udpSockAddress.formatToIPPortString();
                    if (!rsi.addSwitchFlag) {
                        cmd += " no-switch-flag";
                    }
                    commands.add(cmd);
                }
                // create tap
                for (var iface : sw.getIfaces()) {
                    if (!(iface instanceof TapIface tap)) {
                        continue;
                    }
                    cmd = "add tap " + jsonstr(tap.dev) + " to switch " + jsonstr(sw.alias) + " vrf " + tap.localSideVrf;
                    if (tap.postScript != null && !tap.postScript.isBlank()) {
                        cmd += " post-script " + jsonstr(tap.postScript);
                    }
                    commands.add(cmd);
                }
                // create tun
                for (var iface : sw.getIfaces()) {
                    if (!(iface instanceof TunIface tun)) {
                        continue;
                    }
                    if (iface instanceof FubukiTunIface f) {
                        cmd = "add fubuki " + jsonstr(f.nodeName) + " to switch " + jsonstr(sw.alias)
                              + " password " + jsonstr(f.key) + " vrf " + f.localSideVrf + " mac " + f.mac
                              + " address " + f.serverIPPort.formatToIPPortString();
                        if (f.getLocalAddr() != null) {
                            cmd += " ip " + f.getLocalAddr().formatToIPMaskString();
                        }
                    } else {
                        cmd = "add tun " + jsonstr(tun.dev) + " to switch " + jsonstr(sw.alias) + " vrf " + tun.localSideVrf
                              + " mac " + tun.mac;
                        if (tun.postScript != null && !tun.postScript.isBlank()) {
                            cmd += " post-script " + jsonstr(tun.postScript);
                        }
                    }
                    commands.add(cmd);
                }
                // create xdp
                for (var iface : sw.getIfaces()) {
                    if (!(iface instanceof XDPIface xdp)) {
                        continue;
                    }
                    if (!umemNames.contains(xdp.umem.alias)) {
                        Logger.warn(LogType.IMPROPER_USE, "the umem " + xdp.umem.alias + " already removed");
                        continue;
                    }
                    cmd = "add xdp " + jsonstr(xdp.nic) + " to switch " + jsonstr(sw.alias)
                        + " umem " + jsonstr(xdp.umem.alias)
                        + " queue " + xdp.params.queueId()
                        + " rx-ring-size " + xdp.params.rxRingSize()
                        + " tx-ring-size " + xdp.params.txRingSize()
                        + " mode " + xdp.params.mode().name()
                        + " busy-poll " + xdp.params.busyPollBudget()
                        + " vrf " + xdp.vrf;
                    if (xdp.params.pktswOffloaded() || xdp.params.csumOffloaded()) {
                        cmd += " offload ";
                        var ls = new ArrayList<String>();
                        if (xdp.params.pktswOffloaded())
                            ls.add(OffloadHandle.PACKET_SWITCHING);
                        if (xdp.params.csumOffloaded())
                            ls.add(OffloadHandle.CHECKSUM);
                        cmd += jsonstr(String.join(",", ls));
                    }
                    if (xdp.params.zeroCopy()) {
                        cmd += " zerocopy";
                    }
                    if (xdp.params.rxGenChecksum()) {
                        cmd += " rx-gen-csum";
                    }
                    commands.add(cmd);
                }
                // create fubuki-etherip
                for (var iface : sw.getIfaces()) {
                    if (!(iface instanceof FubukiEtherIPIface etherip)) {
                        continue;
                    }
                    cmd = "add fubuki-etherip " + jsonstr(etherip.getParentIface().name().substring("fubuki:".length())) +
                          " to switch " + jsonstr(sw.alias) + " vrf " + etherip.localSideVrf + " ip " + etherip.targetIP.formatToIPString();
                    commands.add(cmd);
                }
                // create sub interfaces
                for (var iface : sw.getIfaces()) {
                    if (!(iface instanceof VLanAdaptorIface vif)) {
                        continue;
                    }
                    if (!switchInterfaceRequiresSaving(vif.getParentIface())) {
                        continue;
                    }
                    cmd = "add vlan " + vif.remoteVLan + "@" + jsonstr(vif.getParentIface().name()) + " to switch " + jsonstr(sw.alias) + " vrf " + vif.localVrf;
                    commands.add(cmd);
                }
                // set iface options
                for (var iface : sw.getIfaces()) {
                    if (!switchInterfaceRequiresSaving(iface)) {
                        continue;
                    }
                    cmd = "update iface " + jsonstr(iface.name()) + " in switch " + jsonstr(sw.alias)
                        + " " + iface.getParams().toCommand()
                        + " " + (iface.isDisabled() ? "disable" : "enable");
                    if (!iface.getAnnotations().isEmpty()) {
                        cmd += " annotations " + iface.getAnnotations();
                    }
                    commands.add(cmd);
                }
                // add persistent arp records
                var networks = sw.getNetworks();
                for (var vrf : networks.keySet()) {
                    var network = networks.get(vrf);
                    var macEntries = network.macTable.listEntries();
                    for (var mac : macEntries) {
                        if (mac.getTimeout() != -1) {
                            continue;
                        }
                        cmd = "add arp " + mac.mac + " to vrf " + network.vrf + " in sw " + jsonstr(sw.alias) + " iface " + jsonstr(mac.iface.name());
                        commands.add(cmd);
                    }
                    var ipEntries = network.arpTable.listEntries();
                    for (var ip : ipEntries) {
                        if (ip.getTimeout() != -1) {
                            continue;
                        }
                        cmd = "add arp " + ip.mac + " to vrf " + network.vrf + " in sw " + jsonstr(sw.alias) + " ip " + ip.ip.formatToIPString();
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
                cmd = "System: add resp-controller " + jsonstr(resp.alias) +
                      " address " + resp.server.bind.formatToIPPortString() + " password " + jsonstr(new String(resp.password));
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
                cmd = "System: add http-controller " + jsonstr(http.getAlias()) + " address " + http.getAddress().formatToIPPortString();
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
                cmd = "System: add plugin " + jsonstr(plugin.alias)
                      + " url " + Arrays.stream(plugin.urls).map(url -> jsonstr(url.toString())).collect(Collectors.joining(","))
                      + " class " + jsonstr(plugin.plugin.getClass().getName());
                if (plugin.args.length > 0) {
                    cmd += " arguments " + jsonstr(Utils.formatArrayToStringCompact(plugin.args));
                }
                commands.add(cmd);
                if (plugin.isEnabled()) {
                    cmd = "System: update plugin " + jsonstr(plugin.alias) + " enable";
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
            || iface instanceof XDPIface
            || iface instanceof TapIface
            || iface instanceof TunIface
            || iface instanceof FubukiEtherIPIface
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
