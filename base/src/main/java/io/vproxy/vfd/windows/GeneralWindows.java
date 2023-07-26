package io.vproxy.vfd.windows;

import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.PNIString;

import java.io.IOException;
import java.nio.ByteBuffer;

public class GeneralWindows implements Windows {
    @Override
    public boolean tapNonBlockingSupported() throws IOException {
        return WindowsNative.get().tapNonBlockingSupported(VProxyThread.current().getEnv());
    }

    @Override
    public long allocateOverlapped() throws IOException {
        return WindowsNative.get().allocateOverlapped(VProxyThread.current().getEnv());
    }

    @Override
    public void releaseOverlapped(long overlapped) throws IOException {
        WindowsNative.get().releaseOverlapped(VProxyThread.current().getEnv(), overlapped);
    }

    @Override
    public long createTapHandle(String dev) throws IOException {
        try (var allocator = Allocator.ofPooled()) {
            return WindowsNative.get().createTapHandle(VProxyThread.current().getEnv(), new PNIString(allocator, dev));
        }
    }

    @Override
    public void closeHandle(long handle) throws IOException {
        WindowsNative.get().closeHandle(VProxyThread.current().getEnv(), handle);
    }

    @Override
    public int read(long handle, ByteBuffer directBuffer, int off, int len, long overlapped) throws IOException {
        return WindowsNative.get().read(VProxyThread.current().getEnv(),
            handle, directBuffer, off, len, overlapped);
    }

    @Override
    public int write(long handle, ByteBuffer directBuffer, int off, int len, long overlapped) throws IOException {
        return WindowsNative.get().write(VProxyThread.current().getEnv(),
            handle, directBuffer, off, len, overlapped);
    }
}
