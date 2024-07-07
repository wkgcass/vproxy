package io.vproxy.vfd.windows;

import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.base.util.ringbuffer.SimpleRingBuffer;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.PNIRef;
import io.vproxy.pni.PooledAllocator;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicBoolean;

public class WinSocket implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Allocator allocator;
    private final PNIRef<WinSocket> ref;

    protected final MemorySegment recvMemSeg;
    protected final MemorySegment sendMemSeg;
    protected final RingBuffer recvRingBuffer;
    protected final RingBuffer sendRingBuffer;

    public final VIOContext recvContext;
    public final VIOContext sendContext;

    public WinSocket(int fd, boolean v4) {
        this(fd, null, v4);
    }

    public WinSocket(int fd, SOCKET listenSocket, boolean v4) {
        allocator = PooledAllocator.ofUnsafePooled();
        this.ref = PNIRef.of(this);
        var socket = new SOCKET(MemorySegment.ofAddress(fd));

        recvMemSeg = allocator.allocate(24576);
        sendMemSeg = allocator.allocate(24576);
        recvRingBuffer = SimpleRingBuffer.wrap(recvMemSeg);
        sendRingBuffer = SimpleRingBuffer.wrap(sendMemSeg);

        recvContext = new VIOContext(allocator);
        {
            recvContext.setSocket(socket);
            recvContext.setListenSocket(listenSocket);
            recvContext.setRef(ref);
            recvContext.setV4(v4);
            if (listenSocket == null) {
                recvContext.setIoType(IOType.READ.code);
            } else {
                recvContext.setIoType(IOType.ACCEPT.code);
            }
            recvContext.getBuffers().get(0).setBuf(recvMemSeg);
            recvContext.getBuffers().get(0).setLen(24576);
            recvContext.setBufferCount(1);
            recvContext.setCtxType(0xFF0A);
            IOCPUtils.setPointer(recvContext);
        }
        sendContext = new VIOContext(allocator);
        {
            sendContext.setSocket(socket);
            sendContext.setRef(ref);
            sendContext.setV4(v4);
            sendContext.setIoType(IOType.WRITE.code);
            sendContext.setCtxType(0xFF0A);
            IOCPUtils.setPointer(sendContext);
        }
    }

    @Override
    public void close() {
        if (closed.get()) {
            return;
        }
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            WindowsNative.get().closeHandle(VProxyThread.current().getEnv(), recvContext.getSocket());
        } catch (IOException e) {
            Logger.error(LogType.SOCKET_ERROR, "failed to close win socket: " + this);
        }
        ref.close();
        allocator.close();
    }

    @Override
    public String toString() {
        return "Socket(" + recvContext.getSocket().MEMORY.address() + ")";
    }
}
