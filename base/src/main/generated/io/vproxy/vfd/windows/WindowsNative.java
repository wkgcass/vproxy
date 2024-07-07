package io.vproxy.vfd.windows;

import io.vproxy.pni.*;
import io.vproxy.pni.hack.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

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

    private static final MethodHandle closeHandleMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_vfd_windows_WindowsNative_closeHandle", io.vproxy.vfd.windows.SOCKET.LAYOUT.getClass() /* handle */);

    public void closeHandle(PNIEnv ENV, io.vproxy.vfd.windows.SOCKET handle) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) closeHandleMH.invokeExact(ENV.MEMORY, (MemorySegment) (handle == null ? MemorySegment.NULL : handle.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle acceptExMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_vfd_windows_WindowsNative_acceptEx", io.vproxy.vfd.windows.SOCKET.LAYOUT.getClass() /* listenSocket */, io.vproxy.vfd.windows.VIOContext.LAYOUT.getClass() /* socketContext */);

    public boolean acceptEx(PNIEnv ENV, io.vproxy.vfd.windows.SOCKET listenSocket, io.vproxy.vfd.windows.VIOContext socketContext) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) acceptExMH.invokeExact(ENV.MEMORY, (MemorySegment) (listenSocket == null ? MemorySegment.NULL : listenSocket.MEMORY), (MemorySegment) (socketContext == null ? MemorySegment.NULL : socketContext.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnBool();
    }

    private static final MethodHandle updateAcceptContextMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_vfd_windows_WindowsNative_updateAcceptContext", io.vproxy.vfd.windows.SOCKET.LAYOUT.getClass() /* listenSocket */, io.vproxy.vfd.windows.SOCKET.LAYOUT.getClass() /* accepted */);

    public void updateAcceptContext(PNIEnv ENV, io.vproxy.vfd.windows.SOCKET listenSocket, io.vproxy.vfd.windows.SOCKET accepted) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) updateAcceptContextMH.invokeExact(ENV.MEMORY, (MemorySegment) (listenSocket == null ? MemorySegment.NULL : listenSocket.MEMORY), (MemorySegment) (accepted == null ? MemorySegment.NULL : accepted.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle tcpConnectMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_vfd_windows_WindowsNative_tcpConnect", io.vproxy.vfd.windows.VIOContext.LAYOUT.getClass() /* ctx */, io.vproxy.vfd.posix.SocketAddressUnion.LAYOUT.getClass() /* addr */);

    public boolean tcpConnect(PNIEnv ENV, io.vproxy.vfd.windows.VIOContext ctx, io.vproxy.vfd.posix.SocketAddressUnion addr) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) tcpConnectMH.invokeExact(ENV.MEMORY, (MemorySegment) (ctx == null ? MemorySegment.NULL : ctx.MEMORY), (MemorySegment) (addr == null ? MemorySegment.NULL : addr.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnBool();
    }

    private static final MethodHandle wsaRecvMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_vfd_windows_WindowsNative_wsaRecv", io.vproxy.vfd.windows.VIOContext.LAYOUT.getClass() /* ctx */);

    public int wsaRecv(PNIEnv ENV, io.vproxy.vfd.windows.VIOContext ctx) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) wsaRecvMH.invokeExact(ENV.MEMORY, (MemorySegment) (ctx == null ? MemorySegment.NULL : ctx.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle wsaRecvFromMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_vfd_windows_WindowsNative_wsaRecvFrom", io.vproxy.vfd.windows.VIOContext.LAYOUT.getClass() /* ctx */);

    public int wsaRecvFrom(PNIEnv ENV, io.vproxy.vfd.windows.VIOContext ctx) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) wsaRecvFromMH.invokeExact(ENV.MEMORY, (MemorySegment) (ctx == null ? MemorySegment.NULL : ctx.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle wsaSendMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_vfd_windows_WindowsNative_wsaSend", io.vproxy.vfd.windows.VIOContext.LAYOUT.getClass() /* ctx */);

    public int wsaSend(PNIEnv ENV, io.vproxy.vfd.windows.VIOContext ctx) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) wsaSendMH.invokeExact(ENV.MEMORY, (MemorySegment) (ctx == null ? MemorySegment.NULL : ctx.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle wsaSendToMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_vfd_windows_WindowsNative_wsaSendTo", io.vproxy.vfd.windows.VIOContext.LAYOUT.getClass() /* ctx */, io.vproxy.vfd.posix.SocketAddressUnion.LAYOUT.getClass() /* addr */);

    public int wsaSendTo(PNIEnv ENV, io.vproxy.vfd.windows.VIOContext ctx, io.vproxy.vfd.posix.SocketAddressUnion addr) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) wsaSendToMH.invokeExact(ENV.MEMORY, (MemorySegment) (ctx == null ? MemorySegment.NULL : ctx.MEMORY), (MemorySegment) (addr == null ? MemorySegment.NULL : addr.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle wsaSendDisconnectMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_vfd_windows_WindowsNative_wsaSendDisconnect", io.vproxy.vfd.windows.SOCKET.LAYOUT.getClass() /* socket */);

    public void wsaSendDisconnect(PNIEnv ENV, io.vproxy.vfd.windows.SOCKET socket) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) wsaSendDisconnectMH.invokeExact(ENV.MEMORY, (MemorySegment) (socket == null ? MemorySegment.NULL : socket.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle convertAddressMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_vfd_windows_WindowsNative_convertAddress", MemorySegment.class /* sockaddr */, boolean.class /* v4 */, io.vproxy.vfd.posix.SocketAddressUnion.LAYOUT.getClass() /* addr */);

    public void convertAddress(PNIEnv ENV, MemorySegment sockaddr, boolean v4, io.vproxy.vfd.posix.SocketAddressUnion addr) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) convertAddressMH.invokeExact(ENV.MEMORY, (MemorySegment) (sockaddr == null ? MemorySegment.NULL : sockaddr), v4, (MemorySegment) (addr == null ? MemorySegment.NULL : addr.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }
}
// metadata.generator-version: pni 22.0.0.20
// sha256:281addf40289e9bfa603588cab5de1ecb140dc2c4fdb196e9ebcaca56be82896
