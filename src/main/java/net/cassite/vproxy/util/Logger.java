package net.cassite.vproxy.util;

public class Logger {
    private Logger() {
    }

    public static void lowLevelDebug(String msg) {
        System.out.println(msg);
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

    public static void shouldNotHappen(String msg) {
        privateStderr("should not happen - " + msg);
    }
}
