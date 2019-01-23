package net.cassite.vproxy.component.app;

import net.cassite.vproxy.app.cmd.Command;
import net.cassite.vproxy.util.Blocking;
import net.cassite.vproxy.util.Callback;
import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;
import sun.misc.Signal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

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
                    saveAndQuit(null, 128 + 2);
                } else {
                    System.out.println("press ctrl-c more times to quit");
                }
            });
            System.out.println("SIGINT handled");
        } catch (Exception e) {
            System.err.println("SIGINT not handled");
        }
        try {
            Signal.handle(new Signal("HUP"), s -> saveAndQuit(null, 128 + 1));
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
        // TODO
        saveAndQuit(null, 0);
    }

    private static void saveAndQuit(String filepath, int exitCode) {
        save(filepath);
        System.exit(exitCode);
    }

    public static void save(String filepath) {
        // TODO
    }

    @Blocking // the reading file process is blocking
    public static void load(String filepath, Callback<String, Throwable> cb) throws Exception {
        if (filepath == null)
            throw new Exception("filepath not specified");
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
        cmd.run(new Callback<String, Throwable>() {
            @Override
            protected void onSucceeded(String value) {
                runCommandsOnLoading(commands, idx + 1, cb);
            }

            @Override
            protected void onFailed(Throwable err) {
                cb.failed(err);
            }
        });
    }
}
