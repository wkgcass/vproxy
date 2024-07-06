package io.vproxy.vfd.windows;

import io.vproxy.pni.*;
import io.vproxy.pni.hack.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;
import io.vproxy.pni.graal.*;
import org.graalvm.nativeimage.*;
import org.graalvm.nativeimage.c.function.*;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.WordFactory;

public class WindowsNative {
    private WindowsNative() {
    }

    private static final WindowsNative INSTANCE = new WindowsNative();

    public static WindowsNative get() {
        return INSTANCE;
    }

    private static final MethodHandle tapNonBlockingSupportedMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions(), "Java_io_vproxy_vfd_windows_WindowsNative_tapNonBlockingSupported");

    public boolean tapNonBlockingSupported(PNIEnv ENV) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) tapNonBlockingSupportedMH.invokeExact(ENV.MEMORY);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnBool();
    }

    private static final MethodHandle allocateOverlappedMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions(), "Java_io_vproxy_vfd_windows_WindowsNative_allocateOverlapped");

    public long allocateOverlapped(PNIEnv ENV) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) allocateOverlappedMH.invokeExact(ENV.MEMORY);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnLong();
    }

    private static final MethodHandle releaseOverlappedMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions(), "Java_io_vproxy_vfd_windows_WindowsNative_releaseOverlapped", long.class /* overlapped */);

    public void releaseOverlapped(PNIEnv ENV, long overlapped) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) releaseOverlappedMH.invokeExact(ENV.MEMORY, overlapped);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle createTapHandleMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions(), "Java_io_vproxy_vfd_windows_WindowsNative_createTapHandle", String.class /* dev */);

    public long createTapHandle(PNIEnv ENV, PNIString dev) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) createTapHandleMH.invokeExact(ENV.MEMORY, (MemorySegment) (dev == null ? MemorySegment.NULL : dev.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnLong();
    }

    private static final MethodHandle closeHandleMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions(), "Java_io_vproxy_vfd_windows_WindowsNative_closeHandle", long.class /* fd */);

    public void closeHandle(PNIEnv ENV, long fd) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) closeHandleMH.invokeExact(ENV.MEMORY, fd);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle readMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions(), "Java_io_vproxy_vfd_windows_WindowsNative_read", long.class /* handle */, ByteBuffer.class /* directBuffer */, int.class /* off */, int.class /* len */, long.class /* overlapped */);

    public int read(PNIEnv ENV, long handle, ByteBuffer directBuffer, int off, int len, long overlapped) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) readMH.invokeExact(ENV.MEMORY, handle, PanamaUtils.format(directBuffer), off, len, overlapped);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle writeMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions(), "Java_io_vproxy_vfd_windows_WindowsNative_write", long.class /* handle */, ByteBuffer.class /* directBuffer */, int.class /* off */, int.class /* len */, long.class /* overlapped */);

    public int write(PNIEnv ENV, long handle, ByteBuffer directBuffer, int off, int len, long overlapped) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) writeMH.invokeExact(ENV.MEMORY, handle, PanamaUtils.format(directBuffer), off, len, overlapped);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnInt();
    }
}
// metadata.generator-version: pni 22.0.0.20
// sha256:fc869110950ddf7ad9b6b1644aff57b8918242b20c71c440a31a43723f07125e
