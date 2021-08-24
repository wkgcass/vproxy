package vproxy.vfd;

import vproxy.base.selector.SelectorEventLoop;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketOption;

public interface FD extends Closeable {
    boolean isOpen();

    void configureBlocking(boolean b) throws IOException;

    <T> void setOption(SocketOption<T> name, T value) throws IOException;

    /**
     * @throws IOException any exception raised
     */
    void close() throws IOException;

    /**
     * @return the real fd object
     */
    FD real();

    boolean contains(FD fd);

    /**
     * @param loop the loop which this fd is attaching to
     * @return true if the fd allows attaching, false otherwise
     */
    default boolean loopAware(SelectorEventLoop loop) {
        return true;
    }
}
