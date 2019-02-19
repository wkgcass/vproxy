package net.cassite.vproxy.test.cases;

import net.cassite.vproxy.connection.*;
import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.test.tool.UDPClient;
import net.cassite.vproxy.test.tool.UDPIdServer;
import net.cassite.vproxy.util.ByteArrayChannel;
import net.cassite.vproxy.util.RingBuffer;
import net.cassite.vproxy.util.Tuple;
import org.junit.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.NetworkChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@SuppressWarnings("Duplicates")
public class TestUDP {
    private static SelectorEventLoop serverLoop;

    @BeforeClass
    public static void classSetUp() throws Exception {
        serverLoop = SelectorEventLoop.open();
        NetEventLoop netEventLoop = new NetEventLoop(serverLoop);
        new UDPIdServer("0", netEventLoop, 19080);
        serverLoop.loop(r -> new Thread(r, "ServerLoop"));
    }

    @AfterClass
    public static void classTearDown() throws Exception {
        serverLoop.close();
    }

    private SelectorEventLoop loop;
    private NetEventLoop netEventLoop;
    private volatile boolean finalizing = false;
    private final AtomicInteger step = new AtomicInteger(0);
    private int step2 = 0;

    @Before
    public void setUp() throws Exception {
        loop = SelectorEventLoop.open();
        netEventLoop = new NetEventLoop(loop);
        loop.loop(r -> new Thread(r, "loop"));
    }

    @After
    public void tearDown() throws Exception {
        finalizing = true;
        loop.close();
    }

    @Test
    public void simpleServer() throws Exception {
        BindServer udpServer = BindServer.createUDP(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 18080));
        netEventLoop.addServer(udpServer, null, new ServerHandler() {
            @Override
            public void acceptFail(ServerHandlerContext ctx, IOException err) {
                fail("acceptFail() will not fire");
            }

            @Override
            public void connection(ServerHandlerContext ctx, Connection connection) {
                step.incrementAndGet();
            }

            @Override
            public Tuple<RingBuffer, RingBuffer> getIOBuffers(NetworkChannel channel) {
                return new Tuple<>(RingBuffer.allocateDirect(32), RingBuffer.allocateDirect(32));
            }

            @Override
            public void removed(ServerHandlerContext ctx) {
                if (!finalizing) {
                    fail("the loop not closed, removed() should not fire");
                }
            }

            @Override
            public void exception(ServerHandlerContext ctx, IOException err) {
                fail("should not get exception");
            }

            @Override
            public ConnectionHandler udpHandler(ServerHandlerContext ctx, Connection conn) {
                return new ConnectionHandler() {
                    @Override
                    public void readable(ConnectionHandlerContext ctx) {
                        byte[] bytes = new byte[32];
                        ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(bytes);
                        int size = ctx.connection.inBuffer.writeTo(chnl);
                        String s = new String(bytes, 0, size, StandardCharsets.UTF_8);
                        assertEquals("hello", s);

                        chnl = ByteArrayChannel.from(bytes, 0, size, 0);
                        ctx.connection.outBuffer.storeBytesFrom(chnl);
                        ++step2;
                    }

                    @Override
                    public void writable(ConnectionHandlerContext ctx) {

                    }

                    @Override
                    public void exception(ConnectionHandlerContext ctx, IOException err) {
                        fail("exception fired");
                    }

                    @Override
                    public void closed(ConnectionHandlerContext ctx) {
                        // ignore
                    }

                    @Override
                    public void removed(ConnectionHandlerContext ctx) {
                        // ignore
                    }
                };
            }
        });

        int clientCount = 100;

        for (int i = 0; i < clientCount; ++i) {
            UDPClient client = new UDPClient(18080);
            client.connect();
            String res = client.sendAndRecv("hello");
            System.out.println(res);
        }

        assertEquals(clientCount, step.get());
        assertEquals(clientCount, step2);
    }

    @Test
    public void udpClient() throws Exception {
        ClientConnection udp = ClientConnection.createUDP(
            new InetSocketAddress("127.0.0.1", 19080),
            new InetSocketAddress("127.0.0.1", 0),
            RingBuffer.allocate(32), RingBuffer.allocate(32)
        );
        netEventLoop.addClientConnection(udp, null, new ClientConnectionHandler() {
            @Override
            public void connected(ClientConnectionHandlerContext ctx) {
                fail("should not fire connected()");
            }

            @Override
            public void readable(ConnectionHandlerContext ctx) {
                byte[] bytes = new byte[32];
                ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(bytes);
                int size = ctx.connection.inBuffer.writeTo(chnl);
                assertEquals("should get only 1 byte", 1, size);
                assertEquals("result should be 0", "0", new String(bytes, 0, 1, StandardCharsets.UTF_8));
                step.incrementAndGet();

                // then send again
                if (step.get() >= 100) {
                    return;
                }
                bytes = "anything".getBytes();
                chnl = ByteArrayChannel.fromFull(bytes);
                size = udp.outBuffer.storeBytesFrom(chnl);
                assertEquals(8, size);
            }

            @Override
            public void writable(ConnectionHandlerContext ctx) {
                // ignore
            }

            @Override
            public void exception(ConnectionHandlerContext ctx, IOException err) {
                ctx.connection.close();
                fail("should not get exception");
            }

            @Override
            public void closed(ConnectionHandlerContext ctx) {
                // ignore
            }

            @Override
            public void removed(ConnectionHandlerContext ctx) {
                ctx.connection.close();
            }
        });

        byte[] bytes = "anything".getBytes();
        ByteArrayChannel chnl = ByteArrayChannel.fromFull(bytes);
        int size = udp.outBuffer.storeBytesFrom(chnl);
        assertEquals(8, size);

        while (step.get() < 100) {
            Thread.sleep(1);
        }
    }
}
