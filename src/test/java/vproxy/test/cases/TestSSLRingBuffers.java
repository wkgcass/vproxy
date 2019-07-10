package vproxy.test.cases;

import vproxy.connection.*;
import vproxy.dns.Resolver;
import vproxy.http.HttpRespParser;
import vproxy.processor.http.entity.Response;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.BlockCallback;
import vproxy.util.ByteArrayChannel;
import vproxy.util.RingBuffer;
import vproxy.util.RingBufferETHandler;
import vproxy.util.ringbuffer.SSLUnwrapRingBuffer;
import vproxy.util.ringbuffer.SSLUtils;
import vproxy.util.ringbuffer.SSLWrapRingBuffer;
import vproxy.util.ringbuffer.SimpleRingBuffer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.Assert.*;

public class TestSSLRingBuffers {
    @BeforeClass
    public static void setUpClass() {
        System.setProperty("javax.net.debug", "all");
    }

    @AfterClass
    public static void tearDownClass() {
        System.setProperty("javax.net.debug", "");
    }

    SelectorEventLoop selectorEventLoop;

    @Test
    public void requestSite() throws Exception {
        String url = "https://ip.cn";
        String host = url.substring("https://".length());
        int port = 443;
        BlockCallback<InetAddress, UnknownHostException> cb = new BlockCallback<>();
        Resolver.getDefault().resolveV4(host, cb);
        InetAddress inet;
        try {
            inet = cb.block();
        } catch (UnknownHostException e) {
            System.out.println("we are not able to resolve the address, " +
                "may be network is wrong, we ignore this case");
            return;
        }
        // make a socket to test whether it's accessible
        {
            try (Socket sock = new Socket()) {
                sock.connect(new InetSocketAddress(inet, port));
            } catch (IOException e) {
                System.out.println("we cannot connect to the remote," +
                    "may be network is wrong, we ignore this case");
                return;
            }
        }

        // create loop
        selectorEventLoop = SelectorEventLoop.open();
        NetEventLoop loop = new NetEventLoop(selectorEventLoop);

        // create connection
        SSLContext ctx = SSLContext.getInstance("TLSv1.2");
        ctx.init(null, null, null);
        SSLEngine engine = ctx.createSSLEngine(host, port);
        engine.setUseClientMode(true);
        SSLUtils.SSLBufferPair pair = SSLUtils.genbuf(
            engine,
            RingBuffer.allocate(16384),
            RingBuffer.allocate(16384),
            selectorEventLoop
        );
        ClientConnection conn = ClientConnection.create(
            new InetSocketAddress(inet, port),
            ConnectionOpts.getDefault(),
            pair.left, pair.right
        );
        loop.addClientConnection(conn, null, new MySSLClientConnectionHandler());

        // start
        selectorEventLoop.loop();
    }

    class MySSLClientConnectionHandler implements ClientConnectionHandler {
        private final ByteArrayChannel chnl;
        private final HttpRespParser parser;

        MySSLClientConnectionHandler() {
            chnl = ByteArrayChannel.fromFull(("" +
                "GET / HTTP/1.1\r\n" +
                "Host: ip.cn\r\n" +
                "User-Agent: vproxy\r\n" + // add an agent, otherwise it will return 403
                "\r\n").getBytes());
            parser = new HttpRespParser(true);
        }

        @Override
        public void connected(ClientConnectionHandlerContext ctx) {
            // send http request
            ctx.connection.getOutBuffer().storeBytesFrom(chnl);
        }

        @Override
        public void readable(ConnectionHandlerContext ctx) {
            int res = parser.feed(ctx.connection.getInBuffer());
            if (res != 0) {
                String err = parser.getErrorMessage();
                assertNull("got error: " + err, err);
                return;
            }
            // headers done
            Response resp = parser.getResult();
            assertEquals("failed: " + resp.toString(), 200, resp.statusCode);
            System.out.println("===============\n" + resp + "\n=============");
            // the body is truncated
            // we actually do not need that
            // successfully parsing the status and headers means that
            // this lib is working properly
            try {
                selectorEventLoop.close();
            } catch (IOException e) {
                fail(e.getMessage());
            }
        }

        @Override
        public void writable(ConnectionHandlerContext ctx) {
            ctx.connection.getOutBuffer().storeBytesFrom(chnl);
        }

        @Override
        public void exception(ConnectionHandlerContext ctx, IOException err) {
            fail();
        }

        @Override
        public void closed(ConnectionHandlerContext ctx) {
            // ignore
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            // ignore
        }
    }

    private static final String keyStoreFile = "testkeys";
    private static final String trustStoreFile = "testkeys";

    ConcurrentLinkedQueue<Runnable> q = new ConcurrentLinkedQueue<>();

    SimpleRingBuffer serverOutputData = RingBuffer.allocate(16384);
    SimpleRingBuffer serverInputData = RingBuffer.allocate(16384);
    SSLWrapRingBuffer serverWrap;
    SSLUnwrapRingBuffer serverUnwrap;

    SimpleRingBuffer clientOutputData = RingBuffer.allocate(16384);
    SimpleRingBuffer clientInputData = RingBuffer.allocate(16384);
    SSLWrapRingBuffer clientWrap;
    SSLUnwrapRingBuffer clientUnwrap;

    byte[] tmp = new byte[16384];
    ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(tmp);

    int serverTotalData;
    int clientTotalData;

    @Test
    public void wrapThenUnwrap() throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        KeyStore ts = KeyStore.getInstance("JKS");

        char[] passphrase = "passphrase".toCharArray();

        ks.load(TestSSLRingBuffers.class.getResourceAsStream("/" + keyStoreFile), passphrase);
        ts.load(TestSSLRingBuffers.class.getResourceAsStream("/" + trustStoreFile), passphrase);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);

        SSLContext context = SSLContext.getInstance("TLSv1.2");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        SSLEngine serverEngine = context.createSSLEngine();
        serverEngine.setUseClientMode(false);
        serverEngine.setNeedClientAuth(true);

        SSLEngine clientEngine = context.createSSLEngine("cassite.net", 443);
        clientEngine.setUseClientMode(true);

        // init buffers

        SSLUtils.SSLBufferPair tuple = SSLUtils.genbuf(serverEngine, serverInputData, serverOutputData, q::add);
        serverWrap = tuple.right;
        serverUnwrap = tuple.left;

        tuple = SSLUtils.genbuf(clientEngine, clientInputData, clientOutputData, q::add);
        clientWrap = tuple.right;
        clientUnwrap = tuple.left;

        // set callback
        serverWrap.addHandler(new RingBufferETHandler() {
            @Override
            public void readableET() {
                serverSendMessage();
            }

            @Override
            public void writableET() {
                // will not fire
            }
        });
        clientWrap.addHandler(new RingBufferETHandler() {
            @Override
            public void readableET() {
                clientSendMessage();
            }

            @Override
            public void writableET() {
                // will not fire
            }
        });

        // set data

        String serverMsg = "Hello Client, I'm Server";
        String clientMsg = "Hi Server, I'm Client";
        serverOutputData.storeBytesFrom(ByteArrayChannel.fromFull(serverMsg.getBytes()));
        clientOutputData.storeBytesFrom(ByteArrayChannel.fromFull(clientMsg.getBytes()));
        serverTotalData = serverOutputData.used();
        clientTotalData = clientOutputData.used();

        // initial push
        clientSendMessage();

        do {
            Thread.sleep(1);
            runQ();
        } while (serverInputData.used() != clientTotalData || clientInputData.used() != serverTotalData);

        assertEquals(clientMsg, serverInputData.toString());
        assertEquals(serverMsg, clientInputData.toString());
    }

    void runQ() {
        Runnable r;
        while ((r = q.poll()) != null) {
            r.run();
        }
    }

    void serverSendMessage() {
        chnl.reset();
        int writeBytes = serverWrap.writeTo(chnl);
        System.out.println("================");
        System.out.println("server sends: " + writeBytes);
        System.out.println("================");

        // write to client
        int storeBytes = clientUnwrap.storeBytesFrom(chnl);
        System.out.println("================");
        System.out.println("client reads: " + storeBytes);
        System.out.println("================");
    }

    private void clientSendMessage() {
        chnl.reset();
        int writeBytes = clientWrap.writeTo(chnl);
        System.out.println("================");
        System.out.println("client sends: " + writeBytes);
        System.out.println("================");

        // write to server
        int storeBytes = serverUnwrap.storeBytesFrom(chnl);
        System.out.println("================");
        System.out.println("server reads: " + storeBytes);
        System.out.println("================");
    }
}
