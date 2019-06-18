package vproxy.poc;

import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import vproxy.component.proxy.ConnectorGen;
import vproxy.processor.Processor;
import vproxy.component.proxy.Proxy;
import vproxy.component.proxy.ProxyNetConfig;
import vproxy.connection.BindServer;
import vproxy.connection.Connection;
import vproxy.connection.Connector;
import vproxy.connection.NetEventLoop;
import vproxy.poc.grpc.GreeterGrpc;
import vproxy.poc.grpc.HelloRequest;
import vproxy.poc.grpc.HelloResponse;
import vproxy.processor.ProcessorProvider;
import vproxy.selector.SelectorEventLoop;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GrpcOverH2Proxy {
    public static void main(String[] args) throws Exception {
        // see hello_world.proto in resources folder
        // compiled into net.cassite.vproxy.poc.grpc using protobuf 3.6.0 and grpc 1.12.0

        // server1
        Server server17890 = ServerBuilder.forPort(17890)
            .addService(new GreeterImpl("17890"))
            .build()
            .start();
        // server2
        Server server17891 = ServerBuilder.forPort(17891)
            .addService(new GreeterImpl("17891"))
            .build()
            .start();

        // start proxy
        NetEventLoop el = new NetEventLoop(SelectorEventLoop.open());
        InetSocketAddress backend1 = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 17890);
        InetSocketAddress backend2 = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 17891);
        InetSocketAddress frontend = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 7890);
        BindServer svr = BindServer.create(frontend);
        Proxy proxy = new Proxy(new ProxyNetConfig()
            .setInBufferSize(8)
            .setOutBufferSize(4) // make them small to see whether the lib will work when payload is large
            .setAcceptLoop(el)
            .setConnGen(new ConnectorGen() {
                @Override
                public Type type() {
                    return Type.processor;
                }

                private int count = 0;

                @Override
                public Connector genConnector(Connection accepted) {
                    int n = count++;
                    if (n % 2 == 0) {
                        return new Connector(backend1);
                    } else {
                        return new Connector(backend2);
                    }
                }

                @Override
                public Processor processor() {
                    return ProcessorProvider.getInstance().get("h2");
                }
            })
            .setHandleLoopProvider(() -> el)
            .setServer(svr),
            server -> {
            });
        proxy.handle();
        el.getSelectorEventLoop().loop(Thread::new);

        // client
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", 7890)
            .usePlaintext()
            .build();

        // executor for client
        ExecutorService executor = Executors.newSingleThreadExecutor();
        int[] step = {0};

        // run
        System.out.println("start requests");
        {
            ListenableFuture<HelloResponse> future =
                GreeterGrpc.newFutureStub(channel)
                    .sayHello(HelloRequest.newBuilder().setName("req1").build());
            future.addListener(() -> {
                try {
                    HelloResponse resp = future.get();
                    System.out.println("request 1 get response: " + resp.getMessage());
                    ++step[0];
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);
        }
        {
            ListenableFuture<HelloResponse> future =
                GreeterGrpc.newFutureStub(channel)
                    .sayHello(HelloRequest.newBuilder().setName("req2").build());
            future.addListener(() -> {
                try {
                    HelloResponse resp = future.get();
                    System.out.println("request 2 get response: " + resp.getMessage());
                    ++step[0];
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);
        }

        while (step[0] != 2) {
            Thread.sleep(1);
        }
        channel.shutdownNow();
        server17890.shutdownNow();
        server17891.shutdownNow();
        channel.shutdownNow();
        executor.shutdownNow();
        Thread.sleep(100);
        el.getSelectorEventLoop().close();
    }

    private static class GreeterImpl extends GreeterGrpc.GreeterImplBase {
        private final String serverId;

        private GreeterImpl(String serverId) {
            this.serverId = serverId;
        }

        @Override
        public void sayHello(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
            String name = request.getName();
            System.out.println("server " + serverId + " get request with name: " + name);
            HelloResponse response = HelloResponse.newBuilder()
                .setMessage("Hello " + name + ", I'm " + serverId).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}
