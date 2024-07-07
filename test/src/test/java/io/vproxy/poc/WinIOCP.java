package io.vproxy.poc;

import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.PNIRef;
import io.vproxy.pni.PNIString;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.posix.PosixNative;
import io.vproxy.vfd.posix.SocketAddressIPv4;
import io.vproxy.vfd.windows.*;

import java.lang.foreign.MemorySegment;

public class WinIOCP {
    private static final Allocator allocator = Allocator.ofUnsafe();

    public static void main(String[] args) throws Exception {
        System.loadLibrary("pni");
        System.loadLibrary("vfdwindows");

        var iocp = IOCP.get().createIoCompletionPort(VProxyThread.current().getEnv(),
            new HANDLE(MemorySegment.ofAddress(-1)),
            null,
            null, 0);
        System.out.println("iocp = " + iocp);

        VProxyThread.create(() -> loop(iocp), "iocp-loop").start();

        var listenFd = PosixNative.get().createIPv4TcpFD(VProxyThread.current().getEnv());
        var listenSocket = new SOCKET(MemorySegment.ofAddress(listenFd));
        System.out.println("listenSocket = " + listenSocket);

        PosixNative.get().bindIPv4(VProxyThread.current().getEnv(),
            listenFd, IP.fromIPv4("127.0.0.1").getIPv4Value(), 8080);

        IOCP.get().createIoCompletionPort(VProxyThread.current().getEnv(),
            new HANDLE(MemorySegment.ofAddress(listenFd)), iocp,
            null, 0);

        var acceptedFd = PosixNative.get().createIPv4TcpFD(VProxyThread.current().getEnv());
        var acceptedSocket = new SOCKET(MemorySegment.ofAddress(acceptedFd));
        System.out.println("create a socket to handle accept event: " + acceptedSocket);

        var acceptCtx = new VIOContext(allocator);
        acceptCtx.setSocket(acceptedSocket);
        acceptCtx.setListenSocket(listenSocket);
        acceptCtx.setIoType(IOType.ACCEPT.code);
        acceptCtx.setV4(true);
        acceptCtx.getBuffers().get(0).setBuf(allocator.allocate(1024));
        acceptCtx.getBuffers().get(0).setLen(1024);
        acceptCtx.setBufferCount(1);

        boolean sync = WindowsNative.get().acceptEx(VProxyThread.current().getEnv(),
            listenSocket, acceptCtx);
        if (sync) {
            System.out.println("sync accept succeeded");
        } else {
            System.out.println("async job delivered");
        }
    }

    private static void loop(HANDLE iocp) {
        while (true) {
            try {
                oneLoop(iocp);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    private static void oneLoop(HANDLE iocp) throws Exception {
        var entries = new OverlappedEntry.Array(allocator, 16);
        var nEvents = IOCP.get().getQueuedCompletionStatusEx(VProxyThread.current().getEnv(), iocp, entries, 16, -1, false);

        System.out.println("IOCP got " + nEvents + " events");

        for (int i = 0; i < nEvents; ++i) {
            var entry = entries.get(i);
            var ctx = IOCPUtils.getIOContextOf(entry.getOverlapped());

            if (ctx.getIoType() == IOType.ACCEPT.code) {
                var socket = ctx.getSocket();
                System.out.println("socket accepted: " + socket);

                WindowsNative.get().updateAcceptContext(VProxyThread.current().getEnv(), ctx.getListenSocket(), socket);

                var st = PosixNative.get().getIPv4Remote(VProxyThread.current().getEnv(), (int) socket.MEMORY.address(), allocator);
                var addr = new SocketAddressIPv4(st.getIp(), st.getPort() & 0xffff);
                System.out.println("remote address is " + addr);

                IOCP.get().createIoCompletionPort(VProxyThread.current().getEnv(),
                    new HANDLE(socket.MEMORY), iocp, null, 0);

                var conn = new Conn(socket, ctx);

                int nRcvd = WindowsNative.get().wsaRecv(VProxyThread.current().getEnv(), conn.readCtx);
                if (nRcvd < 0) {
                    System.out.println("async receive event delivered");
                } else {
                    System.out.println("receive event directly return: " + nRcvd);
                }
            } else if (ctx.getIoType() == IOType.READ.code) {
                var socket = ctx.getSocket();
                System.out.println("socket async recv done: " + socket);

                var rcvLen = entry.getNumberOfBytesTransferred();
                var rbuf = ctx.getBuffers().get(0).getBuf().reinterpret(rcvLen);
                System.out.println("received data: " + new PNIString(rbuf));

                var conn = (Conn) ctx.getRef().getRef();
                var wbuf = conn.writeCtx.getBuffers().get(0);
                wbuf.getBuf().reinterpret(1024).copyFrom(rbuf);
                wbuf.setLen(rcvLen);

                WindowsNative.get().wsaSend(VProxyThread.current().getEnv(), conn.writeCtx);
            } else if (ctx.getIoType() == IOType.WRITE.code) {
                var socket = ctx.getSocket();
                var sndLen = entry.getNumberOfBytesTransferred();
                System.out.println("socket async send done: " + socket + ", wrote " + sndLen + " bytes");

                WindowsNative.get().closeHandle(VProxyThread.current().getEnv(), socket);
            }
        }
    }

    private static class Conn {
        final PNIRef<Conn> ref;
        final VIOContext readCtx;
        final VIOContext writeCtx;

        private Conn(SOCKET socket, VIOContext readCtx) {
            ref = PNIRef.of(this);

            this.readCtx = readCtx;
            this.readCtx.setIoType(IOType.READ.code);
            this.readCtx.setRef(ref);

            this.writeCtx = new VIOContext(allocator);
            this.writeCtx.setSocket(socket);
            this.writeCtx.setV4(true);
            this.writeCtx.setBufferCount(1);
            this.writeCtx.getBuffers().get(0).setBuf(allocator.allocate(1024));
            this.writeCtx.getBuffers().get(0).setLen(1024);
            this.writeCtx.setIoType(IOType.WRITE.code);
            this.writeCtx.setRef(ref);
        }
    }
}
