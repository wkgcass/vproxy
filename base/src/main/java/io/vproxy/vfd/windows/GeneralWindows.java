package io.vproxy.vfd.windows;

import io.vproxy.panama.Panama;
import io.vproxy.panama.WrappedFunction;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.ByteBuffer;

import static io.vproxy.panama.Panama.format;

public class GeneralWindows implements Windows {
    private static final WrappedFunction tapNonBlockingSupported =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_windows_GeneralWindows_tapNonBlockingSupported");

    @Override
    public boolean tapNonBlockingSupported() throws IOException {
        return tapNonBlockingSupported.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e)
        ).returnBool();
    }

    private static final WrappedFunction allocateOverlapped =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_windows_GeneralWindows_allocateOverlapped");

    @Override
    public long allocateOverlapped() throws IOException {
        return allocateOverlapped.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e)
        ).returnLong();
    }

    private static final WrappedFunction releaseOverlapped =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_windows_GeneralWindows_releaseOverlapped",
            long.class);

    @Override
    public void releaseOverlapped(long overlapped) throws IOException {
        releaseOverlapped.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, overlapped)
        );
    }

    private static final WrappedFunction createTapHandle =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_windows_GeneralWindows_createTapHandle",
            String.class);

    @Override
    public long createTapHandle(String dev) throws IOException {
        try (var arena = Arena.ofConfined()) {
            return createTapHandle.invoke(IOException.class, (h, e) ->
                (int) h.invokeExact(e, format(dev, arena))
            ).returnLong();
        }
    }

    private static final WrappedFunction closeHandle =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_windows_GeneralWindows_closeHandle",
            long.class);

    @Override
    public void closeHandle(long handle) throws IOException {
        closeHandle.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, handle)
        );
    }

    private static final WrappedFunction read =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_windows_GeneralWindows_read",
            long.class, ByteBuffer.class, int.class, int.class, long.class);

    @Override
    public int read(long handle, ByteBuffer directBuffer, int off, int len, long overlapped) throws IOException {
        return read.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, handle, format(directBuffer), off, len, overlapped)
        ).returnInt();
    }

    private static final WrappedFunction write =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_windows_GeneralWindows_write",
            long.class, ByteBuffer.class, int.class, int.class, long.class);

    @Override
    public int write(long handle, ByteBuffer directBuffer, int off, int len, long overlapped) throws IOException {
        return write.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, handle, format(directBuffer), off, len, overlapped)
        ).returnInt();
    }
}
