package vproxy.vfd;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface ReadableByteStream {
    int read(ByteBuffer dst) throws IOException;
}
