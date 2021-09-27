package io.vproxy.app.vproxyx;

import io.vproxy.app.app.util.SignalHook;
import io.vproxy.app.process.Shutdown;
import io.vproxy.base.Config;
import io.vproxy.base.connection.ServerSock;
import io.vproxy.base.util.*;
import vproxy.base.util.*;
import io.vproxy.base.util.thread.VProxyThread;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Daemon {
    private static String launchArgs;
    private static volatile Process process;
    private static boolean consideredStable = false;
    private static int aliveCount = 0;
    private static int deadCount = 0;
    private static final Object lock = new Object();
    private static final String flagAdded = "noStdIOController sigIntDirectlyShutdown";
    private static final String flagOnReloading = "noStartupBindCheck";
    private static final String flagCheck = "check";

    public static void main0(String[] args) throws Exception {
        for (String arg : args) {
            if (arg.equals("-h") || arg.equals("--help") || arg.equals("help")) {
                System.out.println("This program is built for systemd, " +
                    "however you can use this as a standalone process that watches and reloads the vproxy process.\n" +
                    "You need to specify full command that would be used to launch a vproxy process.\n" +
                    "re-run `--help` without `-Deploy=Daemon` to see the help page.\n" +
                    "Note: noStdIOController will always be added to the argument list.\n" +
                    "Note: it only works on linux or macos with REUSEPORT support. " +
                    "Try to run the program, the requirements will be checked before actually starting.\n" +
                    "Note: SIGUSR2 for reloading, SIGTERM for exiting, same as the vproxy main program. " +
                    "SIGHUP|SIGUSR1 are captured and ignored. " +
                    "SIGINT for directly shutdown, only use this signal when debugging.");
                return;
            } else if (arg.equals("version")) {
                System.out.println(Version.VERSION);
                return;
            }
        }

        // check platform
        {
            if (OS.isWindows()) {
                System.err.println("Daemon is not supported on Windows");
                Utils.exit(1);
                return;
            }
        }
        // check reuseport
        {
            if (!ServerSock.supportReusePort()) {
                System.err.println("Cannot run Daemon because REUSEPORT is not supported");
                return;
            }
        }

        // write pid
        String pidFilePath = Utils.getSystemProperty("pid_file");
        if (pidFilePath == null) {
            pidFilePath = Config.workingDirectoryFile("vproxy.daemon.pid");
        }
        Shutdown.writePid(pidFilePath);

        Logger.alert("Daemon launched: " + ProcessHandle.current().pid());

        StringBuilder sb = new StringBuilder();
        for (var arg : args) {
            sb.append(arg).append(" ");
        }
        sb.append(flagAdded);
        launchArgs = sb.toString();
        Logger.alert("The launch arguments are: " + launchArgs);

        SignalHook.getInstance().sigUsr2(Daemon::reload);
        SignalHook.getInstance().sigTerm(Daemon::stop);
        SignalHook.getInstance().sigHup(() -> {
        });
        SignalHook.getInstance().sigUsr1(() -> {
        });
        SignalHook.getInstance().sigInt(() -> {
            // only use sigInt for debugging.
            if (process != null) {
                process.destroy();
            }
            Utils.exit(128 + 2);
        });
        start(false);
        join();

        var exitCode = 128 + 15; // exit normally on sigterm
        Logger.alert("Daemon exits: " + exitCode + ", pid: " + ProcessHandle.current().pid());
        Utils.exit(exitCode);
    }

    private static void start(boolean isReload) throws Exception {
        var cmd = launchArgs + " " + (isReload ? flagOnReloading : "");
        var ls = new LinkedList<String>();
        for (var s : cmd.split(" ")) {
            s = s.trim();
            if (!s.isEmpty()) {
                ls.add(s);
            }
        }

        var checkExit = check(ls);
        if (checkExit != 0) {
            if (isReload) {
                Logger.error(LogType.ALERT, "check failed when reloading, exit code is: " + checkExit);
                return;
            } else {
                Logger.error(LogType.ALERT, "check failed when starting, exit code is: " + checkExit);
                Utils.exit(1);
                return;
            }
        }

        // launch
        process = new ProcessBuilder().command(ls).start();
        handleIO(process);
        if (process.isAlive()) {
            Logger.alert("new process is: " + process.pid());
        }
    }

    private static int check(List<String> cmd) throws Exception {
        var ls = new ArrayList<String>(cmd.size() + 1);
        ls.addAll(cmd);
        ls.add(flagCheck);
        var checkProcess = new ProcessBuilder().command(ls).start();
        try {
            assert false;
        } catch (AssertionError ignore) {
            handleIO(checkProcess);
        }
        try {
            while (checkProcess.isAlive()) {
                checkProcess.waitFor();
            }
            return checkProcess.exitValue();
        } finally {
            checkProcess.destroy();
        }
    }

    private static void handleIO(Process p) {
        var pid = "" + p.pid();
        var stdout = p.getInputStream();
        var stderr = p.getErrorStream();
        var stdoutReader = new BufferedReader(new InputStreamReader(stdout));
        var stderrReader = new BufferedReader(new InputStreamReader(stderr));

        printLoop(pid, stdoutReader, System.out, "stdout");
        printLoop(pid, stderrReader, System.err, "stderr");
    }

    private static void printLoop(String pid, BufferedReader reader, PrintStream print, String descr) {
        VProxyThread.create(() -> {
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    print.println(pid + " - " + line);
                }
            } catch (IOException ignore) {
            }
        }, "printLoop-" + descr).start();
    }

    private static void join() throws Exception {
        // use loop and sleep instead of waitFor()
        // because the process ref will be modified when receiving signal
        while (true) {
            synchronized (lock) {
                if (process == null) {
                    // is going to exit
                    consideredStable = false;
                    break;
                }
                if (!process.isAlive()) {
                    Logger.error(LogType.ALERT, "The sub process is dead, launch again");
                    ++deadCount;
                    consideredStable = false;
                    aliveCount = 0;

                    if (deadCount > 10) {
                        // dead too many times
                        process = null;
                        Utils.exit(1);
                        return;
                    }

                    start(false);
                } else {
                    ++aliveCount;
                    if (aliveCount > 20) {
                        consideredStable = true;
                        deadCount = 0;
                    }
                }
            }
            if (consideredStable) {
                Thread.sleep(1_000);
            } else {
                Thread.sleep(100);
            }
        }
    }

    private static void reload() {
        Logger.alert("Received SIGUSR2, reloading: " + launchArgs);
        synchronized (lock) {
            if (process == null) {
                // is going to exit, ignore this condition
                return;
            }
            if (!process.isAlive()) {
                // if not alive, simply start a new process
                // in the "join()" method
                // nothing to be handled
                return;
            }
            // 1. send USR1 to sub process to save file
            // 2. wait for a few milliseconds
            // 3. launch new process
            // 4. send USR2 to sub process to exit

            var oldPid = "" + process.pid();
            // step 1
            {
                Process usr1Process;
                try {
                    usr1Process = new ProcessBuilder().command("/bin/kill", "-USR1", oldPid).start();
                } catch (IOException e) {
                    Logger.error(LogType.ALERT, "send USR1 to " + oldPid + " failed, retry the reloading in 500 ms", e);
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignore) {
                    }
                    reload();
                    return;
                }
                while (usr1Process.isAlive()) {
                    try {
                        usr1Process.waitFor();
                    } catch (InterruptedException ignore) {
                    }
                }
                if (usr1Process.exitValue() != 0) {
                    Logger.error(LogType.ALERT, "send USR1 to " + oldPid + " failed, retry the reloading in 500 ms");
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignore) {
                    }
                    reload();
                    return;
                }
                usr1Process.destroy();
            }

            // step 2
            {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignore) {
                }
            }

            // step 3
            {
                try {
                    start(true);
                } catch (Exception e) {
                    Logger.error(LogType.ALERT, "start new process when reloading failed, retry the reloading in 500 ms", e);
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignore) {
                    }
                    reload();
                    return;
                }
            }

            // step 4
            {
                Process usr2Process;
                try {
                    usr2Process = new ProcessBuilder().command("/bin/kill", "-USR2", oldPid).start();
                } catch (IOException e) {
                    Logger.error(LogType.ALERT, "send USR2 to " + oldPid + " failed", e);
                    return;
                }
                while (usr2Process.isAlive()) {
                    try {
                        usr2Process.waitFor();
                    } catch (InterruptedException ignore) {
                    }
                }
                if (usr2Process.exitValue() != 0) {
                    Logger.error(LogType.ALERT, "send USR2 to " + oldPid + " failed");
                    return;
                }
                usr2Process.destroy();
            }
        }
    }

    private static void stop() {
        Logger.alert("Received SIGTERM, stopping ...");
        synchronized (lock) {
            if (process == null) { // already stopped, do nothing
                return;
            }
            if (!process.isAlive()) {
                process = null; // not alive, so set to null directly
                return;
            }
            var oldPid = "" + process.pid();
            Process termProcess;
            try {
                termProcess = new ProcessBuilder().command("/bin/kill", "-TERM", oldPid).start();
            } catch (IOException e) {
                Logger.error(LogType.ALERT, "send TERM to " + oldPid + " failed", e);
                termProcess = null;
            }
            if (termProcess != null) {
                while (termProcess.isAlive()) {
                    try {
                        termProcess.waitFor();
                    } catch (InterruptedException ignore) {
                    }
                }
                if (termProcess.exitValue() != 0) {
                    Logger.error(LogType.ALERT, "send TERM to " + oldPid + " failed");
                }
                termProcess.destroy();
            }
            while (process.isAlive()) {
                try {
                    process.waitFor();
                } catch (InterruptedException ignore) {
                }
            }
            process.destroy();
            process = null;
        }
    }
}
