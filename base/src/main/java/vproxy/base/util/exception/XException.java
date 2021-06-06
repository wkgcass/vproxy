package vproxy.base.util.exception;

public class XException extends Exception {
    public XException(String msg) {
        super(msg);
    }

    public XException(String msg, Throwable t) {
        super(msg, t);
    }
}
