package vfd.windows;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface Windows {
    boolean tapNonBlockingSupported() throws IOException;

    long allocateOverlapped() throws IOException;

    void releaseOverlapped(long overlapped) throws IOException;

    long createTapHandle(String dev) throws IOException;

    void closeHandle(long fd) throws IOException;

    int read(long handle, ByteBuffer directBuffer, int off, int len, long overlapped) throws IOException;

    int write(long handle, ByteBuffer directBuffer, int off, int len, long overlapped) throws IOException;
}
