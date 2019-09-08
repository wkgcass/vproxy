package vproxy.component.app;

import vproxy.app.*;
import vproxy.app.cmd.CmdResult;
import vproxy.app.cmd.Command;
import vproxy.app.mesh.DiscoveryConfigLoader;
import vproxy.app.mesh.SmartGroupDelegateHolder;
import vproxy.component.auto.AutoConfig;
import vproxy.component.auto.SmartGroupDelegate;
import vproxy.component.check.HealthCheckConfig;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.elgroup.EventLoopWrapper;
import vproxy.component.exception.NoException;
import vproxy.component.exception.NotFoundException;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.secure.SecurityGroupRule;
import vproxy.component.ssl.CertKey;
import vproxy.component.svrgroup.ServerGroup;
import vproxy.component.svrgroup.ServerGroups;
import vproxy.util.*;

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
            save(null);
        } catch (Exception e) {
            Logger.shouldNotHappen("save failed", e);
        }
        System.exit(exitCode);
    }

    private static void end() {
        AutoConfig autoConfig = DiscoveryConfigLoader.getInstance().getAutoConfig();
        if (autoConfig != null) {
            BlockCallback<Void, NoException> cb = new BlockCallback<>();
            autoConfig.khala.discovery.close(cb);
            cb.block();
        }
    }

    public static String defaultFilePath() {
        return System.getProperty("user.home") + File.separator + ".vproxy.last";
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
            filepath = System.getProperty("user.home") + File.separator + ".vproxy.pid";
        }
        if (filepath.startsWith("~")) {
            filepath = System.getProperty("user.home") + filepath.substring("~".length());
        }

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

    @Blocking // writing file is blocking
    public static void save(String filepath) throws Exception {
        if (Config.configSavingDisabled) {
            throw new UnsupportedOperationException("saving is disabled");
        }
        if (filepath == null) {
            filepath = defaultFilePath();
        }
        if (filepath.startsWith("~")) {
            filepath = System.getProperty("user.home") + filepath.substring("~".length());
        }
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
    }

    public static String currentConfig() {
        List<String> commands = new LinkedList<>();

        Application app = Application.get();

        Set<String> certKeyNames = new HashSet<>();

        List<EventLoopGroup> eventLoopGroups = new LinkedList<>();
        Set<String> eventLoopGroupNames = new HashSet<>();

        List<ServerGroup> serverGroups = new LinkedList<>();

        List<ServerGroups> serverGroupss = new LinkedList<>();
        Set<String> serverGroupsNames = new HashSet<>();

        List<SecurityGroup> securityGroups = new LinkedList<>();
        Set<String> securityGroupNames = new HashSet<>();

        List<SmartGroupDelegate> smartGroupDelegates = new LinkedList<>();

        {
            // NOTE
            // NOTE: smart-service-delegate MUST not be saved
            // NOTE
        }
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
                    " timeout " + c.timeout + " period " + c.period + " up " + c.up + " down " + c.down +
                    " method " + sg.getMethod() + " event-loop-group " + sg.eventLoopGroup.alias;
                commands.add(cmd);
                serverGroups.add(sg);
                serverGroupsNames.add(name);
            }
        }
        {
            // create server-groups
            ServerGroupsHolder sgh = app.serverGroupsHolder;
            List<String> names = sgh.names();
            for (String name : names) {
                ServerGroups sgs;
                try {
                    sgs = sgh.get(name);
                } catch (NotFoundException e) {
                    assert Logger.lowLevelDebug("sgs not found " + name);
                    assert Logger.printStackTrace(e);
                    continue;
                }

                String cmd = "add server-groups " + sgs.alias;
                commands.add(cmd);
                serverGroupss.add(sgs);
                serverGroupsNames.add(name);
            }
        }
        {
            // attach group into groups
            for (ServerGroups sgs : serverGroupss) {
                for (ServerGroups.ServerGroupHandle sg : sgs.getServerGroups()) {
                    if (!serverGroupsNames.contains(sg.alias)) {
                        Logger.warn(LogType.IMPROPER_USE, "the sg " + sg.alias + " already removed");
                        continue;
                    }
                    String cmd = "add server-group " + sg.alias + " to server-groups " + sgs.alias + " weight " + sg.getWeight();
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
                        " network " + Utils.ipStr(r.ip) + "/" + Utils.maskInt(r.mask) +
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
                if (!serverGroupsNames.contains(tl.backends.alias)) {
                    Logger.warn(LogType.IMPROPER_USE, "the sgs " + tl.backends.alias + " already removed");
                    continue;
                }
                if (!securityGroupNames.contains(tl.securityGroup.alias) && !tl.securityGroup.alias.equals(SecurityGroup.defaultName)) {
                    Logger.warn(LogType.IMPROPER_USE, "the secg " + tl.securityGroup.alias + " already removed");
                    continue;
                }
                if (tl.certKeys != null) {
                    for (CertKey ck : tl.certKeys) {
                        if (!certKeyNames.contains(ck.alias)) {
                            Logger.warn(LogType.IMPROPER_USE, "the cert-key " + ck.alias + " already removed");
                            continue tl;
                        }
                    }
                }
                StringBuilder cmd = new StringBuilder("add tcp-lb " + tl.alias + " acceptor-elg " + tl.acceptorGroup.alias +
                    " event-loop-group " + tl.workerGroup.alias +
                    " address " + Utils.ipport(tl.bindAddress) + " server-groups " + tl.backends.alias +
                    " timeout " + tl.getTimeout() +
                    " in-buffer-size " + tl.getInBufferSize() + " out-buffer-size " + tl.getOutBufferSize() +
                    " protocol " + tl.protocol);
                if (!tl.securityGroup.alias.equals(SecurityGroup.defaultName)) {
                    cmd.append(" security-group ").append(tl.securityGroup.alias);
                }
                if (tl.certKeys != null) {
                    cmd.append(" cert-key ").append(tl.certKeys[0].alias);
                    for (int i = 1; i < tl.certKeys.length; ++i) {
                        cmd.append(",").append(tl.certKeys[i].alias);
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
                if (!serverGroupsNames.contains(socks5.backends.alias)) {
                    Logger.warn(LogType.IMPROPER_USE, "the sgs " + socks5.backends.alias + " already removed");
                    continue;
                }
                if (!securityGroupNames.contains(socks5.securityGroup.alias) && !socks5.securityGroup.alias.equals(SecurityGroup.defaultName)) {
                    Logger.warn(LogType.IMPROPER_USE, "the secg " + socks5.securityGroup.alias + " already removed");
                    continue;
                }
                String cmd = "add socks5-server " + socks5.alias + " acceptor-elg " + socks5.acceptorGroup.alias +
                    " event-loop-group " + socks5.workerGroup.alias +
                    " address " + Utils.ipport(socks5.bindAddress) + " server-groups " + socks5.backends.alias +
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
            // create smart-group-delegate
            SmartGroupDelegateHolder slgh = app.smartGroupDelegateHolder;
            List<String> names = slgh.names();
            for (String name : names) {
                SmartGroupDelegate s;
                try {
                    s = slgh.get(name);
                } catch (NotFoundException e) {
                    assert Logger.lowLevelDebug("smart-group-delegate not found " + name);
                    assert Logger.printStackTrace(e);
                    continue;
                }
                if (!serverGroups.contains(s.handledGroup)) {
                    Logger.warn(LogType.IMPROPER_USE, "the sg " + s.handledGroup.alias + " already removed");
                    continue;
                }
                String cmd = "add smart-group-delegate " + s.alias +
                    " service " + s.service + " zone " + s.zone +
                    " server-group " + s.handledGroup.alias;
                commands.add(cmd);
                smartGroupDelegates.add(s);
            }
        }
        {
            // create server
            for (ServerGroup sg : serverGroups) {
                if (smartGroupDelegates.stream().anyMatch(s -> s.handledGroup.equals(sg))) {
                    // do not init servers if it's attached to a smartGroupDelegate
                    continue;
                }
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
        StringBuilder sb = new StringBuilder();
        for (String cmd : commands) {
            sb.append(cmd).append("\n");
        }
        return sb.toString();
    }

    @Blocking // the reading file process is blocking
    public static void load(String filepath, Callback<String, Throwable> cb) throws Exception {
        if (Config.configLoadingDisabled) {
            throw new UnsupportedOperationException("loading is disabled");
        }
        if (filepath == null) {
            filepath = defaultFilePath();
        }
        if (filepath.startsWith("~")) {
            filepath = System.getProperty("user.home") + filepath.substring("~".length());
        }
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
