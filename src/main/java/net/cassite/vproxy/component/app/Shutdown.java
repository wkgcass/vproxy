package net.cassite.vproxy.component.app;

import net.cassite.vproxy.app.*;
import net.cassite.vproxy.app.cmd.CmdResult;
import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.component.check.HealthCheckConfig;
import net.cassite.vproxy.component.elgroup.EventLoopGroup;
import net.cassite.vproxy.component.elgroup.EventLoopWrapper;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.component.secure.SecurityGroup;
import net.cassite.vproxy.component.secure.SecurityGroupRule;
import net.cassite.vproxy.component.svrgroup.ServerGroup;
import net.cassite.vproxy.component.svrgroup.ServerGroups;
import net.cassite.vproxy.util.*;
import sun.misc.Signal;

import java.io.*;
import java.util.*;

public class Shutdown {
    private Shutdown() {
    }

    private static boolean initiated = false;
    private static int sigIntTimes = 0;

    public static void init() {
        if (initiated)
            return;
        initiated = true;
        try {
            Signal.handle(new Signal("INT"), s -> {
                ++sigIntTimes;
                if (sigIntTimes > 2) {
                    sigIntTimes = -10000; // set to a very small number to prevent triggered multiple times
                    saveAndQuit(128 + 2);
                } else {
                    System.out.println("press ctrl-c more times to quit");
                }
            });
            System.out.println("SIGINT handled");
        } catch (Exception e) {
            System.err.println("SIGINT not handled");
        }
        try {
            Signal.handle(new Signal("HUP"), s -> saveAndQuit(128 + 1));
            System.out.println("SIGHUP handled");
        } catch (Exception e) {
            System.err.println("SIGHUP not handled");
        }
        new Thread(() -> {
            //noinspection InfiniteLoopStatement
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
        saveAndQuit(0);
    }

    private static void saveAndQuit(int exitCode) {
        try {
            save(null);
        } catch (Exception e) {
            Logger.shouldNotHappen("save failed", e);
        }
        System.exit(exitCode);
    }

    public static String defaultFilePath() {
        return System.getProperty("user.home") + File.separator + ".vproxy.last";
    }

    @Blocking // writing file is blocking
    public static void save(String filepath) throws Exception {
        if (filepath == null) {
            filepath = defaultFilePath();
        }
        if (filepath.startsWith("~")) {
            filepath = System.getProperty("user.home") + filepath.substring("~".length());
        }
        File f = new File(filepath);
        File bakF = new File(filepath + ".bak");
        if (bakF.exists() && !bakF.delete()) {
            throw new Exception("remove old backup file failed");
        }
        if (f.exists() && !f.renameTo(bakF)) {
            throw new Exception("backup old file failed");
        }
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

        List<EventLoopGroup> eventLoopGroups = new LinkedList<>();
        Set<String> eventLoopGroupNames = new HashSet<>();

        List<ServerGroup> serverGroups = new LinkedList<>();

        List<ServerGroups> serverGroupss = new LinkedList<>();
        Set<String> serverGroupsNames = new HashSet<>();

        List<SecurityGroup> securityGroups = new LinkedList<>();
        Set<String> securityGroupNames = new HashSet<>();

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

                String cmd = "add event-loop-group " + elg.alias;
                commands.add(cmd);
                eventLoopGroups.add(elg);
                eventLoopGroupNames.add(name);
            }
        }
        {
            // create event-loop
            for (EventLoopGroup elg : eventLoopGroups) {
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
            // create server
            for (ServerGroup sg : serverGroups) {
                for (ServerGroup.ServerHandle sh : sg.getServerHandles()) {
                    String cmd = "add server " + sh.alias + " to server-group " + sg.alias +
                        " address " + Utils.ipStr(sh.server.getAddress().getAddress()) + ":" + sh.server.getPort() +
                        " via " + Utils.ipStr(sh.local.getAddress()) + " weight " + sh.getWeight();
                    commands.add(cmd);
                }
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
                for (ServerGroup sg : sgs.getServerGroups()) {
                    if (!serverGroupsNames.contains(sg.alias)) {
                        Logger.warn(LogType.IMPROPER_USE, "the sg " + sg.alias + " already removed");
                        continue;
                    }
                    String cmd = "add server-group " + sg.alias + " to server-groups " + sgs.alias;
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
                String cmd = "add tcp-lb " + tl.alias + " acceptor-elg " + tl.acceptorGroup.alias +
                    " event-loop-group " + tl.workerGroup.alias +
                    " address " + tl.server.id() + " server-groups " + tl.backends.alias +
                    " in-buffer-size " + tl.inBufferSize + " out-buffer-size " + tl.outBufferSize;
                if (!tl.securityGroup.alias.equals(SecurityGroup.defaultName)) {
                    cmd += " security-group " + tl.securityGroup.alias;
                }
                commands.add(cmd);
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
        cmd.run(new Callback<CmdResult, Throwable>() {
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
