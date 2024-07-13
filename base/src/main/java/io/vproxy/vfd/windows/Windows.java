package io.vproxy.vfd.windows;

import io.vproxy.vfd.IPPort;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

public interface Windows {
    boolean tapNonBlockingSupported() throws IOException;

    HANDLE createTapHandle(String dev) throws IOException;

    void closeHandle(SOCKET handle) throws IOException;

    void acceptEx(WinSocket socket) throws IOException;

    void updateAcceptContext(WinSocket socket) throws IOException;

    void tcpConnect(WinSocket socket, IPPort ipport) throws IOException;

    void wsaRecv(WinSocket socket) throws IOException;

    void wsaRecvFrom(WinSocket socket) throws IOException;

    void readFile(WinSocket socket) throws IOException;

    void wsaSend(WinSocket socket) throws IOException;

    void wsaSend(WinSocket socket, VIOContext ctx) throws IOException;

    void wsaSendTo(WinSocket socket, VIOContext ctx, IPPort ipport) throws IOException;

    void writeFile(WinSocket socket) throws IOException;

    void writeFile(WinSocket socket, VIOContext ctx) throws IOException;

    void wsaSendDisconnect(WinSocket socket) throws IOException;

    IPPort convertAddress(WinSocket socket, boolean v4) throws IOException;
}
