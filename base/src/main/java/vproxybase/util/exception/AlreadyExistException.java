package vproxybase.util.exception;

public class AlreadyExistException extends Exception {
    public AlreadyExistException(String resourceType, String name) {
        this("Duplicated entry for " + resourceType + " with " + name + ".");
    }

    public AlreadyExistException(String msg) {
        super(msg);
    }
}
