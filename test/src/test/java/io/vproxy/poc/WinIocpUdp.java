package io.vproxy.poc;

import io.vproxy.base.util.Logger;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.vfd.*;
import io.vproxy.vfd.windows.*;

import java.nio.ByteBuffer;

@SuppressWarnings("resource")
public class WinIocpUdp {
    private static DatagramFD listenSocket;

    public static void main(String[] args) throws Exception {
        var windowsFDs = new WindowsFDs();

        var iocp = windowsFDs.openSelector();
        {
            Logger.alert("iocp = " + iocp);
            VProxyThread.create(() -> loop(iocp), "iocp-loop").start();
        }

        {
            listenSocket = windowsFDs.openDatagramFD();

            listenSocket.bind(new IPPort("127.0.0.1:8080"));
            Logger.alert("listenSocket = " + listenSocket);

            iocp.register(listenSocket, EventSet.read(), null);
        }

        Thread.sleep(2_000);

        {
            var fd = windowsFDs.openDatagramFD();

            fd.connect(new IPPort("127.0.0.1:8080"));
            Logger.alert("connectSocket = " + fd);

            iocp.register(fd, EventSet.read(), null);
            var buf = ByteBuffer.wrap("hello".getBytes());
            fd.write(buf);
        }
    }

    private static void loop(FDSelector iocp) {
        //noinspection InfiniteLoopStatement
        while (true) {
            try {
                oneLoop(iocp);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    private static void oneLoop(FDSelector iocp) throws Exception {
        var entries = iocp.select();

        Logger.alert("IOCP got normal events: " + entries.size());

        Logger.alert("---------------------------------");
        for (var entry : entries) {
            Logger.alert(entry.fd() + " fires events: " + entry.ready());
        }
        Logger.alert("---------------------------------");
        for (var entry : entries) {
            var socket = entry.fd();
            if (!socket.isOpen()) {
                Logger.alert(socket + " is closed, ignoring this event: " + socket);
                continue;
            }

            var udpFD = (DatagramFD) socket;
            var buf = ByteBuffer.allocate(1024);
            if (socket == listenSocket) {
                var ipport = udpFD.receive(buf);
                var data = new String(buf.array(), 0, buf.position());
                Logger.alert("server received udp packet from " + ipport + ": " + data);
                buf.flip();
                udpFD.send(buf, ipport);
            } else {
                udpFD.read(buf);
                var data = new String(buf.array(), 0, buf.position());
                Logger.alert("client received udp packet from server : " + data);
                iocp.remove(udpFD);
                udpFD.close();
            }
        }
    }
}
