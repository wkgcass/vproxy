package io.vproxy.vfd.windows;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.vfd.posix.Posix;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class WindowsNetworkFD extends WindowsFD {
    protected boolean canBeConnected = false;
    protected boolean connected = false;
    protected IOException exception = null;
    protected boolean remoteClosed = false;

    public WindowsNetworkFD(Windows windows, Posix posix) {
        super(windows, posix);
    }

    public boolean isConnected() {
        return connected;
    }

    protected void checkConnected() throws IOException {
        if (!connected) {
            throw new IOException("not connected");
        }
    }

    protected void checkError() throws IOException {
        if (exception != null) {
            exception.setStackTrace(Thread.currentThread().getStackTrace());
            throw exception;
        }
    }

    public int read(ByteBuffer dst) throws IOException {
        checkFD();
        checkConnected();
        checkNotClosed();
        checkError();

        assert Logger.lowLevelDebug("user trying to read from " + socket +
                                    ", current rb.used() == " + socket.recvRingBuffer.used() +
                                    ", user input buffer == " + (dst.limit() - dst.position()));

        if (socket.recvRingBuffer.used() == 0) {
            if (remoteClosed) {
                return -1;
            }
            return 0;
        }
        if (socket.isDatagramSocket() && dst.limit() - dst.position() < socket.recvRingBuffer.used()) {
            assert Logger.lowLevelDebug("user buffer too small");
            return 0;
        }

        var freeBeforeRead = socket.recvRingBuffer.free();
        int ret = socket.recvRingBuffer.writeTo(dst);

        if (socket.recvRingBuffer.used() == 0) {
            clearReadable();
        }

        if (socket.isStreamSocket()) {
            // ring buffer was full so the read operation was not delivered in ioComplete
            // need to deliver now
            if (freeBeforeRead == 0 && ret > 0) {
                deliverStreamSocketReadOperation();
            }
            return ret;
        }

        // deliver read operation for datagram socket

        var buf = socket.recvContext.getBuffers().get(0);
        buf.setBuf(socket.recvMemSeg);
        buf.setLen(socket.recvRingBuffer.capacity());
        socket.recvContext.setBufferCount(1);

        doRecv();

        return ret;
    }

    public int write(ByteBuffer src) throws IOException {
        checkFD();
        checkConnected();
        checkNotClosed();
        checkError();

        int srcLen = src.limit() - src.position();
        assert Logger.lowLevelDebug("user trying to write " + srcLen + " bytes to " + socket);

        // nothing to send
        if (srcLen == 0) {
            return 0;
        }

        int ret;
        if (socket.isDatagramSocket()) {
            var ctx = IOCPUtils.buildContextForSendingDatagramPacket(socket, srcLen);
            ByteArray.from(ctx.getBuffers().get(0).getBuf().reinterpret(srcLen)).byteBufferGet(src, 0, srcLen);
            doSend(ctx);
            return srcLen;
        }
        var free = socket.sendRingBuffer.free();
        if (free == 0) { // buffer is full
            return 0;
        }
        var eposBefore = socket.sendRingBuffer.getEPos();
        ret = socket.sendRingBuffer.storeBytesFrom(src);
        var eposAfter = socket.sendRingBuffer.getEPos();

        if (eposAfter <= eposBefore) {
            // wraps
            if (eposAfter == 0) {
                var buf = socket.sendContext.getBuffers().get(0);
                buf.setBuf(socket.sendMemSeg.asSlice(eposBefore));
                buf.setLen(socket.sendRingBuffer.capacity() - eposBefore);
                socket.sendContext.setBufferCount(1);
            } else {
                var buf1 = socket.sendContext.getBuffers().get(0);
                buf1.setBuf(socket.sendMemSeg.asSlice(eposBefore));
                buf1.setLen(socket.sendRingBuffer.capacity() - eposBefore);
                var buf2 = socket.sendContext.getBuffers().get(1);
                buf2.setBuf(socket.sendMemSeg);
                buf2.setLen(eposAfter);
                socket.sendContext.setBufferCount(2);
            }
        } else {
            var buf = socket.sendContext.getBuffers().get(0);
            buf.setBuf(socket.sendMemSeg.asSlice(eposBefore));
            buf.setLen(eposAfter - eposBefore);
            socket.sendContext.setBufferCount(1);
        }

        if (socket.sendRingBuffer.free() == 0) {
            clearWritable();
        }

        doSend();
        return ret;
    }

    @Override
    protected void ioComplete(VIOContext ctx, int nbytes) {
        var ntstatus = ctx.getOverlapped().getInternal();
        if (ntstatus != 0) {
            setException(new IOException(IOCPUtils.convertNTStatusToString(ntstatus)));
            return;
        }
        if (ctx.MEMORY.address() == socket.recvContext.MEMORY.address()) {
            recvComplete(ctx, nbytes);
        } else {
            sendComplete(ctx, nbytes);
        }
    }

    protected void setException(IOException e) {
        exception = e;
        setReadable();
        setWritable();
    }

    private void recvComplete(VIOContext ctx, int nbytes) {
        assert Logger.lowLevelDebug("recvComplete for " + socket + ", ioType=" + ctx.getIoType() + ", received " + nbytes + " bytes");
        if (ctx.getIoType() == IOType.ACCEPT.code) {
            setException(new IOException("received unexpected ACCEPT io operation event"));
            return;
        } else if (ctx.getIoType() == IOType.READ.code) {
            if (nbytes == 0) {
                remoteClosed = true;
            } else {
                socket.recvRingBuffer.fillBytes(nbytes);
            }
            setReadable(); // let user read from it
        } else {
            setException(new IOException("unexpected IOType " + ctx.getIoType() + " with recvContext"));
            return;
        }

        // datagram socket should only deliver read operation when user reads the packet
        if (socket.isDatagramSocket()) {
            return;
        }
        if (socket.recvRingBuffer.free() > 0) {
            deliverStreamSocketReadOperation();
        }
    }

    private void sendComplete(VIOContext ctx, int nbytes) {
        assert Logger.lowLevelDebug("sendComplete for " + socket + ", ioType=" + ctx.getIoType() + ", sent " + nbytes + " bytes");
        if (ctx.getIoType() == IOType.CONNECT.code) {
            ctx.setIoType(IOType.WRITE.code);
            canBeConnected = true;
            setWritable();
        } else if (ctx.getIoType() == IOType.WRITE.code) {
            if (nbytes != 0) {
                socket.sendRingBuffer.discardBytes(nbytes);
                setWritable();
            } else {
                // do not handle the condition where nbytes == 0
                assert Logger.lowLevelDebug("write operation completed, but wrote 0 bytes");
            }
        } else {
            setException(new IOException("unexpected IOType " + ctx.getIoType() + " with sendContext"));
        }
    }

    protected void deliverStreamSocketReadOperation() {
        // do not read anymore if remoteClosed
        if (remoteClosed) {
            return;
        }

        var eIsAfterS = socket.recvRingBuffer.getEPosIsAfterSPos();
        var epos = socket.recvRingBuffer.getEPos();
        var spos = socket.recvRingBuffer.getSPos();

        if (!eIsAfterS) {
            var buf = socket.recvContext.getBuffers().get(0);
            buf.setBuf(socket.recvMemSeg);
            buf.setLen(spos - epos);
            socket.recvContext.setBufferCount(1);
        } else if (spos == 0) {
            var buf = socket.recvContext.getBuffers().get(0);
            buf.setBuf(socket.recvMemSeg.asSlice(epos));
            buf.setLen(socket.recvRingBuffer.capacity() - epos);
            socket.recvContext.setBufferCount(1);
        } else {
            var buf1 = socket.recvContext.getBuffers().get(0);
            buf1.setBuf(socket.recvMemSeg.asSlice(epos));
            buf1.setLen(socket.recvRingBuffer.capacity() - epos);
            var buf2 = socket.recvContext.getBuffers().get(1);
            buf2.setBuf(socket.recvMemSeg);
            buf2.setLen(spos);
            socket.recvContext.setBufferCount(2);
        }

        try {
            doRecv();
        } catch (IOException e) {
            Logger.error(LogType.SOCKET_ERROR, "failed to call doRecv on " + socket, e);
        }
    }

    protected void doRecv() throws IOException {
        windows.wsaRecv(socket);
    }

    protected void doSend() throws IOException {
        windows.wsaSend(socket);
    }

    protected void doSend(VIOContext ctx) throws IOException {
        windows.wsaSend(socket, ctx);
    }
}
