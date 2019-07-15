package vproxy.util;

import java.util.Date;

public class Logger {
    private static final boolean lowLevelDebugOn;
    private static final boolean lowLevelNetDebugOn;

    private static final String DEBUG_COLOR = "\033[0;36m";
    private static final String INFO_COLOR = "\033[0;32m";
    private static final String WARN_COLOR = "\033[0;33m";
    private static final String ERROR_COLOR = "\033[0;31m";
    private static final String RESET_COLOR = "\033[0m";

    static {
        {
            String debug = System.getProperty("vproxy.debug");
            lowLevelDebugOn = !"off".equals(debug);
        }

        {
            String debug = System.getProperty("javax.net.debug");
            lowLevelNetDebugOn = "all".equals(debug) || "vproxy".equals(debug);
        }
    }

    private Logger() {
    }

    private static String fillToTen(int n) {
        return (n < 10 ? "0" : "") + n;
    }

    private static String fillToHundred(int n) {
        return (n < 10 ? "00" : (n < 100 ? "0" : "")) + n;
    }

    @SuppressWarnings("deprecation")
    private static String current() {
        long cur = System.currentTimeMillis();
        Date d = new Date(cur);
        return "[" +
            (d.getYear() + 1900) + "-" +
            fillToTen(d.getMonth() + 1) + "-" +
            fillToTen(d.getDate()) + " " +
            fillToTen(d.getHours()) + ":" +
            fillToTen(d.getMinutes()) + ":" +
            fillToTen(d.getSeconds()) + "." +
            fillToHundred((int) (cur % 1000)) +
            "] ";
    }

    // some message for debugging this project
    // use assert to print this log
    // e.g. assert Logger.lowLevelDebug("i will not be here in production environment")
    public static boolean lowLevelDebug(String msg) {
        if (!lowLevelDebugOn)
            return true;
        String threadName = Thread.currentThread().getName();
        StackTraceElement elem = Thread.currentThread().getStackTrace()[2];
        System.out.println(DEBUG_COLOR + current() + threadName + " - " + elem.getClassName() + "#" + elem.getMethodName() + "(" + elem.getLineNumber() + ") - " + RESET_COLOR + msg);
        return true;
    }

    public static boolean lowLevelNetDebug(String msg) {
        if (!lowLevelNetDebugOn || !lowLevelDebugOn)
            return true;
        String threadName = Thread.currentThread().getName();
        StackTraceElement elem = Thread.currentThread().getStackTrace()[2];
        System.out.println(DEBUG_COLOR + current() + threadName + " - " + elem.getClassName() + "#" + elem.getMethodName() + "(" + elem.getLineNumber() + ") - " + RESET_COLOR + msg);
        return true;
    }

    private static void privateErr(String err) {
        String threadName = Thread.currentThread().getName();
        StackTraceElement elem = Thread.currentThread().getStackTrace()[3];
        System.out.println(ERROR_COLOR + current() + threadName + " - " + elem.getClassName() + "#" + elem.getMethodName() + "(" + elem.getLineNumber() + ") - " + RESET_COLOR + err);
    }

    // unexpected errors, or situation should happen
    public static void fatal(LogType logType, String err) {
        privateErr(logType + " - " + err);
    }

    public static void fatal(LogType logType, String err, Throwable ex) {
        privateErr(logType + " - " + err);
        ex.printStackTrace(System.out);
    }

    // expected errors, but not normal condition
    public static void error(LogType logType, String err) {
        privateErr(logType + " - " + err);
    }

    public static void error(LogType logType, String err, Throwable ex) {
        privateErr(logType + " - " + err);
        ex.printStackTrace(System.out);
    }

    // expected errors, maybe user misuse, and we can recover
    public static void warn(LogType logType, String err) {
        System.out.println(WARN_COLOR + current() + logType + " - " + RESET_COLOR + err);
    }

    // expected condition
    public static void info(LogType logType, String msg) {
        System.out.println(INFO_COLOR + current() + logType + " - " + RESET_COLOR + msg);
    }

    public static void shouldNotHappen(String msg) {
        fatal(LogType.UNEXPECTED, "should not happen - " + msg);
    }

    public static void alert(String msg) {
        info(LogType.ALERT, msg);
    }

    public static void shouldNotHappen(String msg, Throwable err) {
        fatal(LogType.UNEXPECTED, "should not happen - " + msg, err);
    }

    public static boolean printStackTrace(Throwable t) {
        t.printStackTrace(System.out);
        return true;
    }
}
