package io.vproxy.base.util;

import io.vproxy.base.util.log.LogDispatcher;
import io.vproxy.base.util.log.LogLevel;
import io.vproxy.base.util.log.LogRecord;
import io.vproxy.base.util.log.STDOutLogHandler;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.vfd.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.regex.Pattern;

public class Logger {
    public static final boolean stackTraceOn;
    public static final boolean lowLevelDebugOn;
    public static final boolean lowLevelNetDebugOn;

    public static final LogDispatcher logDispatcher = new LogDispatcher();
    private static final STDOutLogHandler stdoutLogHandler;
    private static DatagramFD logChannel;

    static {
        {
            String debug = Utils.getSystemProperty("debug");
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
                String stackTrace = Utils.getSystemProperty("stacktrace", "off");
                stackTraceOn = !"off".equals(stackTrace);
            }
        }

        {
            stdoutLogHandler = new STDOutLogHandler(stackTraceOn);
            logDispatcher.addLogHandler(stdoutLogHandler);
        }
    }

    public static boolean debugOn() {
        return Utils.assertOn() && (lowLevelDebugOn || lowLevelNetDebugOn);
    }

    private Logger() {
    }

    private static final Pattern loggerPattern1 = Pattern.compile(".*Logger\\b.*");

    private static StackTraceElement getFirstElementOutOfLoggerLib() {
        var arr = Thread.currentThread().getStackTrace();
        boolean intoLoggerLib = false;
        for (StackTraceElement e : arr) {
            var cls = e.getClassName();
            if (loggerPattern1.matcher(cls).matches()
                || cls.equals("io.vproxy.adaptor.vertx.VProxyLogDelegate")
                || cls.equals("io.vertx.core.impl.logging.LoggerAdapter")
            ) {
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

    private static final Set<String> ADD_DEBUG_INFO_CLASS_PREFIX = Set.of(
        "io.vproxy.vswitch."
    );

    // some message for debugging this project
    // use assert to print this log
    // e.g. assert Logger.lowLevelDebug("i will not be here in production environment")
    public static boolean lowLevelDebug(String msg) {
        if (!lowLevelDebugOn)
            return true;
        return debugLog(msg);
    }

    public static boolean lowLevelNetDebug(String msg) {
        if (!lowLevelNetDebugOn || !lowLevelDebugOn)
            return true;
        return debugLog(msg);
    }

    private static boolean debugLog(String msg) {
        String threadName = Thread.currentThread().getName();
        StackTraceElement elem = getFirstElementOutOfLoggerLib();
        var record = new LogRecord(
            threadName,
            elem,
            LogLevel.DEBUG,
            null,
            currentTimeMillis(),
            getDebugInfo(elem) + msg,
            null);
        logDispatcher.publish(record);
        return true;
    }

    private static long currentTimeMillis() {
        return FDProvider.get().currentTimeMillis();
    }

    private static String getDebugInfo(StackTraceElement elem) {
        String clsName = elem.getClassName();
        boolean addDebugInfo = false;
        for (String cls : ADD_DEBUG_INFO_CLASS_PREFIX) {
            if (clsName.startsWith(cls)) {
                addDebugInfo = true;
                break;
            }
        }
        if (addDebugInfo) {
            String debugInfo = VProxyThread.current().debugInfo;
            if (debugInfo != null && !debugInfo.isBlank()) {
                return debugInfo + " - ";
            }
        }
        return "";
    }

    private static void privateErr(LogLevel level, LogType type, String err, Throwable t) {
        String threadName = Thread.currentThread().getName();
        StackTraceElement elem = getFirstElementOutOfLoggerLib();
        var record = new LogRecord(
            threadName,
            elem,
            level,
            type,
            currentTimeMillis(),
            err,
            t
        );
        logDispatcher.publish(record);
    }

    // unexpected errors, or situation should happen
    public static void fatal(LogType logType, String err) {
        privateErr(LogLevel.FATAL, logType, err, null);
    }

    public static void fatal(LogType logType, String err, Throwable ex) {
        privateErr(LogLevel.FATAL, logType, err, ex);
    }

    // expected errors, but not normal condition
    public static void error(LogType logType, String err) {
        privateErr(LogLevel.ERROR, logType, err, null);
    }

    public static void error(LogType logType, String err, Throwable ex) {
        privateErr(LogLevel.ERROR, logType, err, ex);
    }

    // expected errors, maybe user misuse, and we can recover
    public static void warn(LogType logType, String msg) {
        var record = new LogRecord(null, null,
            LogLevel.WARN,
            logType,
            currentTimeMillis(),
            msg,
            null);
        logDispatcher.publish(record);
    }

    public static void warn(LogType logType, String err, Throwable t) {
        var record = new LogRecord(null, null,
            LogLevel.WARN,
            logType,
            currentTimeMillis(),
            err,
            t);
        logDispatcher.publish(record);
    }

    // expected condition
    public static void info(LogType logType, String msg) {
        var record = new LogRecord(null, null,
            LogLevel.INFO,
            logType,
            currentTimeMillis(),
            msg,
            null);
        logDispatcher.publish(record);
    }

    public static void trace(LogType logType, String msg) {
        var record = new LogRecord(null, null,
            LogLevel.TRACE,
            logType,
            currentTimeMillis(),
            msg,
            null);
        logDispatcher.publish(record);
    }

    public static void shouldNotHappen(String msg) {
        fatal(LogType.UNEXPECTED, "should not happen - " + msg);
    }

    public static void alert(String msg) {
        info(LogType.ALERT, msg);
    }

    public static void access(String msg) {
        info(LogType.ACCESS, msg);
    }

    public static void probe(String msg) {
        String threadName = Thread.currentThread().getName();
        var record = new LogRecord(threadName, null,
            LogLevel.PROBE,
            LogType.PROBE,
            currentTimeMillis(),
            msg,
            null);
        String log = record.toStringNoColor(stackTraceOn);
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

    private static IPPort getLogAddress() {
        return new IPPort(IP.from(new byte[]{127, 0, 0, 1}), 23456);
    }

    public static void shouldNotHappen(String msg, Throwable err) {
        fatal(LogType.UNEXPECTED, "should not happen - " + msg, err);
    }

    public static boolean printStackTrace(Throwable t) {
        t.printStackTrace(System.out);
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
            byte[] tmp = Utils.allocateByteArray(end - off);
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
            linesArray[i] = Utils.allocateByteArray(bytesPerLine);
        }
        linesArray[linesArray.length - 1] = Utils.allocateByteArray(lastLine);

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
