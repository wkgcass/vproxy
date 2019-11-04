package vproxy.connection;

import vfd.SocketFD;
import vproxy.util.RingBuffer;
import vproxy.util.Tuple;

import java.io.IOException;
import java.nio.channels.NetworkChannel;

public interface ServerHandler {
    void acceptFail(ServerHandlerContext ctx, IOException err);

    void connection(ServerHandlerContext ctx, Connection connection);

    // <in buffer, out buffer>
    Tuple<RingBuffer, RingBuffer> getIOBuffers(SocketFD channel);

    void removed(ServerHandlerContext ctx);

    default void exception(ServerHandlerContext ctx, IOException err) {
        // do nothing
    }

    default ConnectionOpts connectionOpts() {
        return DefaultConnectionOpts.defaultConnectionOpts;
    }
}
