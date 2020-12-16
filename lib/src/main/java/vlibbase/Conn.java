package vlibbase;

import vproxybase.connection.Connection;
import vproxybase.util.ByteArray;

import java.io.IOException;
import java.util.function.Consumer;

public interface Conn extends ConnRef {
    Conn data(Consumer<ByteArray> handler);

    Conn exception(Consumer<IOException> handler);

    Conn remoteClosed(Runnable handler);

    Conn closed(Runnable handler);

    Conn allWritten(Runnable handler);

    void write(ByteArray data);

    void closeWrite();

    void close();

    Connection detach();
}
