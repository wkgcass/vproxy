package vproxy.util;

import vfd.DatagramFD;
import vfd.FDProvider;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Date;

public class Logger {
    private static final boolean stackTraceOn;
    private static final boolean lowLevelDebugOn;
    private static final boolean lowLevelNetDebugOn;

    public static final String DEBUG_COLOR = "\033[0;36m";
    public static final String INFO_COLOR = "\033[0;32m";
    public static final String WARN_COLOR = "\033[0;33m";
    public static final String ERROR_COLOR = "\033[0;31m";
    public static final String RESET_COLOR = "\033[0m";

    private static DatagramFD logChannel;

    static {
        {
            String debug = System.getProperty("vproxy.debug");
            lowLevelDebugOn = !"off".equals(debug);
        }

        {
            String debug = System.getProperty("javax.net.debug");
            lowLevelNetDebugOn = "all".equals(debug) || "vproxy".equals(debug);
        }

        {
            if (lowLevelDebugOn && Utils.assertOn()) {
                stackTraceOn = true;
            } else {
                String stackTrace = System.getProperty("vproxy.stacktrace", "off");
                stackTraceOn = !"off".equals(stackTrace);
            }
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
        long cur = FDProvider.get().currentTimeMillis();
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

    private static StackTraceElement getFirstElementOutOfLoggerLib() {
        final String loggerClass = Logger.class.getName();
        var arr = Thread.currentThread().getStackTrace();
        boolean intoLoggerLib = false;
        for (StackTraceElement e : arr) {
            if (e.getClassName().equals(loggerClass)) {
                intoLoggerLib = true;
            } else {
                if (intoLoggerLib) {
                    // out of logger lib now
                    return e;
                }
            }
        }
        // should not reach here, however return the last element if it really happens
        return arr[arr.length - 1];
    }

    // some message for debugging this project
    // use assert to print this log
    // e.g. assert Logger.lowLevelDebug("i will not be here in production environment")
    public static boolean lowLevelDebug(String msg) {
        if (!lowLevelDebugOn)
            return true;
        String threadName = Thread.currentThread().getName();
        StackTraceElement elem = getFirstElementOutOfLoggerLib();
        System.out.println(DEBUG_COLOR + current() + threadName + " - " + elem.getClassName() + "#" + elem.getMethodName() + "(" + elem.getLineNumber() + ") - " + RESET_COLOR + msg);
        return true;
    }

    public static boolean lowLevelNetDebug(String msg) {
        if (!lowLevelNetDebugOn || !lowLevelDebugOn)
            return true;
        String threadName = Thread.currentThread().getName();
        StackTraceElement elem = getFirstElementOutOfLoggerLib();
        System.out.println(DEBUG_COLOR + current() + threadName + " - " + elem.getClassName() + "#" + elem.getMethodName() + "(" + elem.getLineNumber() + ") - " + RESET_COLOR + msg);
        return true;
    }

    private static void privateErr(String err) {
        String threadName = Thread.currentThread().getName();
        StackTraceElement elem = getFirstElementOutOfLoggerLib();
        System.out.println(ERROR_COLOR + current() + threadName + " - " + elem.getClassName() + "#" + elem.getMethodName() + "(" + elem.getLineNumber() + ") - " + RESET_COLOR + err);
    }

    private static void privatePrintStackTrace(Throwable t) {
        if (stackTraceOn || !(t instanceof Exception) || (t instanceof RuntimeException)) { // always print runtime exceptions and errors
            t.printStackTrace(System.out);
        } else {
            StringBuilder sb = new StringBuilder();
            formatExceptionStackTrace(t, sb);
            System.out.println(sb.toString());
        }
    }

    private static void formatExceptionStackTrace(Throwable t, StringBuilder sb) {
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            sb.append(t.getClass().getName());
        } else {
            sb.append(t.getClass().getName()).append(": ").append(msg.trim());
        }
        if (t.getCause() != null) {
            sb.append(" <= ");
            formatExceptionStackTrace(t.getCause(), sb);
        }
    }

    // unexpected errors, or situation should happen
    public static void fatal(LogType logType, String err) {
        privateErr(logType + " - " + err);
    }

    public static void fatal(LogType logType, String err, Throwable ex) {
        privateErr(logType + " - " + err);
        privatePrintStackTrace(ex);
    }

    // expected errors, but not normal condition
    public static void error(LogType logType, String err) {
        privateErr(logType + " - " + err);
    }

    public static void error(LogType logType, String err, Throwable ex) {
        privateErr(logType + " - " + err);
        privatePrintStackTrace(ex);
    }

    // expected errors, maybe user misuse, and we can recover
    public static void warn(LogType logType, String err) {
        System.out.println(WARN_COLOR + current() + logType + " - " + RESET_COLOR + err);
    }

    public static void warn(LogType logType, String err, Throwable t) {
        System.out.println(WARN_COLOR + current() + logType + " - " + RESET_COLOR + err);
        privatePrintStackTrace(t);
    }

    // expected condition
    public static void info(LogType logType, String msg) {
        System.out.println(INFO_COLOR + current() + logType + " - " + RESET_COLOR + msg);
    }

    public static void trace(LogType logType, String msg) {
        System.out.println(DEBUG_COLOR + current() + logType + " - " + RESET_COLOR + msg);
    }

    public static void shouldNotHappen(String msg) {
        fatal(LogType.UNEXPECTED, "should not happen - " + msg);
    }

    public static void alert(String msg) {
        info(LogType.ALERT, msg);
    }

    public static void probe(String msg) {
        String threadName = Thread.currentThread().getName();
        String log = DEBUG_COLOR + current() + threadName + " " + LogType.PROBE + " - " + RESET_COLOR + msg + "\r\n";
        DatagramFD chnl = getLogChannel();
        try {
            chnl.send(ByteBuffer.wrap(log.getBytes()), getLogAddress());
        } catch (IOException e) {
            Logger.shouldNotHappen("sending log message failed", e);
        }
    }

    private static DatagramFD getLogChannel() {
        if (logChannel == null) {
            try {
                logChannel = FDProvider.get().openDatagramFD();
                logChannel.configureBlocking(false);
            } catch (IOException e) {
                shouldNotHappen("open datagram channel as log channel failed", e);
                if (logChannel != null) {
                    try {
                        logChannel.close();
                    } catch (IOException ignore) {
                    }
                }
                logChannel = null;
                throw new RuntimeException(e);
            }
        }
        if (!logChannel.isOpen()) {
            try {
                logChannel.close();
            } catch (IOException ignore) {
            }
            logChannel = null;
            return getLogChannel();
        }
        return logChannel;
    }

    private static InetSocketAddress getLogAddress() {
        return new InetSocketAddress(Utils.l3addr(127, 0, 0, 1), 23456);
    }

    public static void shouldNotHappen(String msg, Throwable err) {
        fatal(LogType.UNEXPECTED, "should not happen - " + msg, err);
    }

    public static boolean printStackTrace(Throwable t) {
        t.printStackTrace(System.out); // do not use privatePrintStackTrace, always print here
        return true;
    }

    public static boolean lowLevelNetDebugPrintBytes(byte[] array) {
        if (lowLevelNetDebugOn) {
            printBytes(array);
        }
        return true;
    }

    public static boolean lowLevelNetDebugPrintBytes(byte[] array, int off, int end) {
        if (lowLevelNetDebugOn) {
            printBytes(array, off, end);
        }
        return true;
    }

    public static void printBytes(byte[] array) {
        printBytes(array, 0, array.length);
    }

    public static void printBytes(byte[] array, int off, int end) {
        {
            byte[] tmp = new byte[end - off];
            System.arraycopy(array, off, tmp, 0, end - off);
            array = tmp;
        }

        final int bytesPerLine = 36;
        int lastLine = array.length % bytesPerLine;
        if (lastLine == 0) {
            lastLine = bytesPerLine;
        }
        int lines = array.length / bytesPerLine + (lastLine != bytesPerLine ? 1 : 0);
        byte[][] linesArray = new byte[lines][];
        for (int i = 0; i < linesArray.length - 1; ++i) {
            linesArray[i] = new byte[bytesPerLine];
        }
        linesArray[linesArray.length - 1] = new byte[lastLine];

        for (int i = 0; i < array.length; ++i) {
            int idx0 = i / bytesPerLine;
            int idx1 = i % bytesPerLine;
            linesArray[idx0][idx1] = array[i];
        }

        for (int idx = 0; idx < linesArray.length; idx++) {
            byte[] line = linesArray[idx];
            System.out.print(Utils.bytesToHex(line));
            System.out.print("    ");
            if (idx == linesArray.length - 1) {
                for (int i = 0; i < 2 * (bytesPerLine - lastLine); ++i) {
                    System.out.print(" ");
                }
            }
            char[] cs = new char[line.length];
            for (int i = 0; i < line.length; ++i) {
                int b = line[i];
                if (b < 0) {
                    b += 256;
                }
                if (b < 32 || b > 126) {
                    cs[i] = '.';
                } else {
                    cs[i] = (char) b;
                }
            }
            System.out.println(new String(cs));
        }
    }

    public static char toPrintableChar(byte bb) {
        int b = bb;
        if (b < 0) {
            b += 256;
        }
        if (b < 32 || b > 126) {
            return '.';
        } else {
            return (char) b;
        }
    }
}
