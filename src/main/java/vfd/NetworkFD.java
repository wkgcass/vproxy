package vfd;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public interface NetworkFD<ADDR extends SockAddr> extends FD, ReadableByteChannel, WritableByteChannel {
    ADDR getLocalAddress() throws IOException;

    ADDR getRemoteAddress() throws IOException;
}
