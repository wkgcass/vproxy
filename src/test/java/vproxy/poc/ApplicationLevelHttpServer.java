package vproxy.poc;

import vclient.HttpClient;
import vclient.HttpResponse;
import vjson.JSON;
import vjson.util.ObjectBuilder;
import vjson.util.Transformer;
import vproxy.util.Logger;
import vproxy.util.Utils;
import vserver.RoutingContext;
import vserver.HttpServer;
import vserver.Tool;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ApplicationLevelHttpServer {
    public static void main(String[] args) throws Exception {
        System.out.println("Start server");
        new ApplicationLevelHttpServer().runServer();
        Thread.sleep(1_000);
        System.out.println("Start client to build initial data");
        new ApplicationLevelHttpServer().runClient();
    }

    private static class Service {
        final UUID id;
        final String name;
        String address;
        int port;

        private Service(String name, String address, int port) {
            this.id = UUID.randomUUID();
            this.name = name;
            this.address = address;
            this.port = port;
        }
    }

    private final List<Service> services = new LinkedList<>();
    private final Transformer tf = new Transformer()
        .addRule(Service.class, s -> new ObjectBuilder()
            .put("id", s.id.toString())
            .put("name", s.name)
            .put("ingressAddress", s.address)
            .put("ingressPort", s.port)
            .build());

    private void runClient() throws Exception {
        HttpClient client = HttpClient.to("127.0.0.1", 8080);
        client.pst("/api/v1/services").send(new ObjectBuilder()
                .put("name", "myservice1")
                .put("address", "192.168.0.1")
                .put("port", 80)
                .build(),
            (err, resp) -> printResponse("myservice1", err, resp));

        client.pst("/api/v1/services").send(new ObjectBuilder()
                .put("name", "myservice2")
                .put("address", "192.168.0.2")
                .put("port", 80)
                .build(),
            (err, resp) -> printResponse("myservice2", err, resp));

        Thread.sleep(500);
        client.get("/api/v1/services").send((err, resp) -> {
            if (err != null) {
                System.out.println("Request to fetch services failed: " + err);
                err.printStackTrace();
                return;
            }
            if (resp.status() != 200) {
                System.out.println("Request to fetch services failed: " + resp.bodyAsString());
                return;
            }
            JSON.Instance inst = resp.bodyAsJson();
            System.out.println("Fetch services result:");
            System.out.println(inst.pretty());
        });
    }

    private void printResponse(String name, IOException err, HttpResponse resp) {
        if (err != null) {
            System.out.println("Request failed for " + name + ": " + err);
            err.printStackTrace();
            return;
        }
        if (resp.status() != 200) {
            System.out.println("Request failed for " + name + ": " + resp.bodyAsString());
        } else {
            System.out.println("Request succeeded for " + name);
        }
    }

    private void runServer() throws Exception {
        HttpServer.create()
            .all("/api/v1/*", Tool.bodyJsonHandler())
            .get("/api/v1/services/:serviceId", this::getService)
            .get("/api/v1/services", this::listServices)
            .pst("/api/v1/services", this::createService)
            .put("/api/v1/services/:serviceId", this::updateService)
            .del("/api/v1/services/:serviceId", this::deleteService)
            .listen(8080);
    }

    private void listServices(RoutingContext rctx) {
        Logger.alert("listServices called");
        rctx.response().status(200).end(tf.transform(services));
    }

    private void createService(RoutingContext rctx) {
        JSON.Instance body = rctx.get(Tool.bodyJson);
        Logger.alert("listServices called with " + body);
        Service service;
        try {
            JSON.Object o = (JSON.Object) body;
            String name = o.getString("name");
            String address = o.getString("address");
            int port = o.getInt("port");

            if (!Utils.isIpLiteral(address)) {
                throw new IllegalArgumentException();
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException();
            }

            service = new Service(name, address, port);
            services.add(service);
        } catch (RuntimeException e) {
            rctx.response().status(400)
                .end(new ObjectBuilder()
                    .put("message", "invalid request body")
                    .build());
            return;
        }
        rctx.response().status(200).end(tf.transform(service));
    }

    private void getService(RoutingContext rctx) {
        String serviceId = rctx.param("serviceId");
        Logger.alert("getService called with `" + serviceId + "`");
        Optional<Service> ret = services.stream().filter(s -> s.id.toString().equals(serviceId)).findAny();
        if (ret.isPresent()) {
            rctx.response().status(200).end(tf.transform(ret.get()));
        } else {
            rctx.response().status(404)
                .end(new ObjectBuilder()
                    .put("message", "service with id `" + serviceId + "` not found")
                    .build());
        }
    }

    private void updateService(RoutingContext rctx) {
        String serviceId = rctx.param("serviceId");
        JSON.Instance body = rctx.get(Tool.bodyJson);
        Logger.alert("updateService called with `" + serviceId + "` and " + body);

        String address = null;
        int port = -1;
        try {
            JSON.Object o = (JSON.Object) body;
            if (o.containsKey("address")) {
                address = o.getString("address");
                if (!Utils.isIpLiteral(address)) {
                    throw new IllegalArgumentException();
                }
            }
            if (o.containsKey("port")) {
                port = o.getInt("port");
                if (port < 1 || port > 65535) {
                    throw new IllegalArgumentException();
                }
            }
        } catch (RuntimeException e) {
            rctx.response().status(400)
                .end(new ObjectBuilder()
                    .put("message", "invalid request body")
                    .build());
            return;
        }

        Optional<Service> ret = services.stream().filter(s -> s.id.toString().equals(serviceId)).findAny();
        if (ret.isPresent()) {
            Service s = ret.get();
            if (address != null) {
                s.address = address;
            }
            if (port != -1) {
                s.port = port;
            }
            rctx.response().status(204).end();
        } else {
            rctx.response().status(404)
                .end(new ObjectBuilder()
                    .put("message", "service with id `" + serviceId + "` not found")
                    .build());
        }
    }

    private void deleteService(RoutingContext rctx) {
        String serviceId = rctx.param("serviceId");
        Logger.alert("deleteService called with `" + serviceId + "`");
        Optional<Service> ret = services.stream().filter(s -> s.id.toString().equals(serviceId)).findAny();
        if (ret.isPresent()) {
            services.remove(ret.get());
            rctx.response().status(204).end();
        } else {
            rctx.response().status(404)
                .end(new ObjectBuilder()
                    .put("message", "service with id `" + serviceId + "` not found")
                    .build());
        }
    }
}
