package net.cassite.vproxy.util;

import java.util.Date;

public class Logger {
    private Logger() {
    }

    @SuppressWarnings("deprecation")
    private static String current() {
        long cur = System.currentTimeMillis();
        Date d = new Date(cur);
        return "[" + d.getHours() + ":" + d.getMinutes() + ":" + d.getSeconds() + "." + (cur % 1000) + "]";
    }

    // some message for debugging this project
    // use assert to print this log
    // e.g. assert Logger.lowLevelDebug("i will not be here in production environment")
    public static boolean lowLevelDebug(String msg) {
        String threadName = Thread.currentThread().getName();
        StackTraceElement elem = Thread.currentThread().getStackTrace()[2];
        System.out.println(current() + threadName + " - " + elem.getClassName() + "#" + elem.getMethodName() + "(" + elem.getLineNumber() + ") - " + msg);
        return true;
    }

    public static boolean lowLevelNetDebug(String msg) {
        String debug = System.getProperty("javax.net.debug");
        if (debug == null || debug.equals("all")) {
            return true;
        }
        String threadName = Thread.currentThread().getName();
        StackTraceElement elem = Thread.currentThread().getStackTrace()[2];
        System.out.println(current() + threadName + " - " + elem.getClassName() + "#" + elem.getMethodName() + "(" + elem.getLineNumber() + ") - " + msg);
        return true;
    }

    public static void stdout(String msg) {
        System.out.println(current() + msg);
    }

    private static void privateStderr(String err) {
        String threadName = Thread.currentThread().getName();
        StackTraceElement elem = Thread.currentThread().getStackTrace()[3];
        System.err.println(current() + threadName + " - " + elem.getClassName() + "#" + elem.getMethodName() + "(" + elem.getLineNumber() + ") - " + err);
    }

    public static void stderr(String err) {
        privateStderr(err);
    }

    // unexpected errors, or situation should happen
    public static void fatal(LogType logType, String err) {
        privateStderr(logType + " - " + err);
    }

    public static void fatal(LogType logType, String err, Throwable ex) {
        privateStderr(logType + " - " + err);
        ex.printStackTrace(System.err);
    }

    // expected errors, but not normal condition
    public static void error(LogType logType, String err) {
        privateStderr(logType + " - " + err);
    }

    public static void error(LogType logType, String err, Throwable ex) {
        privateStderr(logType + " - " + err);
        ex.printStackTrace(System.err);
    }

    // expected errors, maybe user misuse, and we can recover
    public static void warn(LogType logType, String err) {
        System.err.println(current() + logType + " - " + err);
    }

    // expected condition
    public static void info(LogType logType, String msg) {
        System.out.println(current() + logType + " - " + msg);
    }

    public static void shouldNotHappen(String msg) {
        fatal(LogType.UNEXPECTED, "should not happen - " + msg);
    }

    public static void alert(String msg) {
        info(LogType.ALERT, msg);
    }

    public static void shouldNotHappen(String msg, Throwable err) {
        fatal(LogType.UNEXPECTED, "should not happen - " + msg);
        err.printStackTrace();
    }

    public static boolean printStackTrace(Throwable t) {
        t.printStackTrace();
        return true;
    }
}
