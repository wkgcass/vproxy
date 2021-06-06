package vproxy.vfd;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface WritableByteStream {
    int write(ByteBuffer src) throws IOException;
}
