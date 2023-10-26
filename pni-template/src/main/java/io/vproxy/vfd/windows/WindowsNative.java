package io.vproxy.vfd.windows;

import io.vproxy.pni.annotation.*;

import java.io.IOException;
import java.nio.ByteBuffer;

@Downcall
interface PNIWindowsNative {
    boolean tapNonBlockingSupported() throws IOException;

    long allocateOverlapped() throws IOException;

    void releaseOverlapped(long overlapped) throws IOException;

    long createTapHandle(String dev) throws IOException;

    void closeHandle(long fd) throws IOException;

    int read(long handle, @Raw ByteBuffer directBuffer, int off, int len, long overlapped) throws IOException;

    int write(long handle, @Raw ByteBuffer directBuffer, int off, int len, long overlapped) throws IOException;
}
