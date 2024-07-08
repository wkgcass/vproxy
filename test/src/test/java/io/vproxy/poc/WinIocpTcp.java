package io.vproxy.poc;

import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.objectpool.CursorList;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.pni.Allocator;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.posix.PosixNative;
import io.vproxy.vfd.posix.SocketAddressIPv4;
import io.vproxy.vfd.posix.SocketAddressUnion;
import io.vproxy.vfd.windows.*;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;

public class WinIocpTcp {
    public static void main(String[] args) throws Exception {
        System.loadLibrary("pni");
        System.loadLibrary("vfdwindows");

        var iocp = new io.vproxy.vfd.windows.WinIOCP();
        {
            Logger.alert("iocp = " + iocp);
            VProxyThread.create(() -> loop(iocp), "iocp-loop").start();
        }

        {
            var listenFd = PosixNative.get().createIPv4TcpFD(VProxyThread.current().getEnv());
            var listenSocket = new WinSocket(listenFd);
            Logger.alert("listenSocket = " + listenSocket);

            PosixNative.get().bindIPv4(VProxyThread.current().getEnv(),
                listenFd, IP.fromIPv4("127.0.0.1").getIPv4Value(), 8080);

            deliverAccept(iocp, listenSocket);
        }

        Thread.sleep(2_000);

        {
            var fd = PosixNative.get().createIPv4TcpFD(VProxyThread.current().getEnv());
            var connectSocket = new WinSocket(fd);
            Logger.alert("connectSocket = " + connectSocket);
            iocp.associate(connectSocket);

            deliverConnect(connectSocket);
        }
    }

    private static void deliverConnect(WinSocket socket) throws IOException {
        try (var allocator = Allocator.ofConfined()) {
            var un = new SocketAddressUnion(allocator);
            var v4 = un.getV4();
            v4.setIp(IP.fromIPv4("127.0.0.1").getIPv4Value());
            v4.setPort((short) 8080);

            socket.incrIORefCnt();
            WindowsNative.get().tcpConnect(VProxyThread.current().getEnv(), socket.sendContext, true, un);
        }
        Logger.alert("async connect job delivered");
    }

    private static void deliverAccept(io.vproxy.vfd.windows.WinIOCP iocp, WinSocket listenSocket) throws Exception {
        var acceptedFd = PosixNative.get().createIPv4TcpFD(VProxyThread.current().getEnv());
        Logger.alert("create a socket to handle accept event: " + acceptedFd);
        var acceptedSock = new WinSocket(acceptedFd, listenSocket);
        iocp.associate(acceptedSock);

        acceptedSock.incrIORefCnt();
        WindowsNative.get().acceptEx(VProxyThread.current().getEnv(),
            listenSocket.fd, acceptedSock.recvContext);

        Logger.alert("async accept job delivered");
    }

    private static void handleAccepted(io.vproxy.vfd.windows.WinIOCP iocp, WinSocket socket) throws Exception {
        deliverAccept(iocp, socket.listenSocket);

        WindowsNative.get().updateAcceptContext(VProxyThread.current().getEnv(), socket.listenSocket.fd, socket.fd);

        try (var allocator = Allocator.ofConfined()) {
            var st = PosixNative.get().getIPv4Remote(VProxyThread.current().getEnv(), (int) socket.fd.MEMORY.address(), allocator);
            var addr = new SocketAddressIPv4(st.getIp(), st.getPort() & 0xffff);
            Logger.alert("remote address is " + addr);
        }

        socket.recvContext.setIoType(IOType.READ.code);
        deliverRead(socket);
    }

    private static void deliverRead(WinSocket socket) throws IOException {
        socket.incrIORefCnt();
        WindowsNative.get().wsaRecv(VProxyThread.current().getEnv(), socket.recvContext);
        Logger.alert("async receive event delivered for " + socket);
    }

    private static void loop(io.vproxy.vfd.windows.WinIOCP iocp) {
        try (var allocator = Allocator.ofConfined()) {
            var entries = new OverlappedEntry.Array(allocator, 16);
            var normalEvents = new CursorList<OverlappedEntry>();
            var extraEvents = new CursorList<OverlappedEntry>();
            //noinspection InfiniteLoopStatement
            while (true) {
                normalEvents.clear();
                extraEvents.clear();
                try {
                    oneLoop(entries, normalEvents, extraEvents, iocp);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
        }
    }

    private static void oneLoop(OverlappedEntry.Array entries,
                                List<OverlappedEntry> normalEvents,
                                List<OverlappedEntry> extraEvents,
                                io.vproxy.vfd.windows.WinIOCP iocp) throws Exception {
        iocp.getQueuedCompletionStatusEx(entries, normalEvents, extraEvents, 16, -1, false);

        Logger.alert("IOCP got normal events: " + normalEvents.size() + ", and extra events: " + extraEvents.size());

        Logger.alert("---------------------------------");
        for (var entry : normalEvents) {
            var ctx = IOCPUtils.getIOContextOf(entry.getOverlapped());
            Logger.alert("received socket: " + ctx.getRef().getRef() + ", event: " + ctx.getIoType());
        }
        Logger.alert("---------------------------------");
        for (var entry : normalEvents) {
            var ctx = IOCPUtils.getIOContextOf(entry.getOverlapped());
            var socket = (WinSocket) ctx.getRef().getRef();
            if (socket.isClosed()) {
                Logger.alert(socket + " is closed, ignoring this event: " + ctx.getIoType());
                socket.decrIORefCnt();
                continue;
            }
            socket.decrIORefCnt();

            Logger.alert("last io operation error code = " + entry.getOverlapped().getInternal());
            if (entry.getOverlapped().getInternal() != 0) {
                socket.close();
                continue;
            }

            if (ctx.getIoType() == IOType.ACCEPT.code) {
                Logger.alert("socket accepted: " + socket);
                handleAccepted(iocp, socket);
            } else if (ctx.getIoType() == IOType.READ.code) {
                Logger.alert("socket async recv done: " + socket);

                var rcvLen = entry.getNumberOfBytesTransferred();
                Logger.alert("received " + rcvLen + " bytes");
                if (rcvLen == 0) {
                    Logger.warn(LogType.ALERT, socket + " is closed by remote");
                    socket.close();
                    continue;
                }

                var rbuf = ctx.getBuffers().get(0).getBuf().reinterpret(rcvLen);
                var data = new String(rbuf.toArray(ValueLayout.JAVA_BYTE)).trim();
                Logger.alert("received data: " + data);

                if (data.trim().equals("quit")) {
                    Logger.warn(LogType.ALERT, "closing " + socket);
                    socket.close();
                    continue;
                }

                var wbuf = socket.sendContext.getBuffers().get(0);
                wbuf.setBuf(socket.sendMemSeg);
                wbuf.getBuf().reinterpret(rcvLen).copyFrom(rbuf);
                wbuf.setLen(rcvLen);

                socket.incrIORefCnt();
                WindowsNative.get().wsaSend(VProxyThread.current().getEnv(), socket.sendContext);
                Logger.alert("async send event delivered");
            } else if (ctx.getIoType() == IOType.WRITE.code) {
                var sndLen = entry.getNumberOfBytesTransferred();
                Logger.alert("socket async send done: " + socket + ", wrote " + sndLen + " bytes");

                deliverRead(socket);
            } else if (ctx.getIoType() == IOType.CONNECT.code) {
                Logger.alert("socket connected: " + socket);

                socket.sendContext.setIoType(IOType.WRITE.code);
                var wbuf = socket.sendContext.getBuffers().get(0);
                wbuf.setBuf(socket.sendMemSeg);
                wbuf.getBuf().reinterpret(4).copyFrom(MemorySegment.ofArray("quit".getBytes()));
                wbuf.setLen(4);

                socket.incrIORefCnt();
                WindowsNative.get().wsaSend(VProxyThread.current().getEnv(), socket.sendContext);
                Logger.alert("client async send event delivered");
            } else {
                Logger.error(LogType.ALERT, "unknown io_type: " + ctx.getIoType());
            }
        }
    }
}
