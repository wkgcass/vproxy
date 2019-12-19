package vproxy.dns;

public class InvalidDNSPacketException extends Exception {
    public InvalidDNSPacketException(String message) {
        super(message);
    }
}
