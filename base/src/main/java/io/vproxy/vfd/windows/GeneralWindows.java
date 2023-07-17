package io.vproxy.vfd.windows;

import io.vproxy.panama.Panama;
import io.vproxy.panama.WrappedFunction;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.ByteBuffer;

import static io.vproxy.panama.Panama.format;

@SuppressWarnings({"Convert2MethodRef", "CodeBlock2Expr"})
public class GeneralWindows implements Windows {
    private static final WrappedFunction tapNonBlockingSupported =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_windows_GeneralWindows_tapNonBlockingSupported");

    @Override
    public boolean tapNonBlockingSupported() throws IOException {
        return tapNonBlockingSupported.invoke((h, e) -> {
            h.invokeExact(e);
        }).returnBool(IOException.class);
    }

    private static final WrappedFunction allocateOverlapped =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_windows_GeneralWindows_allocateOverlapped");

    @Override
    public long allocateOverlapped() throws IOException {
        return allocateOverlapped.invoke((h, e) -> {
            h.invokeExact(e);
        }).returnLong(IOException.class);
    }

    private static final WrappedFunction releaseOverlapped =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_windows_GeneralWindows_releaseOverlapped",
            long.class);

    @Override
    public void releaseOverlapped(long overlapped) throws IOException {
        releaseOverlapped.invoke((h, e) -> {
            h.invokeExact(e, overlapped);
        }).returnNothing(IOException.class);
    }

    private static final WrappedFunction createTapHandle =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_windows_GeneralWindows_createTapHandle",
            String.class);

    @Override
    public long createTapHandle(String dev) throws IOException {
        try (var arena = Arena.ofConfined()) {
            return createTapHandle.invoke((h, e) -> {
                h.invokeExact(e, format(dev, arena));
            }).returnLong(IOException.class);
        }
    }

    private static final WrappedFunction closeHandle =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_windows_GeneralWindows_closeHandle",
            long.class);

    @Override
    public void closeHandle(long handle) throws IOException {
        closeHandle.invoke((h, e) -> {
            h.invokeExact(e, handle);
        }).returnNothing(IOException.class);
    }

    private static final WrappedFunction read =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_windows_GeneralWindows_read",
            long.class, ByteBuffer.class, int.class, int.class, long.class);

    @Override
    public int read(long handle, ByteBuffer directBuffer, int off, int len, long overlapped) throws IOException {
        return read.invoke((h, e) -> {
            h.invokeExact(e, handle, format(directBuffer), off, len, overlapped);
        }).returnInt(IOException.class);
    }

    private static final WrappedFunction write =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_windows_GeneralWindows_write",
            long.class, ByteBuffer.class, int.class, int.class, long.class);

    @Override
    public int write(long handle, ByteBuffer directBuffer, int off, int len, long overlapped) throws IOException {
        return write.invoke((h, e) -> {
            h.invokeExact(e, handle, format(directBuffer), off, len, overlapped);
        }).returnInt(IOException.class);
    }
}
