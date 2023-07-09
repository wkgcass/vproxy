package io.vproxy.base.util.log;

import io.vproxy.base.util.LogType;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

public class LogRecord {
    public final String threadName; // nullable
    public final StackTraceElement stackInfo; // nullable
    public final LogLevel level;
    public final LogType type; // nullable
    public final long ts;
    public final String content;
    public final Throwable ex;

    private String generatedColorString;
    private String generatedPlainString;
    private String fullStackTrace;
    private String simpleStackTrace;

    public LogRecord(String threadName, StackTraceElement stackInfo, LogLevel level, LogType type, long ts, String content, Throwable ex) {
        this.threadName = threadName;
        this.stackInfo = stackInfo;
        this.level = level;
        this.type = type;
        this.ts = ts;
        this.content = content;
        this.ex = ex;
    }

    @Override
    public String toString() {
        return toColoredString();
    }

    public String toColoredString() {
        return toColoredString(false);
    }

    public String toColoredString(boolean stackTraceOn) {
        if (generatedColorString == null) {
            generatedColorString = toString(true);
        }
        return generatedColorString + exToString(stackTraceOn);
    }

    public String toStringNoColor() {
        return toStringNoColor(false);
    }

    public String toStringNoColor(boolean stackTraceOn) {
        if (generatedPlainString == null) {
            generatedPlainString = toString(false);
        }
        return generatedPlainString + exToString(stackTraceOn);
    }

    private String toString(boolean withColor) {
        var sb = new StringBuilder();
        if (withColor) {
            sb.append(level.color);
        }
        sb.append(timeString(ts));
        sb.append("[").append(level).append("] - ");
        if (threadName != null) {
            sb.append(threadName);
            sb.append(" - ");
            if (stackInfo != null) {
                sb.append(stackInfo.getClassName()).append("#").append(stackInfo.getMethodName()).append("(").append(stackInfo.getLineNumber()).append(") - ");
            }
        }
        if (type != null) {
            sb.append(type).append(" - ");
        }
        if (withColor) {
            sb.append(LogLevel.RESET_COLOR);
        }
        sb.append(content);
        return sb.toString();
    }

    private static String fillToTen(int n) {
        return (n < 10 ? "0" : "") + n;
    }

    private static String fillToHundred(int n) {
        return (n < 10 ? "00" : (n < 100 ? "0" : "")) + n;
    }

    @SuppressWarnings("deprecation")
    private static String timeString(long cur) {
        Date d = new Date(cur);
        return "[" +
            (d.getYear() + 1900) + "-" +
            fillToTen(d.getMonth() + 1) + "-" +
            fillToTen(d.getDate()) + " " +
            fillToTen(d.getHours()) + ":" +
            fillToTen(d.getMinutes()) + ":" +
            fillToTen(d.getSeconds()) + "." +
            fillToHundred((int) (cur % 1000)) +
            "]";
    }

    private String exToString(boolean stackTraceOn) {
        if (ex == null)
            return "";
        if (stackTraceOn || ex instanceof RuntimeException) {
            if (fullStackTrace == null) {
                fullStackTrace = "\n" + privateFormatStackTrace(true, ex);
            }
            return fullStackTrace;
        } else {
            if (simpleStackTrace == null) {
                simpleStackTrace = "\n" + privateFormatStackTrace(false, ex);
            }
            return simpleStackTrace;
        }
    }

    private static String privateFormatStackTrace(boolean stackTraceOn, Throwable t) {
        if (stackTraceOn || !(t instanceof Exception) || (t instanceof RuntimeException)) { // always print runtime exceptions and errors
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            return sw.toString();
        } else {
            StringBuilder sb = new StringBuilder();
            formatExceptionStackTrace(t, sb);
            return sb.toString();
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
}
