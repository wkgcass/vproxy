package vserver;

import vjson.JSON;
import vjson.ex.JsonParseException;
import vjson.util.ObjectBuilder;
import vproxybase.util.ByteArray;
import vserver.util.UTF8ByteArrayCharStream;

public class Tool {
    public static final RoutingContext.StorageKey<JSON.Instance> bodyJson = new RoutingContext.StorageKey<>() {
    };

    public static RoutingHandler bodyJsonHandler() {
        return ctx -> {
            ByteArray body = ctx.body();
            if (body != null) {
                JSON.Instance inst;
                try {
                    inst = JSON.parse(new UTF8ByteArrayCharStream(body));
                } catch (JsonParseException e) {
                    ctx.response().status(400)
                        .end(new ObjectBuilder()
                            .put("status", 400)
                            .put("reason", "Bad Request")
                            .put("message", "request body is not valid json: " + e.getMessage())
                            .build());
                    return;
                }
                ctx.put(bodyJson, inst);
            }
            ctx.next();
        };
    }

    private Tool() {
    }
}
