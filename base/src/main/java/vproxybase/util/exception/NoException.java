package vproxybase.util.exception;

public class NoException extends RuntimeException {
    private NoException() {
        // should not be constructed
        throw new RuntimeException();
    }
}
