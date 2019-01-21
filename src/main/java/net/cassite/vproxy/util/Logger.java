package net.cassite.vproxy.util;

public class Logger {
    private Logger() {
    }

    // some message for debugging this project
    // use assert to print this log
    // e.g. assert Logger.lowLevelDebug("i will not be here in production environment")
    public static boolean lowLevelDebug(String msg) {
        String threadName = Thread.currentThread().getName();
        StackTraceElement elem = Thread.currentThread().getStackTrace()[2];
        System.out.println(threadName + " - " + elem.getClassName() + "#" + elem.getMethodName() + "(" + elem.getLineNumber() + ") - " + msg);
        return true;
    }

    public static void stdout(String msg) {
        System.out.println(msg);
    }

    private static void privateStderr(String err) {
        String threadName = Thread.currentThread().getName();
        StackTraceElement elem = Thread.currentThread().getStackTrace()[3];
        System.err.println(threadName + " - " + elem.getClassName() + "#" + elem.getMethodName() + "(" + elem.getLineNumber() + ") - " + err);
    }

    public static void stderr(String err) {
        privateStderr(err);
    }

    // unexpected errors, or situation should happen
    public static void fatal(LogType logType, String err) {
        privateStderr(logType + " - " + err);
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
        System.err.println(logType + " - " + err);
    }

    // expected condition
    public static void info(LogType logType, String msg) {
        System.out.println(logType + " - " + msg);
    }

    public static void shouldNotHappen(String msg) {
        fatal(LogType.UNEXPECTED, "should not happen - " + msg);
    }
}
