package io.vproxy.base.processor;

public class ExceptionWithoutStackTrace extends Exception {
    public ExceptionWithoutStackTrace(String msg) {
        super(msg, null, false, false);
    }
}
