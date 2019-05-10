package net.cassite.vproxy.test.cases;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import net.cassite.vproxy.component.app.TcpLB;
import net.cassite.vproxy.component.check.HealthCheckConfig;
import net.cassite.vproxy.component.elgroup.EventLoopGroup;
import net.cassite.vproxy.component.secure.SecurityGroup;
import net.cassite.vproxy.component.svrgroup.Method;
import net.cassite.vproxy.component.svrgroup.ServerGroup;
import net.cassite.vproxy.component.svrgroup.ServerGroups;
import net.cassite.vproxy.poc.grpc.GreeterGrpc;
import net.cassite.vproxy.poc.grpc.HelloRequest;
import net.cassite.vproxy.poc.grpc.HelloResponse;
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

    private TcpLB lb;
    private EventLoopGroup elg;
    private ServerGroups sgs;
    private int step = 0;

    @Before
    public void setUp() throws Exception {
        elg = new EventLoopGroup("elg0");
        elg.add("el0");

        ServerGroup sg = new ServerGroup("sg0", elg,
            new HealthCheckConfig(1000, 200, 2, 3), Method.wrr);
        sg.add("svr1", new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port1), 10);
        sg.add("svr2", new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port2), 10);

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
            "tl0", elg, elg, new InetSocketAddress("127.0.0.1", lbPort), sgs, 10000, 16384, 16384, protocol, SecurityGroup.allowAll()
        );
        lb.start();
    }

    private void waitForHealthCheck() throws Exception {
        Thread.sleep(700);
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
            waitForHealthCheck();

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
            vertx.close();
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
        waitForHealthCheck();

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
}
