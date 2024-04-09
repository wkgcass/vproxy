package io.vproxy.vfd;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface ReadableByteStream {
    int read(ByteBuffer dst) throws IOException;

    default int readBlocking(ByteBuffer buf) throws IOException {
        return read(buf);
    }
}
