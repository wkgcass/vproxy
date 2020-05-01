package vfd.windows;

import java.io.IOException;
import java.nio.ByteBuffer;

public class GeneralWindows implements Windows {
    @Override
    native public boolean tapNonBlockingSupported() throws IOException;

    @Override
    native public long createTapFD(String dev) throws IOException;

    @Override
    native public void closeHandle(long handle) throws IOException;

    @Override
    native public int read(long handle, ByteBuffer directBuffer, int off, int len) throws IOException;

    @Override
    native public int write(long handle, ByteBuffer directBuffer, int off, int len) throws IOException;
}
