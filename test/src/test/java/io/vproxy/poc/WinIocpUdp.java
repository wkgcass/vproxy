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

public class WinIocpUdp {
    private static WinSocket listenSocket;

    public static void main(String[] args) throws Exception {
        System.loadLibrary("pni");
        System.loadLibrary("vfdwindows");

        var iocp = new WinIOCP();
        {
            Logger.alert("iocp = " + iocp);
            VProxyThread.create(() -> loop(iocp), "iocp-loop").start();
        }

        {
            var listenFd = PosixNative.get().createIPv4UdpFD(VProxyThread.current().getEnv());
            listenSocket = WinSocket.ofUdp(listenFd);
            Logger.alert("listenSocket = " + listenSocket);

            PosixNative.get().bindIPv4(VProxyThread.current().getEnv(),
                listenFd, IP.fromIPv4("127.0.0.1").getIPv4Value(), 8080);

            iocp.associate(listenSocket);
            deliverRecvFrom(listenSocket);
        }

        Thread.sleep(2_000);

        {
            var fd = PosixNative.get().createIPv4UdpFD(VProxyThread.current().getEnv());
            var connectSocket = WinSocket.ofUdp(fd);
            Logger.alert("connectSocket = " + connectSocket);
            iocp.associate(connectSocket);

            PosixNative.get().connectIPv4(VProxyThread.current().getEnv(),
                fd, IP.fromIPv4("127.0.0.1").getIPv4Value(), 8080);

            deliverSend(connectSocket);
            deliverRecv(connectSocket);
        }
    }

    private static void deliverRecv(WinSocket connectSocket) throws IOException {
        connectSocket.incrIORefCnt();
        WindowsNative.get().wsaRecv(VProxyThread.current().getEnv(), connectSocket.recvContext);
        Logger.alert("async recv job delivered");
    }

    private static void deliverSend(WinSocket socket) throws IOException {
        var sendContext = IOCPUtils.buildContextForSendingUDPPacket(socket, 5);
        sendContext.getBuffers().get(0).getBuf().reinterpret(5).copyFrom(MemorySegment.ofArray("hello".getBytes()));

        socket.incrIORefCnt();
        WindowsNative.get().wsaSend(VProxyThread.current().getEnv(), sendContext);
        Logger.alert("async send job delivered");
    }

    private static void deliverRecvFrom(WinSocket udpServerSock) throws Exception {
        udpServerSock.incrIORefCnt();
        WindowsNative.get().wsaRecvFrom(VProxyThread.current().getEnv(), udpServerSock.recvContext);
        Logger.alert("async recvFrom job delivered");
    }

    private static void handleRecvFrom(WinSocket socket, int len) throws Exception {
        try (var allocator = Allocator.ofConfined()) {
            var un = new SocketAddressUnion(allocator);
            WindowsNative.get().convertAddress(VProxyThread.current().getEnv(),
                socket.recvContext.getAddr().MEMORY, true, un);
            var addr = new SocketAddressIPv4(un.getV4().getIp(), un.getV4().getPort() & 0xffff);

            var bytes = socket.recvMemSeg.reinterpret(len).toArray(ValueLayout.JAVA_BYTE);
            var str = new String(bytes);
            Logger.alert("received udp packet: " + str + ", from " + addr);

            deliverRecvFrom(socket);

            var ctx = IOCPUtils.buildContextForSendingUDPPacket(socket, len);
            ctx.getBuffers().get(0).getBuf().reinterpret(len).copyFrom(socket.recvMemSeg.reinterpret(len));

            socket.incrIORefCnt();
            WindowsNative.get().wsaSendTo(VProxyThread.current().getEnv(),
                ctx, true, un);
            Logger.alert("async sendTo event delivered");
        }
    }

    private static void loop(WinIOCP iocp) {
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
                                WinIOCP iocp) throws Exception {
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

            var ntstatus = entry.getOverlapped().getInternal();
            Logger.alert("last io operation ntstatus = " + ntstatus);
            if (ntstatus != 0) {
                deliverRecvFrom(socket);
                continue;
            }

            if (ctx.getIoType() == IOType.READ.code) {
                if (socket == listenSocket) {
                    Logger.alert("socket async recvFrom done: " + socket);

                    var rcvLen = entry.getNumberOfBytesTransferred();
                    handleRecvFrom(socket, rcvLen);
                } else {
                    Logger.alert("socket async recv done: " + socket);

                    var rcvLen = entry.getNumberOfBytesTransferred();
                    var str = new String(socket.recvMemSeg.reinterpret(rcvLen).toArray(ValueLayout.JAVA_BYTE));
                    Logger.alert(socket + " received data: " + str);

                    socket.close();
                }
            } else {
                Logger.error(LogType.ALERT, "unknown io_type: " + ctx.getIoType());
            }
        }
    }
}
