package vproxy.component.exception;

public class AlreadyExistException extends Exception {
    public AlreadyExistException() {
    }

    public AlreadyExistException(String msg) {
        super(msg);
    }
}
