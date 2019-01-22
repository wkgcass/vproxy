package net.cassite.vproxy.component.app;

import sun.misc.Signal;

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
}
