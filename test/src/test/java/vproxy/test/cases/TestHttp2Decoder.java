package vproxy.test.cases;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import org.junit.*;
import vproxy.base.http.Http2Decoder;
import vproxy.base.processor.httpbin.BinaryHttpSubContext;
import vproxy.base.processor.httpbin.HttpFrame;
import vproxy.base.processor.httpbin.entity.Header;
import vproxy.base.processor.httpbin.frame.*;
import vproxy.base.util.BlockCallback;
import vproxy.base.util.ByteArray;
import vproxy.base.util.Callback;
import vproxy.base.util.RingBuffer;
import vproxy.base.util.exception.NoException;
import vproxy.base.util.nio.ByteArrayChannel;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

public class TestHttp2Decoder {
    private final Http2Decoder decoder = new Http2Decoder(true);
    private final BinaryHttpSubContext ctx = decoder.getCtx();

    private static final List<BinCheckCase> binCheckCases;

    static {
        binCheckCases = new LinkedList<>();
        {
            binCheckCases.add(new BinCheckCase(
                "preface",
                new Preface(),
                "505249202a20485454502f322e300d0a0d0a534d0d0a0d0a"
            ));
        }
        {
            SettingsFrame settings = new SettingsFrame();
            settings.maxConcurrentStreams = 100;
            settings.maxConcurrentStreamsSet = true;
            settings.maxHeaderListSize = 8192;
            settings.maxHeaderListSizeSet = true;
            binCheckCases.add(new BinCheckCase(
                "settings-1",
                settings,
                "00000c040000000000000300000064000600002000"
            ));
        }
        {
            SettingsFrame settings = new SettingsFrame();
            settings.headerTableSize = 4096;
            settings.headerTableSizeSet = true;
            settings.enablePush = 1;
            settings.enablePushSet = true;
            settings.maxConcurrentStreams = 128;
            settings.maxConcurrentStreamsSet = true;
            settings.initialWindowSize = 65535;
            settings.initialWindowSizeSet = true;
            settings.maxFrameSize = 16384;
            settings.maxFrameSizeSet = true;
            settings.maxHeaderListSize = 2048;
            settings.maxHeaderListSizeSet = true;
            binCheckCases.add(new BinCheckCase(
                "settings-2",
                settings,
                "00002404000000000000010000100000020000000100030000008000040000ffff000500004000000600000800"
            ));
        }
        {
            SettingsFrame settings = new SettingsFrame();
            settings.ack = true;
            var aCase = new BinCheckCase(
                "settings-ack",
                settings,
                "000000040100000000"
            );
            aCase.noPayload = true;
            binCheckCases.add(aCase);
        }
        {
            SettingsFrame settings = new SettingsFrame();
            settings.headerTableSize = 4096;
            settings.headerTableSizeSet = true;
            settings.maxConcurrentStreams = 128;
            settings.maxConcurrentStreamsSet = true;
            settings.initialWindowSize = 65535;
            settings.initialWindowSizeSet = true;
            settings.maxFrameSize = 16384;
            settings.maxFrameSizeSet = true;
            settings.enableConnectProtocol = 1;
            settings.enableConnectProtocolSet = true;
            binCheckCases.add(new BinCheckCase(
                "settings-3",
                settings,
                "00001e04000000000000010000100000030000008000040000ffff000500004000000800000001"
            ));
        }
        {
            HeadersFrame headers = new HeadersFrame();
            headers.endHeaders = true;
            headers.streamId = 1;
            headers.headers = Arrays.asList(
                new Header(":scheme", "http"),
                new Header(":method", "POST"),
                new Header(":path", "/echo-and-push-promise"),
                new Header("content-length", "11")
            );
            binCheckCases.add(new BinCheckCase(
                "headers-1",
                headers,
                "0000180104000000018683449060a49ceb0ea916aed44eb5761e9320bf5c023131"
            ));
        }
        {
            DataFrame data = new DataFrame();
            data.endStream = true;
            data.streamId = 1;
            data.data = ByteArray.from("hello world");
            binCheckCases.add(new BinCheckCase(
                "data-1",
                data,
                "00000b00010000000168656c6c6f20776f726c64"
            ));
        }
        {
            PushPromiseFrame pushPromise = new PushPromiseFrame();
            pushPromise.endHeaders = true;
            pushPromise.streamId = 1;
            pushPromise.promisedStreamId = 2;
            pushPromise.headers = Arrays.asList(
                new Header(":method", "GET"),
                new Header(":path", "/push-promise"),
                new Header(":scheme", "http"),
                new Header(":authority", "0.0.0.0:38080")
            );
            binCheckCases.add(new BinCheckCase(
                "push-promise",
                pushPromise,
                "00001e0504000000010000000282448a62bb513ad5d87a4c82ff86418a02e05c0b82e32f01e07f"
            ));
        }
        {
            HeadersFrame headers = new HeadersFrame();
            headers.priority = true;
            headers.endHeaders = true;
            headers.streamId = 1;
            headers.streamDependency = 0;
            headers.weight = 15;
            headers.headers = Arrays.asList(
                new Header(":status", "200"),
                new Header("content-length", "40")
            );
            binCheckCases.add(new BinCheckCase(
                "headers-2",
                headers,
                "00000a012400000001000000000f885c023430"
            ));
        }
        {
            DataFrame data = new DataFrame();
            data.endStream = true;
            data.streamId = 1;
            data.data = ByteArray.from("POST /echo-and-push-promise: hello world");
            binCheckCases.add(new BinCheckCase(
                "data-2",
                data,
                "000028000100000001504f5354202f6563686f2d616e642d707573682d70726f6d6973653a2068656c6c6f20776f726c64"
            ));
        }
        {
            HeadersFrame headers = new HeadersFrame();
            headers.priority = true;
            headers.padded = true;
            headers.streamId = 3;
            headers.streamDependency = 0;
            headers.weight = 10;
            headers.headers = Arrays.asList(
                new Header(":scheme", "http"),
                new Header(":method", "PUT"),
                new Header(":path", "/echo")
            );
            headers.padding = ByteArray.from("headers-padding");
            binCheckCases.add(new BinCheckCase(
                "headers-3",
                headers,
                "0000210128000000030f000000000a864203505554448460a49cff686561646572732d70616464696e67"
            ));
        }
        {
            ContinuationFrame cont = new ContinuationFrame();
            cont.endHeaders = true;
            cont.streamId = 3;
            cont.headers = Collections.singletonList(new Header("content-length", "7"));
            binCheckCases.add(new BinCheckCase(
                "continuation",
                cont,
                "0000030904000000035c0137"
            ));
        }
        {
            DataFrame data = new DataFrame();
            data.padded = true;
            data.streamId = 3;
            data.data = ByteArray.from("foo");
            data.padding = ByteArray.from("data-padding");
            binCheckCases.add(new BinCheckCase(
                "data-3",
                data,
                "0000100008000000030c666f6f646174612d70616464696e67"
            ));
        }
        {
            GoAwayFrame goaway = new GoAwayFrame();
            goaway.errorCode = 248;
            goaway.lastStreamId = 3;
            goaway.additionalDebugData = ByteArray.from("simple test case");
            binCheckCases.add(new BinCheckCase(
                "goaway",
                goaway,
                "00001807000000000000000003000000f873696d706c6520746573742063617365"
            ));
        }
        {
            RstStreamFrame rst = new RstStreamFrame();
            rst.streamId = 3;
            rst.errorCode = 123;
            binCheckCases.add(new BinCheckCase(
                "rst",
                rst,
                "0000040300000000030000007b"
            ));
        }
        {
            PushPromiseFrame pushPromise = new PushPromiseFrame();
            pushPromise.streamId = 1;
            pushPromise.padded = true;
            pushPromise.promisedStreamId = 2;
            pushPromise.headers = Arrays.asList(
                new Header(":scheme", "http"),
                new Header(":method", "GET"),
                new Header(":path", "/abc")
            );
            pushPromise.padding = ByteArray.from("push-promise-padding");
            binCheckCases.add(new BinCheckCase(
                "push-promise-2",
                pushPromise,
                "000020050800000001140000000286824483607193707573682d70726f6d6973652d70616464696e67"
            ));
        }
        {
            PingFrame ping = new PingFrame();
            ping.data = ByteArray.from("pingdata").int64(0);
            binCheckCases.add(new BinCheckCase(
                "ping-1",
                ping,
                "00000806000000000070696e6764617461"
            ));
        }
        {
            PingFrame ping = new PingFrame();
            ping.data = ByteArray.from("pingdata").int64(0);
            ping.ack = true;
            binCheckCases.add(new BinCheckCase(
                "ping-2",
                ping,
                "00000806010000000070696e6764617461"
            ));
        }
        {
            WindowUpdateFrame w = new WindowUpdateFrame();
            w.windowSizeIncrement = 19;
            binCheckCases.add(new BinCheckCase(
                "window-update",
                w,
                "00000408000000000000000013"
            ));
        }
        {
            PriorityFrame priority = new PriorityFrame();
            priority.streamId = 13;
            priority.streamDependency = 11;
            priority.weight = 10;
            binCheckCases.add(new BinCheckCase(
                "priorty",
                priority,
                "00000502000000000d0000000b0a"
            ));
        }
    }

    private static class BinCheckCase {
        final String desc;
        final HttpFrame frame;
        final String hex;
        boolean noPayload;

        private BinCheckCase(String desc, HttpFrame frame, String hex) {
            this.desc = desc;
            this.frame = frame;
            this.hex = hex;
        }
    }

    @Test
    public void binCheck() throws Exception {
        Field stateField = BinaryHttpSubContext.class.getDeclaredField("state");
        stateField.setAccessible(true);
        Field chnlField = BinaryHttpSubContext.class.getDeclaredField("chnl");
        chnlField.setAccessible(true);

        Http2Decoder decoder2 = new Http2Decoder(true);
        BinaryHttpSubContext ctx2 = decoder2.getCtx();

        Http2Decoder decoder3 = new Http2Decoder(true);
        BinaryHttpSubContext ctx3 = decoder3.getCtx();

        RingBuffer rb = RingBuffer.allocate(8192);
        for (var aCase : binCheckCases) {
            System.out.println("running bin check: " + aCase.desc);
            ByteArray expected = ByteArray.fromHexString(aCase.hex);
            ByteArray actual = aCase.frame.serializeH2(ctx);
            assertEquals("\n" +
                    expected.toHexString() + "\n" +
                    actual.toHexString(),
                expected,
                actual
            );

            if (aCase.frame instanceof Preface) {
                stateField.set(ctx2, BinaryHttpSubContext.STATE_PREFACE);
                chnlField.set(ctx2, ByteArrayChannel.fromEmpty(24));
            } else if (aCase.frame instanceof ContinuationFrame) {
                stateField.set(ctx2, BinaryHttpSubContext.STATE_CONTINUATION_FRAME_HEADER);
                chnlField.set(ctx2, ByteArrayChannel.fromEmpty(9));
            } else {
                stateField.set(ctx2, BinaryHttpSubContext.STATE_FRAME_HEADER);
                chnlField.set(ctx2, ByteArrayChannel.fromEmpty(9));
            }

            rb.storeBytesFrom(ByteArrayChannel.fromFull(expected));
            ctx2.feed(rb);
            if (!aCase.noPayload) {
                ctx2.feed(rb);
            }
            assertEquals(0, rb.used());
            HttpFrame frame = ctx2.getFrame();
            ByteArray serialized = frame.serializeH2(ctx3);
            assertEquals("\n" +
                    "expected: " + expected.toHexString() + "\n" +
                    "actual  : " + serialized.toHexString() + "\n" +
                    "expected: " + aCase.frame + "\n" +
                    "actual  : " + frame,
                expected, serialized);
        }
    }

    private static final int myListenPort = 28080;
    private static final int vertxListenPort = 38080;
    private static Vertx vertx;
    private static HttpServer vertxHttpServer;

    @BeforeClass
    public static void beforeClass() throws Throwable {
        vertx = Vertx.vertx();
        vertxHttpServer = vertx.createHttpServer();
        vertxHttpServer.requestHandler(req -> {
            if (req.path().equals("/echo-and-push-promise")) {
                req.bodyHandler(buf -> {
                    req.response().push(HttpMethod.GET, "/push-promise", r -> {
                        if (r.failed()) {
                            r.cause().printStackTrace();
                            throw new Error(r.cause());
                        }
                        vertx.setTimer(200, l -> r.result().end("push promise body"));
                    });
                    req.response().end(req.method() + " " + req.uri() + ": " + buf.toString());
                });
            } else if (req.path().equals("/echo")) {
                req.bodyHandler(buf -> req.response().end(req.method() + " " + req.uri() + ": " + buf.toString()));
            }
        });
        BlockCallback<Void, Throwable> listenCB = new BlockCallback<>();
        vertxHttpServer.listen(vertxListenPort, r -> {
            if (r.succeeded()) {
                listenCB.succeeded();
            } else {
                listenCB.failed(r.cause());
            }
        });
        listenCB.block();
    }

    @AfterClass
    public static void afterClass() {
        BlockCallback<Void, NoException> cb = new BlockCallback<>();
        vertxHttpServer.close(r -> cb.succeeded());
        cb.block();
        vertx.close();
    }

    private NetClient netClient;
    private NetServer netServer;
    private HttpClient httpClient;

    @Before
    public void setUp() {
        netClient = vertx.createNetClient();
        netServer = vertx.createNetServer();
        httpClient = vertx.createHttpClient(new HttpClientOptions()
            .setHttp2ClearTextUpgrade(false)
            .setProtocolVersion(HttpVersion.HTTP_2));
    }

    @After
    public void tearDown() {
        netClient.close();
        netServer.close();
        httpClient.close();
    }

    @Test
    public void client() throws Throwable {
        BlockCallback<String, Throwable> cb1 = new BlockCallback<>();
        BlockCallback<String, Throwable> cb2 = new BlockCallback<>();
        BlockCallback<String, Throwable> cb3 = new BlockCallback<>();
        BlockCallback<Void, Throwable> cb4 = new BlockCallback<>();

        Callback[] cbs = new Callback[]{cb1, cb2, cb3, cb4};

        netClient.connect(vertxListenPort, "127.0.0.1", r -> {
            if (r.failed()) {
                cb1.failed(r.cause());
                return;
            }
            NetSocket sock = r.result();

            RingBuffer inBuffer = RingBuffer.allocate(8192);
            sock.handler(new Handler<>() {
                int state = 0;

                @Override
                public void handle(Buffer buf) {
                    inBuffer.storeBytesFrom(ByteArrayChannel.fromFull(buf.getBytes()));
                    while (true) {
                        int res = decoder.feed(inBuffer);
                        if (res == 0) {
                            HttpFrame frame = decoder.getResult();
                            try {
                                handleFrame(frame);
                            } catch (Throwable e) {
                                for (var cb : cbs) {
                                    //noinspection unchecked
                                    cb.failed(e);
                                }
                                return;
                            }
                        } else {
                            String errMsg = decoder.getErrorMessage();
                            if (errMsg == null) {
                                // want more data
                                break;
                            }
                            for (var cb : cbs) {
                                //noinspection unchecked
                                cb.failed(new Exception(errMsg));
                            }
                            return;
                        }
                        if (inBuffer.used() == 0) {
                            break;
                        }
                    }
                }

                private void handleFrame(HttpFrame frame) throws Throwable {
                    if (state == 0) {
                        assertTrue("expecting settings frame, but got " + frame, frame instanceof SettingsFrame);

                        // send first settings frame
                        SettingsFrame settings = SettingsFrame.newClientSettings();
                        settings.maxHeaderListSize = 2048;
                        settings.maxHeaderListSizeSet = true;
                        sock.write(Buffer.buffer(settings.serializeH2(ctx).toJavaArray()));
                        // send ack of the peer settings frame
                        settings = SettingsFrame.newAck();
                        sock.write(Buffer.buffer(settings.serializeH2(ctx).toJavaArray()));

                        state = 1;
                    } else if (state == 1) {
                        assertTrue("expecting settings frame (ack), but got " + frame, frame instanceof SettingsFrame);
                        assertTrue("settings.ack should be set", ((SettingsFrame) frame).ack);

                        // send simple request
                        HeadersFrame headers = HeadersFrame.newRequest("http", "POST", "/echo-and-push-promise",
                            new Header("Content-Length", "" + ("hello world".length())));
                        headers.streamId = 1;
                        sock.write(Buffer.buffer(headers.serializeH2(ctx).toJavaArray()));
                        DataFrame data = new DataFrame();
                        data.data = ByteArray.from("hello world");
                        data.endStream = true;
                        data.streamId = 1;
                        sock.write(Buffer.buffer(data.serializeH2(ctx).toJavaArray()));

                        state = 2;
                    } else if (state == 2) {
                        assertTrue("expecting push-promise frame, but got " + frame, frame instanceof PushPromiseFrame);
                        assertEquals(1, frame.streamId);

                        assertArrayEquals("/push-promise".getBytes(), ((PushPromiseFrame) frame).get(":path"));
                        assertEquals(2, ((PushPromiseFrame) frame).promisedStreamId);

                        state = 3;
                    } else if (state == 3) {
                        assertTrue("expecting headers frame, but got " + frame, frame instanceof HeadersFrame);
                        assertEquals(1, frame.streamId);

                        var headers = ((HeadersFrame) frame).toMap();
                        assertTrue(headers.containsKey(":status"));
                        assertEquals("200", headers.get(":status"));

                        state = 4;
                    } else if (state == 4) {
                        assertTrue("expecting data frame, but got " + frame, frame instanceof DataFrame);
                        assertEquals(1, frame.streamId);
                        assertTrue(((DataFrame) frame).endStream);
                        byte[] data = ((DataFrame) frame).data.toJavaArray();
                        cb1.succeeded(new String(data));
                        state = 5;
                    } else if (state == 5) {
                        assertTrue("expecting headers frame, but got " + frame, frame instanceof HeadersFrame);
                        assertEquals(2, frame.streamId);

                        var headers = ((HeadersFrame) frame).toMap();
                        assertTrue(headers.containsKey(":status"));
                        assertEquals("200", headers.get(":status"));

                        state = 6;
                    } else if (state == 6) {
                        assertTrue("expecting data frame, but got " + frame, frame instanceof DataFrame);
                        assertEquals(2, frame.streamId);
                        byte[] dataBytes = ((DataFrame) frame).data.toJavaArray();
                        cb2.succeeded(new String(dataBytes));

                        // send another request
                        HeadersFrame headers = HeadersFrame.newRequest("http", "PUT", "/echo");
                        headers.endHeaders = false; // test continuation
                        headers.padded = true; // test padding
                        headers.priority = true; // test dependency
                        headers.streamId = 3;
                        headers.padding = ByteArray.from("headers-padding");
                        headers.streamDependency = 0;
                        headers.weight = 10;
                        sock.write(Buffer.buffer(headers.serializeH2(ctx).toJavaArray()));
                        // send continuation
                        ContinuationFrame cont = new ContinuationFrame();
                        cont.endHeaders = true;
                        cont.streamId = 3;
                        cont.headers = Collections.singletonList(new Header("Content-Length", "" + ("foo bar".length())));
                        sock.write(Buffer.buffer(cont.serializeH2(ctx).toJavaArray()));
                        // send data 1
                        DataFrame data = new DataFrame();
                        data.data = ByteArray.from("foo");
                        data.endStream = false;
                        data.padded = true;
                        data.streamId = 3;
                        data.padding = ByteArray.from("data-padding");
                        sock.write(Buffer.buffer(data.serializeH2(ctx).toJavaArray()));
                        // send data 2
                        DataFrame data2 = new DataFrame();
                        data2.data = ByteArray.from(" bar");
                        data2.endStream = true;
                        data2.streamId = 3;
                        sock.write(Buffer.buffer(data2.serializeH2(ctx).toJavaArray()));

                        state = 7;
                    } else if (state == 7) {
                        assertTrue("expecting headers frame, but got " + frame, frame instanceof HeadersFrame);
                        assertEquals(3, frame.streamId);

                        var headers = ((HeadersFrame) frame).toMap();
                        assertTrue(headers.containsKey(":status"));
                        assertEquals("200", headers.get(":status"));

                        state = 8;
                    } else if (state == 8) {
                        assertTrue("expecting data frame, but got " + frame, frame instanceof DataFrame);
                        assertEquals(3, frame.streamId);

                        assertTrue(((DataFrame) frame).endStream);
                        byte[] data = ((DataFrame) frame).data.toJavaArray();
                        cb3.succeeded(new String(data));

                        // send goaway
                        GoAwayFrame goaway = new GoAwayFrame();
                        goaway.lastStreamId = 3;
                        goaway.errorCode = 248;
                        goaway.additionalDebugData = ByteArray.from("simple test case");
                        goaway.streamId = 0;
                        sock.write(Buffer.buffer(goaway.serializeH2(ctx).toJavaArray()));

                        // send rst
                        RstStreamFrame rst = new RstStreamFrame();
                        rst.streamId = 3;
                        rst.errorCode = 123;
                        sock.write(Buffer.buffer(rst.serializeH2(ctx).toJavaArray()));

                        state = 9;

                        cb4.succeeded();
                        sock.end();
                    } else {
                        throw new Exception("unexpected frame " + frame);
                    }
                }
            });
            sock.closeHandler(v -> {
                for (var cb : cbs) {
                    if (!cb.isCalled()) {
                        //noinspection unchecked
                        cb.failed(new Exception("closed unexpectedly"));
                    }
                }
            });

            Preface preface = new Preface();
            try {
                sock.write(Buffer.buffer(preface.serializeH2(ctx).toJavaArray()));
            } catch (Exception e) {
                for (var cb : cbs) {
                    //noinspection unchecked
                    cb.failed(e);
                }
            }
        });
        String res = cb1.block();
        assertEquals("POST /echo-and-push-promise: hello world", res);
        res = cb2.block();
        assertEquals("push promise body", res);
        res = cb3.block();
        assertEquals("PUT /echo: foo bar", res);
        cb4.block();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void server() throws Throwable {
        BlockCallback<Void, Throwable> serverCB = new BlockCallback<>();

        netServer.connectHandler(sock -> {
            sock.closeHandler(v -> {
                if (!serverCB.isCalled()) {
                    serverCB.failed(new Exception("closed unexpectedly"));
                }
            });
            sock.handler(new Handler<>() {
                int state = 0;
                private final RingBuffer inBuffer = RingBuffer.allocate(8192);
                private final Http2Decoder serverDecoder = new Http2Decoder(false);
                private final BinaryHttpSubContext ctx = serverDecoder.getCtx();

                @Override
                public void handle(Buffer buf) {
                    inBuffer.storeBytesFrom(ByteArrayChannel.fromFull(buf.getBytes()));
                    while (true) {
                        int res = serverDecoder.feed(inBuffer);
                        if (res == 0) {
                            HttpFrame frame = serverDecoder.getResult();
                            try {
                                handleFrame(frame);
                            } catch (Throwable e) {
                                serverCB.failed(e);
                                return;
                            }
                        } else {
                            String errMsg = serverDecoder.getErrorMessage();
                            if (errMsg == null) {
                                // want more data
                                break;
                            }
                            serverCB.failed(new Exception(errMsg));
                            return;
                        }
                        if (inBuffer.used() == 0) {
                            break;
                        }
                    }
                }

                private void handleFrame(HttpFrame frame) throws Throwable {
                    System.out.println("got frame: " + frame);
                    if (state == 0) {
                        assertTrue("expecting preface, but got " + frame, frame instanceof Preface);
                        state = 1;
                    } else if (state == 1) {
                        assertTrue("expecting settings frame, but got " + frame, frame instanceof SettingsFrame);

                        // send settings
                        SettingsFrame settings = SettingsFrame.newServerSettings();
                        sock.write(Buffer.buffer(settings.serializeH2(ctx).toJavaArray()));
                        SettingsFrame ack = SettingsFrame.newAck();
                        sock.write(Buffer.buffer(ack.serializeH2(ctx).toJavaArray()));

                        state = 2;
                    } else if (state == 2) {
                        assertTrue("expecting settings ack, but got " + frame, frame instanceof SettingsFrame);
                        assertTrue(((SettingsFrame) frame).ack);
                        state = 3;
                    } else if (state == 3) {
                        assertTrue("expecting headers frame, but got " + frame, frame instanceof HeadersFrame);
                        assertEquals(1, frame.streamId);
                        HeadersFrame headersFrame = (HeadersFrame) frame;
                        assertTrue(headersFrame.endHeaders);
                        assertTrue(headersFrame.endStream);
                        assertArrayEquals("/xyz".getBytes(), headersFrame.get(":path"));

                        // send push promise
                        PushPromiseFrame pushPromise = PushPromiseFrame.newSimple(
                            "http", "GET", "/abc");
                        pushPromise.streamId = 1;
                        pushPromise.endHeaders = false;
                        pushPromise.padded = true;
                        pushPromise.promisedStreamId = 2;
                        pushPromise.padding = ByteArray.from("push-promise-padding");
                        sock.write(Buffer.buffer(pushPromise.serializeH2(ctx).toJavaArray()));
                        // continuation
                        ContinuationFrame cont = new ContinuationFrame();
                        cont.streamId = 1;
                        cont.endHeaders = true;
                        cont.headers = Collections.singletonList(new Header("xxx", "yyy"));
                        sock.write(Buffer.buffer(cont.serializeH2(ctx).toJavaArray()));
                        // headers frame for the resp
                        HeadersFrame headers = HeadersFrame.newResponse(200,
                            new Header("Content-Length", "333".length() + ""));
                        headers.streamId = 1;
                        sock.write(Buffer.buffer(headers.serializeH2(ctx).toJavaArray()));
                        // data frame for the resp
                        DataFrame data = new DataFrame();
                        data.streamId = 1;
                        data.endStream = true;
                        data.data = ByteArray.from("333");
                        sock.write(Buffer.buffer(data.serializeH2(ctx).toJavaArray()));
                        // headers frame for push-promise resp
                        headers = HeadersFrame.newResponse(200,
                            new Header("Content-Length", "22".length() + ""));
                        headers.streamId = 2;
                        sock.write(Buffer.buffer(headers.serializeH2(ctx).toJavaArray()));
                        // data frame for the push promise
                        data = new DataFrame();
                        data.streamId = 2;
                        data.endStream = true;
                        data.data = ByteArray.from("22");
                        sock.write(Buffer.buffer(data.serializeH2(ctx).toJavaArray()));

                        serverCB.succeeded();

                        state = 4;
                    } else {
                        throw new Exception("unexpected");
                    }
                }
            });
        });
        netServer.listen(myListenPort);

        BlockCallback<String, Throwable> pushCB = new BlockCallback<>();
        BlockCallback<String, Throwable> normalCB = new BlockCallback<>();
        var req = httpClient.get(myListenPort, "127.0.0.1", "/xyz");
        req.pushHandler(pReq -> pReq.handler(resp -> resp.bodyHandler(body -> pushCB.succeeded(body.toString()))));
        req.handler(resp -> resp.bodyHandler(body -> normalCB.succeeded(body.toString()))).end();

        serverCB.block();
        assertEquals("22", pushCB.block());
        assertEquals("333", normalCB.block());
    }

    @Test
    public void control() throws Throwable {
        BlockCallback<Void, Throwable> cb = new BlockCallback<>();
        netClient.connect(vertxListenPort, "127.0.0.1", r -> {
            if (r.failed()) {
                cb.failed(r.cause());
                return;
            }
            var sock = r.result();
            sock.handler(new Handler<>() {
                private int state = 0;

                @Override
                public void handle(Buffer buf) {
                    RingBuffer inBuffer = RingBuffer.allocate(8192);
                    inBuffer.storeBytesFrom(ByteArrayChannel.fromFull(buf.getBytes()));
                    while (true) {
                        int res = decoder.feed(inBuffer);
                        if (res == 0) {
                            HttpFrame frame = decoder.getResult();
                            try {
                                handleFrame(frame);
                            } catch (Throwable e) {
                                cb.failed(e);
                                return;
                            }
                        } else {
                            String errMsg = decoder.getErrorMessage();
                            if (errMsg == null) {
                                // want more data
                                break;
                            }
                            cb.failed(new Exception(errMsg));
                            return;
                        }
                        if (inBuffer.used() == 0) {
                            break;
                        }
                    }
                }

                private void handleFrame(HttpFrame frame) throws Throwable {
                    if (state == 0) {
                        assertTrue("expecting SettingsFrame, but got " + frame, frame instanceof SettingsFrame);

                        SettingsFrame settings = SettingsFrame.newAck();
                        sock.write(Buffer.buffer(settings.serializeH2(ctx).toJavaArray()));
                        state = 1;
                    } else if (state == 1) {
                        assertTrue("expecting Settings ack, but got " + frame, frame instanceof SettingsFrame);
                        assertTrue(((SettingsFrame) frame).ack);

                        // send ping
                        PingFrame ping = new PingFrame();
                        ping.data = ByteArray.from("pingdata").int64(0);
                        sock.write(Buffer.buffer(ping.serializeH2(ctx).toJavaArray()));
                        state = 2;
                    } else if (state == 2) {
                        assertTrue("expecting PingFrame, but got " + frame, frame instanceof PingFrame);
                        assertTrue(((PingFrame) frame).ack);

                        // send window update
                        WindowUpdateFrame windowUpdate = new WindowUpdateFrame();
                        windowUpdate.windowSizeIncrement = 19;
                        sock.write(Buffer.buffer(windowUpdate.serializeH2(ctx).toJavaArray()));

                        // send priority
                        PriorityFrame priority = new PriorityFrame();
                        priority.streamId = 13;
                        priority.streamDependency = 11;
                        priority.weight = 10;
                        sock.write(Buffer.buffer(priority.serializeH2(ctx).toJavaArray()));
                        cb.succeeded();

                        state = 3;
                    } else {
                        throw new Exception("unexpected");
                    }
                }
            });
            sock.closeHandler(v -> {
                if (!cb.isCalled()) {
                    cb.failed(new Exception("closed unexpectedly"));
                }
            });
            try {
                Preface preface = new Preface();
                sock.write(Buffer.buffer(preface.serializeH2(ctx).toJavaArray()));
                SettingsFrame settings = SettingsFrame.newClientSettings();
                sock.write(Buffer.buffer(settings.serializeH2(ctx).toJavaArray()));
            } catch (Exception e) {
                cb.failed(e);
            }
        });
        cb.block();
    }
}
