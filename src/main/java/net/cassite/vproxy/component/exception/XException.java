package net.cassite.vproxy.component.exception;

public class XException extends Exception {
    public XException(String msg) {
        super(msg, null, false, false);
    }
}
