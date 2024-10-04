package io.vproxy.base.util.exception;

public class InUseException extends Exception {
    public InUseException(String msg) {
        super(msg);
    }

    public InUseException(String type, String name, String requiredType, String requiredName) {
        this(type + " " + name + " is still required by " + requiredType + " " + requiredName);
    }
}
