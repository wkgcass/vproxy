package io.vproxy.test.cases;

import org.junit.*;
import io.vproxy.base.component.check.AnnotatedHcConfig;
import io.vproxy.base.component.check.CheckProtocol;
import io.vproxy.base.component.check.ConnectClient;
import io.vproxy.base.component.check.ConnectResult;
import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.callback.BlockCallback;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.test.tool.DirectCloseServer;
import io.vproxy.test.tool.IdServer;
import io.vproxy.test.tool.SendOnConnectIdServer;
import io.vproxy.vfd.IPPort;

import java.io.IOException;
import java.nio.channels.InterruptedByTimeoutException;

import static org.junit.Assert.*;

public class TestConnectClient {
    private static final int normalServerPort = 19080;
    private static final int directWritePort = 19081;
    private static final int directClosePort = 19082;

    private static SelectorEventLoop serverLoop;

    @BeforeClass
    public static void classSetUp() throws Exception {
        serverLoop = SelectorEventLoop.open();
        serverLoop.loop(r -> VProxyThread.create(r, "serverLoop"));
        NetEventLoop serverNetLoop = new NetEventLoop(serverLoop);
        new IdServer("0", serverNetLoop, normalServerPort);
        new SendOnConnectIdServer("abcdefghijklmn"/*make it long to fill the buffer*/, serverNetLoop, directWritePort);
        new DirectCloseServer(serverNetLoop, directClosePort);
    }

    @AfterClass
    public static void classTearDown() throws Exception {
        Thread t = serverLoop.getRunningThread();
        serverLoop.close();
        t.join();
    }

    private NetEventLoop netEventLoop;

    @Before
    public void setUp() throws Exception {
        netEventLoop = new NetEventLoop(SelectorEventLoop.open());
        netEventLoop.getSelectorEventLoop().loop(r -> VProxyThread.create(r, "netEventLoop"));
    }

    @After
    public void tearDown() throws Exception {
        Thread.sleep(100);
        netEventLoop.getSelectorEventLoop().close();
    }

    private void doConnect(CheckProtocol protocol, int port) throws Exception {
        doConnect("127.0.0.1", protocol, port);
    }

    private void doConnect(String targetAddress, CheckProtocol protocol, int port) throws Exception {
        ConnectClient client = new ConnectClient(netEventLoop,
            new IPPort(targetAddress, port),
            protocol,
            100,
            new AnnotatedHcConfig());
        BlockCallback<ConnectResult, IOException> cb = new BlockCallback<>();
        client.handle(cb);
        cb.block();
    }

    @Test
    public void connectionSuccessToNormalServer() throws Exception {
        doConnect(CheckProtocol.tcp, normalServerPort);
        // should success
    }

    @Test
    public void connectionWithDelaySuccessToNormalServer() throws Exception {
        doConnect(CheckProtocol.tcpDelay, normalServerPort);
        // should success
    }

    @Test
    public void connectionSuccessToActivelyWriteServer() throws Exception {
        doConnect(CheckProtocol.tcp, directWritePort);
        // should success
    }

    @Test
    public void connectionWithDelaySuccessToActivelyWriteServer() throws Exception {
        doConnect(CheckProtocol.tcpDelay, directWritePort);
        // should success
    }

    @Test
    public void connectionSuccessToCloseServer() throws Exception {
        doConnect(CheckProtocol.tcp, directClosePort);
        // should success
    }

    @Test
    public void connectionWithDelayFailToCloseServer() throws Exception {
        try {
            doConnect(CheckProtocol.tcpDelay, directClosePort);
            fail();
        } catch (IOException e) {
            assertEquals("remote closed", e.getMessage());
        }
    }

    @Test
    public void connectionFailWithReset() throws Exception {
        try {
            doConnect(CheckProtocol.tcp, 22222);
            fail();
        } catch (IOException e) {
            assertEquals("Connection refused", e.getMessage());
        }
    }

    @Test
    public void connectionWithDelayFailWithReset() throws Exception {
        try {
            doConnect(CheckProtocol.tcpDelay, 22222);
            fail();
        } catch (IOException e) {
            assertEquals("Connection refused", e.getMessage());
        }
    }

    @Test
    public void connectionFailWithUnreachable() throws Exception {
        try {
            doConnect("1.1.1.1", CheckProtocol.tcp, 22222);
            fail();
        } catch (IOException e) {
            assertTrue(e instanceof InterruptedByTimeoutException);
        }
    }

    @Test
    public void connectionWithDelayFailWithUnreachable() throws Exception {
        try {
            doConnect("1.1.1.1", CheckProtocol.tcpDelay, 22222);
            fail();
        } catch (IOException e) {
            assertTrue(e instanceof InterruptedByTimeoutException);
        }
    }
}
