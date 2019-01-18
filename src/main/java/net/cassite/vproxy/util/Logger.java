package net.cassite.vproxy.util;

public class Logger {
    private Logger() {
    }

    // some message for debugging this project
    public static void lowLevelDebug(String msg) {
        StackTraceElement elem = Thread.currentThread().getStackTrace()[2];
        System.out.println(elem.getClassName() + "#" + elem.getMethodName() + "(" + elem.getLineNumber() + ") - " + msg);
    }

    public static void stdout(String msg) {
        System.out.println(msg);
    }

    private static void privateStderr(String err) {
        StackTraceElement elem = Thread.currentThread().getStackTrace()[3];
        System.err.println(elem.getClassName() + "#" + elem.getMethodName() + "(" + elem.getLineNumber() + ") - " + err);
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

    // expected errors, and we can recover
    public static void warn(LogType logType, String err) {
        System.err.println(logType + " - " + err);
    }

    // expected condition
    public static void info(LogType logType, String msg) {
        System.out.println(logType + " - " + msg);
    }

    public static void shouldNotHappen(String msg) {
        privateStderr("should not happen - " + msg);
    }
}
