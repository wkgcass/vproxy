package vproxy.test.cases;

import com.alibaba.dubbo.config.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import vproxy.component.app.TcpLB;
import vproxy.component.check.CheckProtocol;
import vproxy.component.check.HealthCheckConfig;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.svrgroup.Method;
import vproxy.component.svrgroup.ServerGroup;
import vproxy.component.svrgroup.ServerGroups;
import vproxy.poc.dubbo.GreetingsService;
import vproxy.poc.grpc.GreeterGrpc;
import vproxy.poc.grpc.HelloRequest;
import vproxy.poc.grpc.HelloResponse;
import vproxy.poc.thrift.HelloWorldService;
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

import java.net.InetAddress;
import java.net.InetSocketAddress;
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
    private ServerGroups sgs;
    private int step = 0;

    @Before
    public void setUp() throws Exception {
        elg = new EventLoopGroup("elg0");
        elg.add("el0");

        ServerGroup sg = new ServerGroup("sg0", elg,
            new HealthCheckConfig(1000, 10000, 1, 3, CheckProtocol.tcpDelay), Method.wrr);
        sg.add("svr1", new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port1), 10);
        sg.add("svr2", new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port2), 10);

        // set to up
        sg.getServerHandles().forEach(h -> h.healthy = true);

        sgs = new ServerGroups("sgs0");
        sgs.add(sg, 10);
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
            "tl0", elg, elg, new InetSocketAddress("0.0.0.0", lbPort), sgs, 10000, 16384, 16384, protocol, SecurityGroup.allowAll()
        );
        lb.start();
    }

    private void initDubboLb() throws Exception {
        ServerGroups sgs = new ServerGroups("dubboSgs");
        ServerGroup sg = new ServerGroup("dubboSg", elg, new HealthCheckConfig(1000, 10000, 1, 3), Method.wrr);
        sgs.add(sg, 10);
        lb = new TcpLB(
            "tl0", elg, elg, new InetSocketAddress("0.0.0.0", lbPort), sgs, 10000, 16384, 16384, "dubbo", SecurityGroup.allowAll()
        );
        lb.start();
        sg.add("svr3", new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port3dubbo), 10);
        sg.add("svr4", new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port4dubbo), 10);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void h2() throws Throwable {
        Vertx vertx = Vertx.vertx();
        try {
            Throwable[] err = new Throwable[]{null};

            Handler<HttpServerRequest> handler = req -> {
                try {
                    assertEquals("/", req.uri());
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

            while (step != 4 && err[0] == null) {
                Thread.sleep(1);
            }
            if (err[0] != null)
                throw err[0];
            assertEquals(4, step);
            assertEquals(1, svr1[0]);
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
            new Thread(server1::serve).start();
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
            new Thread(server2::serve).start();
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
        Vertx vertx = Vertx.vertx();
        try {
            Throwable[] err = new Throwable[]{null};

            Handler<HttpServerRequest> handler = req -> {
                try {
                    assertEquals("/", req.uri());
                } catch (Throwable t) {
                    err[0] = t;
                }
                String resp = "" + req.localAddress().port();
                req.response().end("resp-" + resp);
            };
            vertx.createHttpServer().requestHandler(handler).listen(port1);
            vertx.createHttpServer().requestHandler(handler).listen(port2);

            initLb("http");

            int[] conn = {0};
            HttpClient client = vertx.createHttpClient(new HttpClientOptions()
                .setMaxPoolSize(1)
                .setKeepAlive(true));
            client.connectionHandler(connV -> ++conn[0]);

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

            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/"));
            doWithReq.accept(client.get(lbPort, "127.0.0.1", "/"));

            while (step != 2 && err[0] == null) {
                Thread.sleep(1);
            }
            if (err[0] != null)
                throw err[0];
            assertEquals(2, step);
            assertEquals(1, svr1[0]);
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
}
