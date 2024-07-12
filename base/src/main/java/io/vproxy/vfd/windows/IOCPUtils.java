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

    public static void notify(WinIOCP iocp) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (iocp) {
            if (!iocp.polling) {
                iocp.notified = true;
                return;
            }
        }
        var allocator = Allocator.ofAllocateAndForgetUnsafe();
        var ctx = new VIOContext(allocator);
        IOCPUtils.setPointer(ctx);
        ctx.setCtxType(IOCPUtils.VPROXY_CTX_TYPE);
        ctx.setIoType(IOType.NOTIFY.code);

        try {
            IOCP.get().postQueuedCompletionStatus(VProxyThread.current().getEnv(),
                iocp.handle, 0, null, ctx.getOverlapped());
        } catch (IOException e) {
            Logger.error(LogType.SYS_ERROR, "failed to notify iocp: " + iocp);
            SunUnsafe.freeMemory(ctx.MEMORY.address());
        }
    }

    public static VIOContext buildContextForSendingDatagramPacket(WinSocket socket, int dataLen) {
        var allocator = PooledAllocator.ofUnsafePooled();
        var data = allocator.allocate(dataLen);
        return buildContextForSendingDatagramPacket(socket, allocator, data);
    }

    public static VIOContext buildContextForSendingDatagramPacket(WinSocket socket, MemorySegment data) {
        var allocator = PooledAllocator.ofUnsafePooled();
        return buildContextForSendingDatagramPacket(socket, allocator, data);
    }

    public static VIOContext buildContextForSendingDatagramPacket(WinSocket socket, Allocator allocator, MemorySegment data) {
        var ref = PNIRef.of(new Tuple<>(allocator, socket));

        var ctx = new VIOContext(allocator);
        IOCPUtils.setPointer(ctx);
        ctx.setCtxType(IOCPUtils.VPROXY_CTX_TYPE);
        ctx.setIoType(IOType.DISCARD.code);
        ctx.setRef(ref);
        ctx.setSocket(socket.fd);
        var buf = ctx.getBuffers().get(0);
        buf.setBuf(data);
        buf.setLen((int) data.byteSize());
        ctx.setBufferCount(1);

        return ctx;
    }

    public static int getContextType(VIOContext ctx) {
        return ctx.getPtr().reinterpret(4).get(ValueLayout.JAVA_INT_UNALIGNED, 0);
    }

    public static String convertNTStatusToString(long ntstatus) {
        return "Unexpected NTSTATUS: " + Long.toHexString(ntstatus);
    }
}
