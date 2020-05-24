package vproxy.test.cases;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import vfd.IP;
import vfd.IPPort;
import vproxy.component.app.TcpLB;
import vproxybase.component.check.CheckProtocol;
import vproxybase.component.check.HealthCheckConfig;
import vproxybase.component.elgroup.EventLoopGroup;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.ssl.CertKey;
import vproxybase.component.svrgroup.Method;
import vproxybase.component.svrgroup.ServerGroup;
import vproxy.component.svrgroup.Upstream;
import vproxybase.connection.*;
import vproxybase.dns.Resolver;
import vproxybase.http.HttpRespParser;
import vproxybase.processor.http1.entity.Response;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.util.BlockCallback;
import vproxybase.util.RingBuffer;
import vproxybase.util.RingBufferETHandler;
import vproxybase.util.nio.ByteArrayChannel;
import vproxybase.util.ringbuffer.SSLUnwrapRingBuffer;
import vproxybase.util.ringbuffer.SSLUtils;
import vproxybase.util.ringbuffer.SSLWrapRingBuffer;
import vproxybase.util.ringbuffer.SimpleRingBuffer;
import vproxybase.util.ringbuffer.ssl.VSSLContext;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.Assert.*;

public class TestSSL {
    public static final String TEST_CERT = "-----BEGIN CERTIFICATE-----\n" +
        "MIIDrDCCApQCCQDO2qFtjzwFWzANBgkqhkiG9w0BAQsFADCBljELMAkGA1UEBhMC\n" +
        "Q04xETAPBgNVBAgMCFpoZWppYW5nMREwDwYDVQQHDAhIYW5nemhvdTEPMA0GA1UE\n" +
        "CgwGdnByb3h5MQ8wDQYDVQQLDAZ2cHJveHkxGzAZBgNVBAMMEnZwcm94eS5jYXNz\n" +
        "aXRlLm5ldDEiMCAGCSqGSIb3DQEJARYTd2tnY2Fzc0Bob3RtYWlsLmNvbTAgFw0x\n" +
        "OTA3MTYwODAxNDNaGA8yMTE5MDYyMjA4MDE0M1owgZYxCzAJBgNVBAYTAkNOMREw\n" +
        "DwYDVQQIDAhaaGVqaWFuZzERMA8GA1UEBwwISGFuZ3pob3UxDzANBgNVBAoMBnZw\n" +
        "cm94eTEPMA0GA1UECwwGdnByb3h5MRswGQYDVQQDDBJ2cHJveHkuY2Fzc2l0ZS5u\n" +
        "ZXQxIjAgBgkqhkiG9w0BCQEWE3drZ2Nhc3NAaG90bWFpbC5jb20wggEiMA0GCSqG\n" +
        "SIb3DQEBAQUAA4IBDwAwggEKAoIBAQCgTHZBQNzCeuTcN4s5Cc7uKg/iLWwobByG\n" +
        "rTTVHAYSpUe0ygaHCAWV4nblf0pSW5uPhhxTGEZFJomjt2EKFkSYEJXpT2C3abQw\n" +
        "Jw8lZM8gfeqeC/9Xng8c2nffcu8Cy0PcNq1O6B9vXiKQ6JtRHnQeGUIGWWW8cMUT\n" +
        "H6FSyk4C/nB64F+bjYeG8bJBNeziUFVBZSeOhE7Pjf42HkotqIpuMzBEhnNWlpY3\n" +
        "pkgbCZaMNkaeCAW63XveDxj2YaFByLAAoAhtLO9mqcX44e7HILg1POL8rIwNy/l8\n" +
        "kkoPu1UHyzOS/f6WaddBjZqtjjls4Ph8xD0ZBwfd27TywGZCOaz/AgMBAAEwDQYJ\n" +
        "KoZIhvcNAQELBQADggEBAEC+cvEiSrnZQZRQG+vS4VGnpnerllxfUQxn+JU+B529\n" +
        "fJWlacY1TlVxkrAN/33m0xoK5KhyN0ML/OPGcCGQbh36QjZGnFREsDn+xMvs8Kfh\n" +
        "ufW67kDNh0GTJWHseAI/MXzwVUrfOrEHGEhYat4QjVNtrqQVtsR18f+z+k3pfTED\n" +
        "e1C8zyKbbjeCNybOuuGOxc2HHuBFZveDpB3sCyUIW2iS1tCBXvI9u2cLo/QsjsAz\n" +
        "kkv6/Fh8BOQT3IMHTh31tfdDJuA0lCs9o9Kc66AaZxTYm8SyNh5L1doYHXoptphI\n" +
        "gAAa3BEO21XanlNRU1927oxt6mwNp+WeU1xvyoxCWeE=\n" +
        "-----END CERTIFICATE-----\n";
    public static final String TEST_KEY = "-----BEGIN PRIVATE KEY-----\n" +
        "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQCgTHZBQNzCeuTc\n" +
        "N4s5Cc7uKg/iLWwobByGrTTVHAYSpUe0ygaHCAWV4nblf0pSW5uPhhxTGEZFJomj\n" +
        "t2EKFkSYEJXpT2C3abQwJw8lZM8gfeqeC/9Xng8c2nffcu8Cy0PcNq1O6B9vXiKQ\n" +
        "6JtRHnQeGUIGWWW8cMUTH6FSyk4C/nB64F+bjYeG8bJBNeziUFVBZSeOhE7Pjf42\n" +
        "HkotqIpuMzBEhnNWlpY3pkgbCZaMNkaeCAW63XveDxj2YaFByLAAoAhtLO9mqcX4\n" +
        "4e7HILg1POL8rIwNy/l8kkoPu1UHyzOS/f6WaddBjZqtjjls4Ph8xD0ZBwfd27Ty\n" +
        "wGZCOaz/AgMBAAECggEBAJiyXY+pVuHXqXzxWCj8Y+dRoBHHTRlwavgLtKEw8cP/\n" +
        "N8BLFk644IE32iShzc2IQDZG/WJWZFHo9QJEZCb0sWDdo8A9AheVlLSt8GqhjgEY\n" +
        "kU7+hL0U7raAkeIEHRPfRwRV/V+GFLPEy06YlaN/TAOD8fYUYKpSDhk6bzVrS0bj\n" +
        "UYi0MdQqV89Y4JOMf9fSiJR3kf4H1zW6FViOMhbZegzLKfJcpDDZRQuf/cRm2+z3\n" +
        "OJX+XoJsHdGmY42sTHGidDLFjRxVjMwrUtL6lFt/lREFsScvKB2OTVWw7nHZ4MVs\n" +
        "d8e/kAsSQ3PaEWn7CQmPG1pcTV0u6vDPC+g2acO25tECgYEAzhnBDYi5FFkgdfuu\n" +
        "ejWouJmNL2XLtnDEi68I2zS1P/TuUMhAZ3DHk4pGWTGLCKXtb1hQfMat85hfDQF6\n" +
        "TAXZ9b8Dm1atEKd8TGuIU/Gzo9eglApw69SO99LFcr+L6qnUuClKEJTAzYi2xz7N\n" +
        "klhxlhim5lZd/Tp/QpA4KeH54FcCgYEAxxvgKgd2T6slo7zXbwxSUQw18kWsIuvF\n" +
        "O0oH6TV0CA/712FhDAsV+QhuAU5X53zC/j+KoXXSQfvZsj4AqYgYL5dzfmv9zQAS\n" +
        "/A3qkvKjsaPMZnKWGw1BzPUh3AWMPVL9DO2P7UHv1nnCvWMlVgci1WeIKHhdChkU\n" +
        "KmnFynm+j5kCgYEAvBmLNTP0XtrdInD6k7UHcLtLvNd2LeMLrsSoG5AmX+HF41pw\n" +
        "VTf8He7UN7FcyB7P7ZA3nTmjJzCIh5EysdGhVITp4MshlpKVghWeTabJoh45AwPo\n" +
        "fYP4m7v00r55D0nCx/V/EFUDBlLhJkVuT0ODH08OfCiVDXlnDjQb3jXM3W8CgYA8\n" +
        "8QhEdPI+YjjsC9G4mIHdcqpUVATiz10Xz4nqVEUGbrX7bz+/6ui3x1+8IJmBLcuU\n" +
        "/CfXUXOgZJB2IModGZ2le2qLKEyPYVVuNmg0v/VgWq0mMi5Fa2JXdDP/3ubUokD3\n" +
        "owKpcMQS1kPHqb/0u8xqmvyuvmBjxddJQASc+3RbCQKBgQC3MC+MpFimu6Ig3sLN\n" +
        "W9y83ww4KHyjsNzNJSFMOUn1zLLoVeQ2VpfRCxqxUfU0gfeJmn8It7WtkG87bmVW\n" +
        "jsLm2X0GsXQsLqLY9iKXUhJHslad+7xS91Mc81K9YJhRpHpl5UFd/hGhoJdN5mpF\n" +
        "5wonEzCuFqzz1ASiYQWtbeWSBA==\n" +
        "-----END PRIVATE KEY-----\n";

    private static String javaxnetdebug;

    @BeforeClass
    public static void setUpClass() {
        javaxnetdebug = System.getProperty("javax.net.debug");
        // System.setProperty("javax.net.debug", "all");
        if (javaxnetdebug == null) {
            javaxnetdebug = "";
        }
    }

    @AfterClass
    public static void tearDownClass() {
        System.setProperty("javax.net.debug", javaxnetdebug);
    }

    SelectorEventLoop selectorEventLoop;

    @Test
    public void requestSite() throws Exception {
        String url = "https://myip.ipip.net";
        String host = url.substring("https://".length());
        int port = 443;
        BlockCallback<IP, UnknownHostException> cb = new BlockCallback<>();
        Resolver.getDefault().resolveV4(host, cb);
        IP inet;
        try {
            inet = cb.block();
        } catch (UnknownHostException e) {
            System.out.println("we are not able to resolve the address, " +
                "may be network is wrong, we ignore this case");
            return;
        }
        var remote = new IPPort(inet, port);
        // make a socket to test whether it's accessible
        {
            try (Socket sock = new Socket()) {
                sock.connect(remote.toInetSocketAddress());
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
            selectorEventLoop,
            remote
        );
        ConnectableConnection conn = ConnectableConnection.create(
            remote,
            ConnectionOpts.getDefault(),
            pair.left, pair.right
        );
        loop.addConnectableConnection(conn, null, new MySSLConnectableConnectionHandler());

        // start
        selectorEventLoop.loop();
    }

    class MySSLConnectableConnectionHandler implements ConnectableConnectionHandler {
        private final ByteArrayChannel chnl;
        private final HttpRespParser parser;

        MySSLConnectableConnectionHandler() {
            chnl = ByteArrayChannel.fromFull(("" +
                "GET / HTTP/1.1\r\n" +
                "Host: myip.ipip.net\r\n" +
                "User-Agent: curl/vproxy\r\n" + // add curl agent to get json response
                "\r\n").getBytes());
            parser = new HttpRespParser(true);
        }

        @Override
        public void connected(ConnectableConnectionHandlerContext ctx) {
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
        public void remoteClosed(ConnectionHandlerContext ctx) {
            ctx.connection.close();
            closed(ctx);
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

        ks.load(TestSSL.class.getResourceAsStream("/" + keyStoreFile), passphrase);
        ts.load(TestSSL.class.getResourceAsStream("/" + trustStoreFile), passphrase);

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

    @Test
    public void certKey() throws Exception {
        CertKey key = new CertKey("test", new String[]{TEST_CERT}, TEST_KEY);
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null);
        key.setInto(keyStore);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keyStore, "changeit".toCharArray());

        KeyManager[] km = kmf.getKeyManagers();

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(km, null, null);
        ServerSocket socket = context.getServerSocketFactory().createServerSocket(54321);
        socket.close();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void sslProxy() throws Exception {
        Vertx vertx = Vertx.vertx();
        EventLoopGroup elg = new EventLoopGroup("elg0");

        try {
            // start backend
            vertx.createHttpServer().requestHandler(req -> req.response().end("hello")).listen(39999);

            // build ssl context if needed
            // create ctx
            VSSLContext sslContext = new VSSLContext();
            CertKey ck = new CertKey("ck", new String[]{TEST_CERT}, TEST_KEY);
            ck.setInto(sslContext);

            elg.add("el");
            Upstream ups = new Upstream("ups");
            ServerGroup sg = new ServerGroup("sg", elg, new HealthCheckConfig(400, 2000, 1, 2, CheckProtocol.tcpDelay), Method.wrr);
            ups.add(sg, 10);

            Thread.sleep(1000);

            sg.add("svr", new IPPort(IP.from(new byte[]{127, 0, 0, 1}), 39999), 10);

            TcpLB tl = new TcpLB(
                "testSslProxy",
                elg,
                elg,
                new IPPort(IP.from(new byte[]{127, 0, 0, 1}), 19999),
                ups,
                1000,
                4096,
                4096,
                "http",
                sslContext,
                null,
                SecurityGroup.allowAll()
            );
            tl.start();

            String[] body = {null};

            HttpClient cli = vertx.createHttpClient(new HttpClientOptions()
                .setSsl(true)
                .setTrustAll(true)
                .setVerifyHost(false));
            cli.get(19999, "127.0.0.1", "/").handler(resp ->
                resp.bodyHandler(_body -> {
                    body[0] = _body.toString().trim();
                })
            ).end();

            while (body[0] == null) {
                Thread.sleep(1);
            }

            assertEquals("hello", body[0]);
        } finally {
            vertx.close();
            elg.close();
        }
    }
}
