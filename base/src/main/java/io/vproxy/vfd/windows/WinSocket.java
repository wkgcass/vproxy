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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class WinSocket {
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Allocator allocator;
    private final PNIRef<WinSocket> ref;

    public final SOCKET fd;
    public final WinSocket listenSocket; // optional

    public final MemorySegment recvMemSeg;
    public final MemorySegment sendMemSeg;
    public final RingBuffer recvRingBuffer;
    public final RingBuffer sendRingBuffer;

    public final VIOContext recvContext;
    public final VIOContext sendContext;

    private final AtomicInteger refCnt = new AtomicInteger(1); // decr when close

    WinIOCP iocp;
    final ConcurrentLinkedQueue<WinIOCP.Notification> notifications = new ConcurrentLinkedQueue<>();

    public static WinSocket ofTcp(int fd) {
        return new WinSocket(fd, null, false);
    }

    public static WinSocket ofAcceptedTcp(int fd, WinSocket listenSocket) {
        return new WinSocket(fd, listenSocket, false);
    }

    public static WinSocket ofUdp(int fd) {
        return new WinSocket(fd, null, true);
    }

    public WinSocket(int fd, WinSocket listenSocket, boolean udp) {
        allocator = PooledAllocator.ofUnsafePooled();
        this.ref = PNIRef.of(this);
        this.fd = new SOCKET(MemorySegment.ofAddress(fd));
        this.listenSocket = listenSocket;

        recvMemSeg = allocator.allocate(24576);
        MemorySegment sendMemSeg = null;
        if (!udp) {
            sendMemSeg = allocator.allocate(24576);
        }
        this.sendMemSeg = sendMemSeg;

        recvRingBuffer = SimpleRingBuffer.wrap(recvMemSeg);
        RingBuffer sendRingBuffer = null;
        if (!udp) {
            sendRingBuffer = SimpleRingBuffer.wrap(sendMemSeg);
        }
        this.sendRingBuffer = sendRingBuffer;

        recvContext = new VIOContext(allocator);
        {
            recvContext.setSocket(this.fd);
            recvContext.setRef(ref);
            if (listenSocket == null) {
                recvContext.setIoType(IOType.READ.code);
            } else {
                recvContext.setIoType(IOType.ACCEPT.code);
            }
            recvContext.getBuffers().get(0).setBuf(recvMemSeg);
            recvContext.getBuffers().get(0).setLen(24576);
            recvContext.setBufferCount(1);
            recvContext.setCtxType(IOCPUtils.VPROXY_CTX_TYPE);
            recvContext.setAddrLen((int) PNISockaddrStorage.LAYOUT.byteSize());
            IOCPUtils.setPointer(recvContext);
        }
        VIOContext sendContext = null;
        if (!udp) {
            sendContext = new VIOContext(allocator);
            sendContext.setSocket(this.fd);
            sendContext.setRef(ref);
            if (listenSocket == null) {
                sendContext.setIoType(IOType.CONNECT.code);
            } else {
                sendContext.setIoType(IOType.WRITE.code);
            }
            sendContext.setBufferCount(1);
            sendContext.setCtxType(IOCPUtils.VPROXY_CTX_TYPE);
            IOCPUtils.setPointer(sendContext);
        }
        this.sendContext = sendContext;

        UnderlyingIOCP.get().associate(this.fd);
    }

    WinIOCP getIocp() {
        var iocp = this.iocp;
        if (iocp == null) {
            return null;
        }
        if (iocp.isClosed()) {
            this.iocp = null;
            return null;
        }
        return iocp;
    }

    public void incrIORefCnt() {
        refCnt.incrementAndGet();
    }

    public void decrIORefCnt() {
        if (refCnt.decrementAndGet() == 0) {
            realClose();
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

    public void close() {
        if (closed.get()) {
            return;
        }
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        int refCntAfterDec = refCnt.decrementAndGet();
        assert Logger.lowLevelDebug(this + " is closing, current ioRefCnt is " + refCntAfterDec + ", notifications.size = " + notifications.size());
        if (refCntAfterDec == 0) {
            realClose();
            return;
        }
        //noinspection WhileCanBeDoWhile
        while (notifications.poll() != null) {
            if (refCnt.decrementAndGet() == 0) {
                realClose();
                return;
            }
        }
    }

    private void realClose() {
        assert Logger.lowLevelDebug("calling realClose on " + this);
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
        return "Socket(" + recvContext.getSocket().MEMORY.address() + ", refCnt=" + refCnt.get() + ")";
    }
}
