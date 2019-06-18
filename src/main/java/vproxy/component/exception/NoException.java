package vproxy.component.exception;

public class NoException extends RuntimeException {
    private NoException() {
        // should not be constructed
        throw new RuntimeException();
    }
}
