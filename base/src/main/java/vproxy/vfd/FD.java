package vproxy.vfd;

import vproxy.base.selector.SelectorEventLoop;
import vproxy.vfd.type.FDCloseReq;
import vproxy.vfd.type.FDCloseReturn;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketOption;

public interface FD extends Closeable {
    boolean isOpen();

    void configureBlocking(boolean b) throws IOException;

    <T> void setOption(SocketOption<T> name, T value) throws IOException;

    /**
     * Close the fd.
     * This method signature ensures that the implementations always call <code>super.close(req)</code>.
     *
     * @param req close request
     * @return close result
     * @throws IOException any exception raised
     */
    FDCloseReturn close(FDCloseReq req) throws IOException;

    /**
     * Implementations are not recommended to override this method.
     * If you have to, do call {@link #close(FDCloseReq)} and make sure the result is valid yourself.
     *
     * @throws IOException any exception raised
     */
    default void close() throws IOException {
        FDCloseReturn ret = close(FDCloseReq.inst());
        if (ret == null) {
            throw new IOException("IMPLEMENTATION ERROR!!! the close(x) method must return a CloseReturn object");
        }
    }

    /**
     * @return the real fd object
     */
    FD real();

    /**
     * @param loop the loop which this fd is attaching to
     * @return true if the fd allows attaching, false otherwise
     */
    default boolean loopAware(SelectorEventLoop loop) {
        return true;
    }
}
