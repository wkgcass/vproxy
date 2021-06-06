package vproxy.test.cases;

import com.alibaba.dubbo.config.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.TFastFramedTransport;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import vproxy.base.component.check.CheckProtocol;
import vproxy.base.component.check.HealthCheckConfig;
import vproxy.base.component.elgroup.EventLoopGroup;
import vproxy.base.component.svrgroup.Method;
import vproxy.base.component.svrgroup.ServerGroup;
import vproxy.base.util.AnnotationKeys;
import vproxy.base.util.Annotations;
import vproxy.base.util.thread.VProxyThread;
import vproxy.component.app.TcpLB;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.svrgroup.Upstream;
import vproxy.poc.dubbo.GreetingsService;
import vproxy.poc.grpc.GreeterGrpc;
import vproxy.poc.grpc.HelloRequest;
import vproxy.poc.grpc.HelloResponse;
import vproxy.poc.thrift.HelloWorldService;
import vproxy.vfd.IP;
import vproxy.vfd.IPPort;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestProtocols {
    private static final int lbPort = 7890;
    private static final int port1 = 17891;
    private static final int port2 = 17892;

    // dubbo cannot shutdown, so use standalone port for it
    private static final int port3dubbo = 17893;
    private static final int port4dubbo = 17894;

    private TcpLB lb;
    private EventLoopGroup elg;
    private Upstream ups;
    private int step = 0;

    @Before
    public void setUp() throws Exception {
        elg = new EventLoopGroup("elg0");
        elg.add("el0");

        ServerGroup sg1 = new ServerGroup("test-s1", elg,
            new HealthCheckConfig(1000, 10000, 1, 3, CheckProtocol.tcpDelay), Method.wrr);
        sg1.setAnnotations(new Annotations(Map.of(
            AnnotationKeys.ServerGroup_HintHost.name, "s1.test.com",
            AnnotationKeys.ServerGroup_HintUri.name, "/a")));
        sg1.add("svr1", new IPPort(IP.from("127.0.0.1"), port1), 10);
        ServerGroup sg2 = new ServerGroup("test-s2", elg,
            new HealthCheckConfig(1000, 10000, 1, 3, CheckProtocol.tcpDelay), Method.wrr);
        sg2.setAnnotations(new Annotations(Map.of(
            AnnotationKeys.ServerGroup_HintHost.name, "s2.test.com",
            AnnotationKeys.ServerGroup_HintUri.name, "/b"
        )));
        sg2.add("svr2", new IPPort(IP.from("127.0.0.1"), port2), 10);

        // set to up
        sg1.getServerHandles().forEach(h -> h.healthy = true);
        sg2.getServerHandles().forEach(h -> h.healthy = true);

        ups = new Upstream("ups0");
        ups.add(sg1, 10);
        ups.add(sg2, 10);
    }

    @After
    public void tearDown() {
        if (lb != null) {
            lb.destroy();
        }
        if (elg != null) {
            elg.close();
        }
    }

    private void initLb(String protocol) throws Exception {
        lb = new TcpLB(
            "tl0", elg, elg, new IPPort("0.0.0.0", lbPort), ups, 10000, 16384, 16384, protocol, null, null, SecurityGroup.allowAll()
        );
        lb.start();
    }

    private void initDubboLb() throws Exception {
        Upstream ups = new Upstream("dubboUps");
        ServerGroup sg = new ServerGroup("dubboSg", elg, new HealthCheckConfig(1000, 10000, 1, 3), Method.wrr);
        ups.add(sg, 10);
        lb = new TcpLB(
            "tl0", elg, elg, new IPPort("0.0.0.0", lbPort), ups, 10000, 16384, 16384, "dubbo", null, null, SecurityGroup.allowAll()
        );
        lb.start();
        sg.add("svr3", new IPPort(IP.from("127.0.0.1"), port3dubbo), 10);
        sg.add("svr4", new IPPort(IP.from("127.0.0.1"), port4dubbo), 10);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void h2() throws Throwable {
        Vertx vertx = Vertx.vertx();
        try {
            Throwable[] err = new Throwable[]{null};

            Handler<HttpServerRequest> handler = req -> {
                try {
                    assertTrue(req.uri().equals("/") ||
                        req.uri().equals("/a") ||
                        req.uri().equals("/b"));
                } catch (Throwable t) {
                    err[0] = t;
                }
                String resp = "" + req.localAddress().port();
                req.response().push(HttpMethod.GET, "/push-promise", r -> {
                    if (r.failed()) {
                        err[0] = r.cause();
                    } else {
                        r.result().end("push-promise-" + resp);
                    }
                });
                req.response().end("resp-" + resp);
            };
            vertx.createHttpServer().requestHandler(handler).listen(port1);
            vertx.createHttpServer().requestHandler(handler).listen(port2);

            initLb("h2");

            int[] conn = {0};
            HttpClient client = vertx.createHttpClient(new HttpClientOptions()
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setHttp2ClearTextUpgrade(false));
            client.connectionHandler(connV -> ++conn[0]);

            int[] svr1 = {0};
            int[] svr2 = {0};
            Consumer<HttpClientRequest> doWithReq = req -> {
                String[] expect = {null};
                req.pushHandler(pushReq -> pushReq.handler(resp -> resp.bodyHandler(buf -> {
                    try {
                        assertEquals("/push-promise", pushReq.uri());
                        if (expect[0] == null) {
                            expect[0] = buf.toString().substring("push-promise-".length());
                        } else {
                            assertEquals("push-promise-" + expect[0], buf.toString());
                        }
                        ++step;
                    } catch (Throwable t) {
                        err[0] = t;
                    }
                })));
                req.handler(resp -> resp.bodyHandler(buf -> {
                    try {
                        if (expect[0] == null) {
                            expect[0] = buf.toString().substring("resp-".length());
                        } else {
                            assertEquals("resp-" + expect[0], buf.toString());
                        }
                        if (expect[0].equals("" + port1)) {
                            ++svr1[0];
                        } else {
                            ++svr2[0];
                        }
                        ++step;
                    } catch (Throwable t) {
                        err[0] = t;
                    }
                }));
                req.end();
            };

            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/"));
            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/"));
            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/"));
            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/"));

            while (step != 8 && err[0] == null) {
                Thread.sleep(1);
            }
            if (err[0] != null)
                throw err[0];
            assertEquals(8, step);
            assertEquals(2, svr1[0]);
            assertEquals(2, svr2[0]);
            assertEquals(1, conn[0]);

            // =====
            // test forwarding using host

            client.close();
            client = vertx.createHttpClient(new HttpClientOptions()
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setHttp2ClearTextUpgrade(false));
            client.connectionHandler(connV -> ++conn[0]);
            step = 0;
            svr1[0] = 0;
            svr2[0] = 0;
            conn[0] = 0;

            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/")
                .putHeader("Host", "s1.test.com"));
            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/")
                .putHeader("Host", "s1.test.com"));
            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/")
                .putHeader("Host", "s1.test.com"));
            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/")
                .putHeader("Host", "s2.test.com"));

            while (step != 8 && err[0] == null) {
                Thread.sleep(1);
            }
            if (err[0] != null)
                throw err[0];
            assertEquals(8, step);
            assertEquals(3, svr1[0]);
            assertEquals(1, svr2[0]);
            assertEquals(1, conn[0]);

            // =====
            // test forwarding using uri

            client.close();
            client = vertx.createHttpClient(new HttpClientOptions()
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setHttp2ClearTextUpgrade(false));
            client.connectionHandler(connV -> ++conn[0]);
            step = 0;
            svr1[0] = 0;
            svr2[0] = 0;
            conn[0] = 0;

            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/a"));
            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/a"));
            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/a"));
            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/b"));

            while (step != 8 && err[0] == null) {
                Thread.sleep(1);
            }
            if (err[0] != null)
                throw err[0];
            assertEquals(8, step);
            assertEquals(3, svr1[0]);
            assertEquals(1, svr2[0]);
            assertEquals(1, conn[0]);
        } finally {
            boolean[] closeDone = {false};
            vertx.close(v -> closeDone[0] = true);
            while (!closeDone[0]) {
                Thread.sleep(1);
            }
            Thread.sleep(200);
        }
    }

    @Test
    public void grpcOverH2() throws Exception {
        class GreeterImpl extends GreeterGrpc.GreeterImplBase {
            private final String serverId;

            private GreeterImpl(String serverId) {
                this.serverId = serverId;
            }

            @Override
            public void sayHello(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
                String name = request.getName();
                HelloResponse response = HelloResponse.newBuilder()
                    .setMessage(name + "/" + serverId).build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        }

        // server1
        Server svr1 = ServerBuilder.forPort(port1)
            .addService(new GreeterImpl("" + port1))
            .build()
            .start();
        // server2
        Server svr2 = ServerBuilder.forPort(port2)
            .addService(new GreeterImpl("" + port2))
            .build()
            .start();

        initLb("h2");

        // client
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", lbPort)
            .usePlaintext()
            .build();

        try {
            int get1 = 0;
            int get2 = 0;

            for (int i = 0; i < 10; ++i) {
                HelloResponse resp1 = GreeterGrpc.newBlockingStub(channel)
                    .sayHello(HelloRequest.newBuilder().setName("req" + i).build());
                assertTrue(resp1.getMessage().startsWith("req" + i + "/"));
                if (resp1.getMessage().substring("req?/".length()).equals("" + port1)) {
                    ++get1;
                } else {
                    ++get2;
                }
            }

            assertEquals(5, get1);
            assertEquals(5, get2);
        } finally {
            channel.shutdownNow();
            svr1.shutdownNow();
            svr2.shutdownNow();
        }
    }

    @Test
    public void thriftFramed() throws Exception {
        class HelloWorldImpl implements HelloWorldService.Iface {
            private final String serverId;

            HelloWorldImpl(String serverId) {
                this.serverId = serverId;
            }

            @Override
            public String sayHello(String name) {
                return name + "/" + serverId;
            }
        }

        TServer server1;
        // server
        {
            @SuppressWarnings("unchecked")
            TProcessor tprocessor = new HelloWorldService.Processor(new HelloWorldImpl("" + port1));
            TServerSocket serverTransport = new TServerSocket(port1);
            TServer.Args serverArgs = new TServer.Args(serverTransport);
            serverArgs.processor(tprocessor);
            serverArgs.protocolFactory(new TBinaryProtocol.Factory());
            serverArgs.transportFactory(new TFastFramedTransport.Factory());
            server1 = new TSimpleServer(serverArgs);
            VProxyThread.create(server1::serve, "server1").start();
        }
        TServer server2;
        // server
        {
            @SuppressWarnings("unchecked")
            TProcessor tprocessor = new HelloWorldService.Processor(new HelloWorldImpl("" + port2));
            TServerSocket serverTransport = new TServerSocket(port2);
            TServer.Args serverArgs = new TServer.Args(serverTransport);
            serverArgs.processor(tprocessor);
            serverArgs.protocolFactory(new TBinaryProtocol.Factory());
            serverArgs.transportFactory(new TFastFramedTransport.Factory());
            server2 = new TSimpleServer(serverArgs);
            VProxyThread.create(server2::serve, "server2").start();
        }

        // lb
        initLb("framed-int32");

        // client
        TSocket clientSock;
        HelloWorldService.Client client;
        {
            clientSock = new TSocket("127.0.0.1", lbPort, 1000);
            TTransport transport = new TFastFramedTransport(clientSock);
            TProtocol protocol = new TBinaryProtocol(transport);

            clientSock.open();
            client = new HelloWorldService.Client(protocol);
        }

        int resp1 = 0;
        int resp2 = 0;
        // request
        for (int i = 0; i < 10; ++i) {
            String result = client.sayHello("req" + i);
            String[] arr = result.split("/");
            assertEquals("req" + i, arr[0]);
            assertTrue(arr[1].equals("" + port1) || arr[1].equals("" + port2));
            if (arr[1].equals("" + port1)) {
                ++resp1;
            } else {
                ++resp2;
            }
        }

        // check
        assertEquals(5, resp1);
        assertEquals(5, resp2);

        // cleanup
        clientSock.close();
        server1.stop();
        server2.stop();
    }

    @Test
    public void dubbo() throws Exception {
        class GreetingsServiceImpl implements GreetingsService {
            private final String serverId;

            GreetingsServiceImpl(String serverId) {
                this.serverId = serverId;
            }

            @Override
            public String sayHi(String name) {
                return name + "/" + serverId;
            }
        }

        // servers
        {
            ServiceConfig<GreetingsService> service1 = new ServiceConfig<>();
            service1.setApplication(new ApplicationConfig("provider-1"));
            service1.setInterface(GreetingsService.class);
            {
                RegistryConfig registryConfig = new RegistryConfig();
                registryConfig.setRegister(false);
                service1.setRegistry(registryConfig);
            }
            {
                ProtocolConfig protocolConfig = new ProtocolConfig();
                protocolConfig.setName("dubbo");
                protocolConfig.setHost("127.0.0.1");
                protocolConfig.setPort(port3dubbo);
                service1.setProtocol(protocolConfig);
            }
            service1.setRef(new GreetingsServiceImpl("" + port3dubbo));
            service1.export();
        }
        {
            ServiceConfig<GreetingsService> service2 = new ServiceConfig<>();
            service2.setApplication(new ApplicationConfig("provider-1"));
            service2.setInterface(GreetingsService.class);
            {
                RegistryConfig registryConfig = new RegistryConfig();
                registryConfig.setRegister(false);
                service2.setRegistry(registryConfig);
            }
            {
                ProtocolConfig protocolConfig = new ProtocolConfig();
                protocolConfig.setName("dubbo");
                protocolConfig.setHost("127.0.0.1");
                protocolConfig.setPort(port4dubbo);
                service2.setProtocol(protocolConfig);
            }
            service2.setRef(new GreetingsServiceImpl("" + port4dubbo));
            service2.export();
        }

        // lb
        initDubboLb();

        // client
        ReferenceConfig<GreetingsService> reference = new ReferenceConfig<>();
        reference.setUrl("dubbo://127.0.0.1:" + lbPort);
        reference.setApplication(new ApplicationConfig("client"));
        reference.setInterface(GreetingsService.class);
        GreetingsService greetingsService = reference.get();

        // requests
        int resp1 = 0;
        int resp2 = 0;
        for (int i = 0; i < 10; ++i) {
            String result = greetingsService.sayHi("req" + i);
            String[] arr = result.split("/");
            assertEquals("req" + i, arr[0]);
            assertTrue(arr[1].equals("" + port3dubbo) || arr[1].equals("" + port4dubbo));
            if (arr[1].equals("" + port3dubbo)) {
                ++resp1;
            } else {
                ++resp2;
            }
        }

        // check
        assertEquals(5, resp1);
        assertEquals(5, resp2);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void h1() throws Throwable {
        int[] conn = {0};
        int[] svr1 = {0};
        int[] svr2 = {0};
        Throwable[] err = new Throwable[]{null};
        HttpClient client;
        Consumer<HttpClientRequest> doWithReq = req -> {
            req.handler(resp -> resp.bodyHandler(buf -> {
                try {
                    String expect = buf.toString().substring("resp-".length());
                    if (expect.equals("" + port1)) {
                        ++svr1[0];
                    } else {
                        ++svr2[0];
                    }
                    ++step;
                } catch (Throwable t) {
                    err[0] = t;
                }
            }));
            req.end();
        };

        Vertx vertx = Vertx.vertx();
        try {
            Handler<HttpServerRequest> handler = req -> {
                try {
                    assertTrue(req.uri().equals("/") ||
                        req.uri().equals("/a") ||
                        req.uri().equals("/b")
                    );
                } catch (Throwable t) {
                    err[0] = t;
                }
                String resp = "" + req.localAddress().port();
                req.response().end("resp-" + resp);
            };
            vertx.createHttpServer().requestHandler(handler).listen(port1);
            vertx.createHttpServer().requestHandler(handler).listen(port2);

            initLb("http/1.x");

            conn[0] = 0;
            client = vertx.createHttpClient(new HttpClientOptions()
                .setMaxPoolSize(1)
                .setKeepAlive(true));
            client.connectionHandler(connV -> ++conn[0]);

            svr1[0] = 0;
            svr2[0] = 0;

            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/"));
            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/"));
            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/"));
            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/"));

            while (step != 4 && err[0] == null) {
                Thread.sleep(1);
            }
            if (err[0] != null)
                throw err[0];
            assertEquals(4, step);
            assertEquals(2, svr1[0]);
            assertEquals(2, svr2[0]);
            assertEquals(1, conn[0]);

            // =======
            // test forwarding using Host

            client.close();
            client = vertx.createHttpClient(new HttpClientOptions()
                .setMaxPoolSize(1)
                .setKeepAlive(false));
            client.connectionHandler(connV -> ++conn[0]);
            step = 0;
            svr1[0] = 0;
            svr2[0] = 0;
            conn[0] = 0;

            {
                HttpClientRequest req = client.get(lbPort, "127.0.0.1", "/");
                req.headers().add("Host", "s1.test.com");
                doWithReq.accept(req);
            }
            {
                HttpClientRequest req = client.get(lbPort, "127.0.0.1", "/");
                req.headers().add("Host", "s1.test.com");
                doWithReq.accept(req);
            }
            {
                HttpClientRequest req = client.get(lbPort, "127.0.0.1", "/");
                req.headers().add("Host", "s1.test.com");
                doWithReq.accept(req);
            }
            {
                HttpClientRequest req = client.get(lbPort, "127.0.0.1", "/");
                req.headers().add("Host", "s2.test.com");
                doWithReq.accept(req);
            }

            while (step != 4 && err[0] == null) {
                Thread.sleep(1);
            }
            if (err[0] != null)
                throw err[0];
            assertEquals(4, step);
            assertEquals(3, svr1[0]);
            assertEquals(1, svr2[0]);
            assertEquals(4, conn[0]);

            // =======
            // test forwarding using uri

            client.close();
            client = vertx.createHttpClient(new HttpClientOptions()
                .setMaxPoolSize(1));
            client.connectionHandler(connV -> ++conn[0]);
            step = 0;
            svr1[0] = 0;
            svr2[0] = 0;
            conn[0] = 0;

            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/a"));
            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/a"));
            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/a"));
            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/b"));

            while (step != 4 && err[0] == null) {
                Thread.sleep(1);
            }
            if (err[0] != null)
                throw err[0];
            assertEquals(4, step);
            assertEquals(3, svr1[0]);
            assertEquals(1, svr2[0]);
            assertEquals(1, conn[0]);

        } finally {
            boolean[] closeDone = {false};
            vertx.close(v -> closeDone[0] = true);
            while (!closeDone[0]) {
                Thread.sleep(1);
            }
            Thread.sleep(200);
        }
    }

    @Test
    public void h1websocket() throws Throwable {
        Vertx vertx = Vertx.vertx();
        try {
            Handler<ServerWebSocket> handler = sock -> {
                sock.write(Buffer.buffer(sock.path()));
                sock.end();
            };
            vertx.createHttpServer().websocketHandler(handler).listen(port1);
            vertx.createHttpServer().websocketHandler(handler).listen(port2);

            initLb("http/1.x");

            Throwable[] err = new Throwable[1];
            Set<String> results = new HashSet<>();
            for (int i = 0; i < 4; ++i) {
                vertx.createHttpClient().websocket(lbPort, "127.0.0.1", "/request-" + i, MultiMap.caseInsensitiveMultiMap(), WebsocketVersion.V13,
                    "", socket -> socket.handler(buf -> {
                        synchronized (results) {
                            results.add(buf.toString());
                        }
                        ++step;
                    }), e -> err[0] = e);
            }

            while (step != 4 && err[0] == null) {
                Thread.sleep(1);
            }
            if (err[0] != null) {
                throw err[0];
            }
            assertEquals(Set.of(
                "/request-0",
                "/request-1",
                "/request-2",
                "/request-3"
            ), results);
        } finally {
            boolean[] closeDone = {false};
            vertx.close(v -> closeDone[0] = true);
            while (!closeDone[0]) {
                Thread.sleep(1);
            }
            Thread.sleep(200);
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void generalHttp() throws Throwable {
        Vertx vertx = Vertx.vertx();
        try {
            Throwable[] err = new Throwable[]{null};

            int[] ver1 = {0};
            int[] ver2 = {0};
            Handler<HttpServerRequest> handler = req -> {
                try {
                    assertEquals("/", req.uri());
                } catch (Throwable t) {
                    err[0] = t;
                }
                if (req.version() == HttpVersion.HTTP_2) {
                    synchronized (ver2) {
                        ++ver2[0];
                    }
                } else {
                    synchronized (ver1) {
                        ++ver1[0];
                    }
                }
                String resp = "" + req.localAddress().port();
                req.response().end("resp-" + resp);
            };
            vertx.createHttpServer().requestHandler(handler).listen(port1);
            vertx.createHttpServer().requestHandler(handler).listen(port2);

            initLb("http");

            int[] conn = {0};
            HttpClient h1client = vertx.createHttpClient(new HttpClientOptions()
                .setMaxPoolSize(1)
                .setKeepAlive(true));
            h1client.connectionHandler(connV -> ++conn[0]);
            HttpClient h2client = vertx.createHttpClient(new HttpClientOptions()
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setHttp2ClearTextUpgrade(false));
            h2client.connectionHandler(connV -> ++conn[0]);

            int[] svr1 = {0};
            int[] svr2 = {0};
            Consumer<HttpClientRequest> doWithReq = req -> {
                req.handler(resp -> resp.bodyHandler(buf -> {
                    try {
                        String expect = buf.toString().substring("resp-".length());
                        if (expect.equals("" + port1)) {
                            ++svr1[0];
                        } else {
                            ++svr2[0];
                        }
                        ++step;
                    } catch (Throwable t) {
                        err[0] = t;
                    }
                }));
                req.end();
            };

            for (int i = 0; i < 100; ++i) {
                doWithReq.accept(h1client.get(lbPort, "127.0.0.1", "/"));
            }
            Thread.sleep(1000);
            for (int i = 0; i < 100; ++i) {
                doWithReq.accept(h2client.get(lbPort, "127.0.0.1", "/"));
            }

            while (step != 200 && err[0] == null) {
                Thread.sleep(1);
            }
            if (err[0] != null)
                throw err[0];
            assertEquals(200, step);
            assertEquals(1d, ((double) svr1[0]) / svr2[0], 0.05);
            assertEquals(2, conn[0]);
            assertEquals(100, ver1[0]);
            assertEquals(100, ver2[0]);
        } finally {
            boolean[] closeDone = {false};
            vertx.close(v -> closeDone[0] = true);
            while (!closeDone[0]) {
                Thread.sleep(1);
            }
            Thread.sleep(200);
        }
    }
}
