package io.vproxy.base.util.log;

public enum LogLevel {
    PROBE("\033[0;36m"),
    DEBUG("\033[0;36m"),
    TRACE("\033[0;36m"),
    INFO("\033[0;32m"),
    WARN("\033[0;33m"),
    ERROR("\033[0;31m"),
    FATAL("\033[0;31m"),
    ;
    public static final String RESET_COLOR = "\033[0m";

    public final String color;

    LogLevel(String color) {
        this.color = color;
    }
}
