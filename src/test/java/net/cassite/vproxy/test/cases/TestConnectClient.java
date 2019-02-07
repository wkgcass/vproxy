package net.cassite.vproxy.test.cases;

import net.cassite.vproxy.component.check.CheckProtocol;
import net.cassite.vproxy.component.check.ConnectClient;
import net.cassite.vproxy.connection.NetEventLoop;
import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.test.tool.DirectCloseServer;
import net.cassite.vproxy.test.tool.IdServer;
import net.cassite.vproxy.test.tool.SendOnConnectIdServer;
import net.cassite.vproxy.util.BlockCallback;
import org.junit.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.InterruptedByTimeoutException;

import static org.junit.Assert.*;

public class TestConnectClient {
    private static SelectorEventLoop serverLoop;

    @BeforeClass
    public static void classSetUp() throws Exception {
        serverLoop = SelectorEventLoop.open();
        serverLoop.loop(r -> new Thread(r, "serverLoop"));
        NetEventLoop serverNetLoop = new NetEventLoop(serverLoop);
        new IdServer("0", serverNetLoop, 19080);
        new SendOnConnectIdServer("abcdefghijklmn"/*make it long to fill the buffer*/, serverNetLoop, 19081);
        new DirectCloseServer(serverNetLoop, 19082);
    }

    @AfterClass
    public static void classTearDown() throws Exception {
        Thread t = serverLoop.runningThread;
        serverLoop.close();
        t.join();
    }

    private NetEventLoop netEventLoop;

    @Before
    public void setUp() throws Exception {
        netEventLoop = new NetEventLoop(SelectorEventLoop.open());
        netEventLoop.getSelectorEventLoop().loop(r -> new Thread(r, "netEventLoop"));
    }

    @After
    public void tearDown() throws Exception {
        Thread.sleep(100);
        netEventLoop.getSelectorEventLoop().close();
    }

    @Test
    public void connectionSuccessToNormalServer() throws Exception {
        ConnectClient client = new ConnectClient(netEventLoop,
            new InetSocketAddress("127.0.0.1", 19080),
            InetAddress.getByName("127.0.0.1"),
            CheckProtocol.tcp,
            100);
        BlockCallback<Void, IOException> cb = new BlockCallback<>();
        client.handle(cb);
        cb.block(); // should success
    }

    @Test
    public void connectionWithDelaySuccessToNormalServer() throws Exception {
        ConnectClient client = new ConnectClient(netEventLoop,
            new InetSocketAddress("127.0.0.1", 19080),
            InetAddress.getByName("127.0.0.1"),
            CheckProtocol.tcpDelay,
            100);
        BlockCallback<Void, IOException> cb = new BlockCallback<>();
        client.handle(cb);
        cb.block(); // should success
    }

    @Test
    public void connectionSuccessToActivelyWriteServer() throws Exception {
        ConnectClient client = new ConnectClient(netEventLoop,
            new InetSocketAddress("127.0.0.1", 19081),
            InetAddress.getByName("127.0.0.1"),
            CheckProtocol.tcp,
            100);
        BlockCallback<Void, IOException> cb = new BlockCallback<>();
        client.handle(cb);
        cb.block(); // should success
    }

    @Test
    public void connectionWithDelaySuccessToActivelyWriteServer() throws Exception {
        ConnectClient client = new ConnectClient(netEventLoop,
            new InetSocketAddress("127.0.0.1", 19081),
            InetAddress.getByName("127.0.0.1"),
            CheckProtocol.tcpDelay,
            100);
        BlockCallback<Void, IOException> cb = new BlockCallback<>();
        client.handle(cb);
        cb.block(); // should success
    }

    @Test
    public void connectionSuccessToCloseServer() throws Exception {
        ConnectClient client = new ConnectClient(netEventLoop,
            new InetSocketAddress("127.0.0.1", 19082),
            InetAddress.getByName("127.0.0.1"),
            CheckProtocol.tcp,
            100);
        BlockCallback<Void, IOException> cb = new BlockCallback<>();
        client.handle(cb);
        cb.block(); // should success
    }

    @Test
    public void connectionWithDelayFailToCloseServer() throws Exception {
        ConnectClient client = new ConnectClient(netEventLoop,
            new InetSocketAddress("127.0.0.1", 19082),
            InetAddress.getByName("127.0.0.1"),
            CheckProtocol.tcpDelay,
            100);
        BlockCallback<Void, IOException> cb = new BlockCallback<>();
        client.handle(cb);
        try {
            cb.block();
            fail();
        } catch (IOException e) {
            assertEquals("remote closed", e.getMessage());
        }
    }

    @Test
    public void connectionFailWithReset() throws Exception {
        ConnectClient client = new ConnectClient(netEventLoop,
            new InetSocketAddress("127.0.0.1", 22222),
            InetAddress.getByName("127.0.0.1"),
            CheckProtocol.tcp,
            100);
        BlockCallback<Void, IOException> cb = new BlockCallback<>();
        client.handle(cb);
        try {
            cb.block();
            fail();
        } catch (IOException e) {
            assertEquals("Connection refused", e.getMessage());
        }
    }

    @Test
    public void connectionWithDelayFailWithReset() throws Exception {
        ConnectClient client = new ConnectClient(netEventLoop,
            new InetSocketAddress("127.0.0.1", 22222),
            InetAddress.getByName("127.0.0.1"),
            CheckProtocol.tcpDelay,
            100);
        BlockCallback<Void, IOException> cb = new BlockCallback<>();
        client.handle(cb);
        try {
            cb.block();
            fail();
        } catch (IOException e) {
            assertEquals("Connection refused", e.getMessage());
        }
    }

    @Test
    public void connectionFailWithUnreachable() throws Exception {
        ConnectClient client = new ConnectClient(netEventLoop,
            new InetSocketAddress("127.1.2.3", 22222),
            InetAddress.getByName("127.0.0.1"),
            CheckProtocol.tcp,
            100);
        BlockCallback<Void, IOException> cb = new BlockCallback<>();
        client.handle(cb);
        try {
            cb.block();
            fail();
        } catch (IOException e) {
            assertTrue(e instanceof InterruptedByTimeoutException);
        }
    }

    @Test
    public void connectionWithDelayFailWithUnreachable() throws Exception {
        ConnectClient client = new ConnectClient(netEventLoop,
            new InetSocketAddress("127.1.2.3", 22222),
            InetAddress.getByName("127.0.0.1"),
            CheckProtocol.tcpDelay,
            100);
        BlockCallback<Void, IOException> cb = new BlockCallback<>();
        client.handle(cb);
        try {
            cb.block();
            fail();
        } catch (IOException e) {
            assertTrue(e instanceof InterruptedByTimeoutException);
        }
    }
}
