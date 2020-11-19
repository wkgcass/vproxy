package vlibbase;

import vproxybase.connection.Connection;

import java.io.IOException;

public interface ConnRef {
    boolean isValidRef();

    boolean isTransferring();

    <T> T transferTo(ConnectionAware<T> client) throws IOException;

    Connection raw();

    void close();
}
