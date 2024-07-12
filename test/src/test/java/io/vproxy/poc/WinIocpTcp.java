package io.vproxy.poc;

import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.vfd.*;
import io.vproxy.vfd.windows.WindowsFDs;

import java.nio.ByteBuffer;

@SuppressWarnings("resource")
public class WinIocpTcp {
    static FD connectFD;

    public static void main(String[] args) throws Exception {
        var fds = new WindowsFDs();
        var iocp = fds.openSelector();
        {
            Logger.alert("iocp = " + iocp);
            VProxyThread.create(() -> loop(iocp), "iocp-loop").start();
        }

        {
            var listenFd = fds.openServerSocketFD();
            Logger.alert("listenSocket = " + listenFd);

            listenFd.bind(new IPPort("127.0.0.1:8080"));

            iocp.register(listenFd, EventSet.read(), null);
        }

        Thread.sleep(2_000);

        {
            var fd = fds.openSocketFD();
            Logger.alert("connectSocket = " + fd);
            fd.connect(new IPPort("127.0.0.1:8080"));

            connectFD = fd;

            iocp.register(fd, EventSet.write(), null);
        }
    }

    private static void loop(FDSelector iocp) {
        var buf = ByteBuffer.allocateDirect(1024);
        //noinspection InfiniteLoopStatement
        while (true) {
            try {
                oneLoop(iocp, buf);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    private static void oneLoop(FDSelector iocp, ByteBuffer buf) throws Exception {
        var entries = iocp.select();

        Logger.alert("IOCP got " + entries.size() + " events");

        Logger.alert("---------------------------------");
        for (var entry : entries) {
            Logger.alert("fired socket: " + entry.fd() + ", event: " + entry.ready());
        }
        Logger.alert("---------------------------------");
        for (var entry : entries) {
            var fd = entry.fd();
            var ready = entry.ready();
            if (fd instanceof ServerSocketFD server) {
                if (ready.have(Event.READABLE)) {
                    var accepted = server.accept();
                    iocp.register(accepted, EventSet.read(), null);
                }
                continue;
            }

            var sock = (SocketFD) fd;
            if (ready.have(Event.READABLE)) {
                buf.limit(buf.capacity()).position(0);
                int n = sock.read(buf);
                Logger.alert("received " + n + " bytes");
                if (n == 0) {
                    Logger.warn(LogType.ALERT, sock + " is closed by remote");
                    fd.close();
                    continue;
                }

                buf.flip();
                var toStringBuf = new byte[n];
                buf.get(toStringBuf);
                var data = new String(toStringBuf);

                Logger.alert("received data: " + data);
                if (data.trim().equals("quit")) {
                    Logger.warn(LogType.ALERT, "closing " + fd);
                    fd.close();
                    continue;
                }

                buf.position(0);
                iocp.modify(sock, EventSet.write());
            }
            if (ready.have(Event.WRITABLE)) {
                if (connectFD == fd) {
                    sock.finishConnect();
                    var sndbuf = Utils.allocateByteBuffer(4);
                    sndbuf.put("quit".getBytes());
                    sndbuf.flip();
                    int n = sock.write(sndbuf);
                    Logger.alert("client " + fd + " worte " + n + " bytes");
                } else {
                    // use the buf which is already modified
                    int n = sock.write(buf);
                    Logger.alert("socket " + fd + " wrote " + n + " bytes");
                }
                iocp.modify(sock, EventSet.read());
            }
        }
    }
}
