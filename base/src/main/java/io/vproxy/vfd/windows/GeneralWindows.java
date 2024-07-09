package io.vproxy.vfd.windows;

import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.PNIString;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.posix.SocketAddressIPv4;
import io.vproxy.vfd.posix.SocketAddressIPv6;
import io.vproxy.vfd.posix.SocketAddressUnion;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

public class GeneralWindows implements Windows {
    @Override
    public boolean tapNonBlockingSupported() throws IOException {
        return WindowsNative.get().tapNonBlockingSupported(VProxyThread.current().getEnv());
    }

    @Override
    public SOCKET createTapHandle(String dev) throws IOException {
        try (var allocator = Allocator.ofPooled()) {
            return WindowsNative.get().createTapHandle(VProxyThread.current().getEnv(), new PNIString(allocator, dev));
        }
    }

    @Override
    public void closeHandle(SOCKET fd) throws IOException {
        WindowsNative.get().closeHandle(VProxyThread.current().getEnv(), fd);
    }

    @Override
    public void acceptEx(WinSocket socket) throws IOException {
        socket.incrIORefCnt();
        try {
            WindowsNative.get().acceptEx(VProxyThread.current().getEnv(),
                socket.listenSocket.fd, socket.recvContext);
        } catch (IOException e) {
            socket.decrIORefCnt();
            throw e;
        }
    }

    @Override
    public void updateAcceptContext(WinSocket socket) throws IOException {
        WindowsNative.get().updateAcceptContext(VProxyThread.current().getEnv(),
            socket.listenSocket.fd, socket.fd);
    }

    private interface HandleSocketAddress {
        void handle(boolean v4, SocketAddressUnion un) throws IOException;
    }

    private static void handleSocketAddress(IPPort ipport, HandleSocketAddress f) throws IOException {
        try (var allocator = Allocator.ofConfined()) {
            boolean v4;
            var un = new SocketAddressUnion(allocator);
            if (ipport.getAddress() instanceof IPv4 ipv4) {
                v4 = true;
                un.getV4().setIp(ipv4.getIPv4Value());
                un.getV4().setPort((short) ipport.getPort());
            } else {
                v4 = false;
                un.getV6().setIp(ipport.getAddress().formatToIPString());
                un.getV6().setPort((short) ipport.getPort());
            }
            f.handle(v4, un);
        }
    }

    @Override
    public void tcpConnect(WinSocket socket, IPPort ipport) throws IOException {
        handleSocketAddress(ipport, (v4, un) -> {
            socket.incrIORefCnt();
            try {
                WindowsNative.get().tcpConnect(VProxyThread.current().getEnv(), socket.sendContext, v4, un);
            } catch (IOException e) {
                socket.decrIORefCnt();
                throw e;
            }
        });
    }

    @Override
    public void wsaRecv(WinSocket socket) throws IOException {
        socket.incrIORefCnt();
        try {
            WindowsNative.get().wsaRecv(VProxyThread.current().getEnv(), socket.recvContext);
        } catch (IOException e) {
            socket.decrIORefCnt();
            throw e;
        }
    }

    @Override
    public void wsaRecvFrom(WinSocket socket) throws IOException {
        socket.incrIORefCnt();
        try {
            WindowsNative.get().wsaRecvFrom(VProxyThread.current().getEnv(), socket.recvContext);
        } catch (IOException e) {
            socket.decrIORefCnt();
            throw e;
        }
    }

    @Override
    public void wsaSend(WinSocket socket) throws IOException {
        socket.incrIORefCnt();
        try {
            WindowsNative.get().wsaSend(VProxyThread.current().getEnv(), socket.sendContext);
        } catch (IOException e) {
            socket.decrIORefCnt();
            throw e;
        }
    }

    @Override
    public void wsaSendTo(WinSocket socket, MemorySegment data, IPPort ipport) throws IOException {
        var ctx = IOCPUtils.buildContextForSendingUDPPacket(socket, data);
        handleSocketAddress(ipport, (v4, un) -> {
            socket.incrIORefCnt();
            try {
                WindowsNative.get().wsaSendTo(VProxyThread.current().getEnv(), ctx, v4, un);
            } catch (IOException e) {
                socket.decrIORefCnt();
                throw e;
            }
        });
    }

    @Override
    public void wsaSendDisconnect(WinSocket socket) throws IOException {
        WindowsNative.get().wsaSendDisconnect(VProxyThread.current().getEnv(), socket.fd);
    }

    @Override
    public IPPort convertAddress(WinSocket socket, boolean v4) throws IOException {
        try (var allocator = Allocator.ofConfined()) {
            var un = new SocketAddressUnion(allocator);
            WindowsNative.get().convertAddress(VProxyThread.current().getEnv(),
                socket.recvContext.getAddr().MEMORY, v4, un);
            if (v4) {
                var addr = new SocketAddressIPv4(un.getV4().getIp(), un.getV6().getPort() & 0xffff);
                return addr.toIPPort();
            } else {
                var addr = new SocketAddressIPv6(un.getV6().getIp(), un.getV6().getPort() & 0xffff);
                return addr.toIPPort();
            }
        }
    }
}
