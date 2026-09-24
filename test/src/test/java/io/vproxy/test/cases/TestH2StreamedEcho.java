package io.vproxy.test.cases;

import io.vproxy.base.GlobalInspection;
import io.vproxy.base.connection.*;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.selector.wrap.h2streamed.H2StreamedClientFDs;
import io.vproxy.base.selector.wrap.h2streamed.H2StreamedServerFDs;
import io.vproxy.base.selector.wrap.kcp.KCPFDs;
import io.vproxy.base.selector.wrap.udp.UDPBasedFDs;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.SocketFD;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestH2StreamedEcho {
    private static final int PORT = 28180;

    @Test
    public void echoKcp() throws Exception {
        List<String> metricsBefore = transportMetrics();
        SelectorEventLoop selectorLoop = SelectorEventLoop.open();
        try {
            KCPFDs kcpFDs = KCPFDs.getFast3();
            echo(selectorLoop, kcpFDs, kcpFDs);
        } finally {
            selectorLoop.close();
        }
        assertEquals("transport metrics leaked after loop shutdown", metricsBefore, transportMetrics());
    }

    @Test
    public void echoH2Streamed() throws Exception {
        List<String> metricsBefore = transportMetrics();
        SelectorEventLoop selectorLoop = SelectorEventLoop.open();
        H2StreamedServerFDs serverFDs = null;
        H2StreamedClientFDs clientFDs = null;
        try {
            KCPFDs kcpFDs = KCPFDs.getFast3();
            serverFDs = new H2StreamedServerFDs(kcpFDs, selectorLoop, new IPPort(PORT));
            clientFDs = new H2StreamedClientFDs(kcpFDs, selectorLoop,
                new IPPort(IP.from("127.0.0.1"), PORT));
            echo(selectorLoop, serverFDs, clientFDs);
        } finally {
            stop(clientFDs);
            stop(serverFDs);
            selectorLoop.close();
        }
        assertEquals("transport metrics leaked after loop shutdown", metricsBefore, transportMetrics());
    }

    private static void stop(Object fds) throws Exception {
        if (fds == null) {
            return;
        }
        Method stop = fds.getClass().getSuperclass().getDeclaredMethod("stop");
        stop.setAccessible(true);
        stop.invoke(fds);
    }

    private void echo(SelectorEventLoop selectorLoop, UDPBasedFDs serverFds, UDPBasedFDs clientFds) throws Exception {
        NetEventLoop netLoop = new NetEventLoop(selectorLoop);
        ServerSock server = ServerSock.createUDP(new IPPort(PORT), selectorLoop, serverFds);
        netLoop.addServer(server, null, new EchoServerHandler());
        VProxyThread.create(selectorLoop::loop, "echo-test-loop").start();

        // the streamed fds need some time to finish handshaking
        IPPort remote = new IPPort(IP.from("127.0.0.1"), PORT);
        ConnectableConnection conn = null;
        try {
            long start = System.currentTimeMillis();
            while (conn == null) {
                assertTrue("the arq connection is not ready in time", System.currentTimeMillis() - start < 10_000);
                RingBuffer in = RingBuffer.allocateDirect(128 * 1024);
                RingBuffer out = RingBuffer.allocateDirect(128 * 1024);
                try {
                    conn = ConnectableConnection.createUDP(remote, new ConnectionOpts(), in, out, selectorLoop, clientFds);
                } catch (IOException ignore) {
                    in.clean();
                    out.clean();
                    Thread.sleep(100);
                }
            }

            byte[] expected = buildData();
            ClientHandler handler = new ClientHandler(expected);
            netLoop.addConnectableConnection(conn, null, handler);
            assertTrue("echo did not finish in time", handler.done.await(60, TimeUnit.SECONDS));
            assertFalse("echoed data mismatch (read " + handler.readPos + "/" + expected.length + ")", handler.failed.get());
        } finally {
            if (conn != null) {
                conn.close();
            }
            server.close();
        }
    }

    private static List<String> transportMetrics() {
        return GlobalInspection.getInstance().getPrometheusString().lines()
            .filter(line -> line.startsWith("streamed_") || line.startsWith("server_datagram_fd_"))
            .sorted().toList();
    }

    private static byte[] buildData() {
        // Exercise a large echo with unaligned frame boundaries. Deterministic
        // write()==0 coverage lives in TestArqStreamedLifecycle.
        Random random = new Random(12345);
        int total = 0;
        for (int i = 0; i < 1500; ++i) {
            total += 1 + random.nextInt(1400);
        }
        for (int i = 0; i < 20; ++i) {
            total += 16_384;
        }
        for (int i = 0; i < 3; ++i) {
            total += 400_000;
        }
        byte[] data = new byte[total];
        new Random(12345).nextBytes(data);
        return data;
    }

    private static class EchoServerHandler implements ServerHandler {
        @Override
        public void acceptFail(ServerHandlerContext ctx, IOException err) {
            err.printStackTrace();
        }

        @Override
        public void connection(ServerHandlerContext ctx, Connection connection) {
            try {
                ctx.eventLoop.addConnection(connection, null, new EchoConnectionHandler());
            } catch (IOException e) {
                connection.close();
            }
        }

        @Override
        public Tuple<RingBuffer, RingBuffer> getIOBuffers(SocketFD channel) {
            // the same buffer for input and output, so the data is echoed automatically
            RingBuffer buffer = RingBuffer.allocateDirect(128 * 1024);
            return new Tuple<>(buffer, buffer);
        }

        @Override
        public void removed(ServerHandlerContext ctx) {
            ctx.server.close();
        }
    }

    private static class EchoConnectionHandler implements ConnectionHandler {
        @Override
        public void readable(ConnectionHandlerContext ctx) {
            // the input and output buffer are the same, nothing to do
        }

        @Override
        public void writable(ConnectionHandlerContext ctx) {
            // see comment in readable
        }

        @Override
        public void exception(ConnectionHandlerContext ctx, IOException err) {
            System.err.println("server connection got exception: " + err);
            err.printStackTrace();
            ctx.connection.close();
        }

        @Override
        public void remoteClosed(ConnectionHandlerContext ctx) {
            ctx.connection.close();
        }

        @Override
        public void closed(ConnectionHandlerContext ctx) {
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            ctx.connection.close();
        }
    }

    private static class ClientHandler implements ConnectableConnectionHandler {
        private final byte[] expected;
        private final CountDownLatch done = new CountDownLatch(1);
        private final AtomicBoolean failed = new AtomicBoolean(false);
        private final ByteBuffer readTmp = ByteBuffer.allocate(128 * 1024);
        private int writePos = 0;
        private int readPos = 0;
        // the writable callback may be reentered inside storeBytesFrom (QuickWrite triggers
        // the buffer readableET callback), so guard the writePos accounting against reentrancy
        private boolean writing = false;

        private ClientHandler(byte[] expected) {
            this.expected = expected;
        }

        private void writeMore(ConnectionHandlerContext ctx) {
            if (writing) {
                return;
            }
            writing = true;
            try {
                var out = ctx.connection.getOutBuffer();
                while (writePos < expected.length && out.free() > 0) {
                    ByteBuffer bb = ByteBuffer.wrap(expected, writePos, Math.min(out.free(), expected.length - writePos));
                    int n = out.storeBytesFrom(bb);
                    if (n <= 0) {
                        return;
                    }
                    writePos += n;
                    if (writePos > expected.length) {
                        throw new IllegalStateException("writePos=" + writePos + " > " + expected.length + ", n=" + n + ", free=" + out.free() + ", bb.remaining=" + bb.remaining());
                    }
                }
            } finally {
                writing = false;
            }
        }

        @Override
        public void connected(ConnectableConnectionHandlerContext ctx) {
            writeMore(ctx);
        }

        @Override
        public void writable(ConnectionHandlerContext ctx) {
            writeMore(ctx);
        }

        @Override
        public void readable(ConnectionHandlerContext ctx) {
            var in = ctx.connection.getInBuffer();
            while (in.used() > 0) {
                readTmp.clear();
                int n = in.writeTo(readTmp);
                if (n <= 0) {
                    return;
                }
                readTmp.flip();
                for (int i = 0; i < n; ++i) {
                    if (readTmp.get() != expected[readPos + i]) {
                        int pos = readPos + i;
                        System.err.println("mismatch at " + pos + " (writePos=" + writePos + ", readPos=" + readPos + ", total=" + expected.length + ")");
                        StringBuilder expSb = new StringBuilder();
                        StringBuilder actSb = new StringBuilder();
                        for (int j = Math.max(0, i - 8); j < Math.min(n, i + 8); ++j) {
                            expSb.append(String.format("%02x ", expected[readPos + j]));
                            if (j == i) {
                                actSb.append(String.format("%02x ", readTmp.get(j)));
                            } else {
                                actSb.append(".. ");
                            }
                        }
                        System.err.println("expected: " + expSb);
                        System.err.println("actual  : " + actSb + "(.. are matched bytes)");
                        failed.set(true);
                        done.countDown();
                        return;
                    }
                }
                readPos += n;
            }
            if (readPos >= expected.length) {
                done.countDown();
            }
        }

        @Override
        public void exception(ConnectionHandlerContext ctx, IOException err) {
            System.err.println("client connection got exception: " + err);
            err.printStackTrace();
            failed.set(true);
            done.countDown();
        }

        @Override
        public void remoteClosed(ConnectionHandlerContext ctx) {
            System.err.println("client remoteClosed at readPos=" + readPos + "/" + expected.length);
            failed.set(readPos < expected.length);
            done.countDown();
        }

        @Override
        public void closed(ConnectionHandlerContext ctx) {
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            ctx.connection.close();
        }
    }
}
