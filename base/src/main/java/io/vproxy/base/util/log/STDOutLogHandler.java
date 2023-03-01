package io.vproxy.base.util.log;

public class STDOutLogHandler implements LogHandler {
    private final boolean stackTraceOn;

    public STDOutLogHandler(boolean stackTraceOn) {
        this.stackTraceOn = stackTraceOn;
    }

    @Override
    public void publish(LogRecord record) {
        System.out.println(record.toColoredString(stackTraceOn));
    }
}
