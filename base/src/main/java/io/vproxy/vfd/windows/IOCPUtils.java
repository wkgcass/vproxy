package io.vproxy.vfd.windows;

import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.base.util.unsafe.SunUnsafe;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.PNIRef;
import io.vproxy.pni.PooledAllocator;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class IOCPUtils {
    private IOCPUtils() {
    }

    public static final int VPROXY_CTX_TYPE = 0xFF0A;
    public static SOCKET INVALID_HANDLE = new SOCKET(MemorySegment.ofAddress(-1));

    public static VIOContext getIOContextOf(Overlapped overlapped) {
        return new VIOContext(MemorySegment.ofAddress(overlapped.MEMORY.address() - 8));
    }

    public static void setPointer(VIOContext ctx) {
        ctx.setPtr(MemorySegment.ofAddress(
            ctx.MEMORY.address() +
                ValueLayout.ADDRESS.byteSize() +
                Overlapped.LAYOUT.byteSize()));
    }

    public static void notify(HANDLE iocp) {
        var allocator = Allocator.ofAllocateAndForgetUnsafe();
        var ctx = new VIOContext(allocator);
        IOCPUtils.setPointer(ctx);
        ctx.setCtxType(IOCPUtils.VPROXY_CTX_TYPE);
        ctx.setIoType(IOType.NOTIFY.code);

        try {
            IOCP.get().postQueuedCompletionStatus(VProxyThread.current().getEnv(),
                iocp, 0, null, ctx.getOverlapped());
        } catch (IOException e) {
            Logger.error(LogType.SYS_ERROR, "failed to notify iocp: " + iocp.MEMORY.address());
            SunUnsafe.freeMemory(ctx.MEMORY.address());
        }
    }

    public static VIOContext buildContextForSendingUDPPacket(WinSocket socket, int dataLen) {
        var allocator = PooledAllocator.ofUnsafePooled();
        var ref = PNIRef.of(new Tuple<>(allocator, socket));

        var ctx = new VIOContext(allocator);
        IOCPUtils.setPointer(ctx);
        ctx.setCtxType(IOCPUtils.VPROXY_CTX_TYPE);
        ctx.setIoType(IOType.DISCARD.code);
        ctx.setRef(ref);
        ctx.setSocket(socket.fd);
        var buf = ctx.getBuffers().get(0);
        buf.setBuf(allocator.allocate(dataLen));
        buf.setLen(dataLen);
        ctx.setBufferCount(1);

        return ctx;
    }

    public static int getContextType(VIOContext ctx) {
        return ctx.getPtr().reinterpret(4).get(ValueLayout.JAVA_INT_UNALIGNED, 0);
    }
}
