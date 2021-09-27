package io.vproxy.base.util.exception;

public class NotFoundException extends Exception {
    public NotFoundException(String resourceType, String name) {
        super("Cannot find " + resourceType + " with name " + name + ".");
    }

    public NotFoundException(String msg) {
        super(msg);
    }
}
