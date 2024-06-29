package io.vproxy.vproxyx.nexus.entity;

import io.vproxy.base.connection.ServerSock;
import io.vproxy.vfd.IPPort;
import vjson.JSON;
import vjson.JSONObject;
import vjson.deserializer.rule.IntRule;
import vjson.deserializer.rule.ObjectRule;
import vjson.deserializer.rule.Rule;
import vjson.deserializer.rule.StringRule;
import vjson.util.ObjectBuilder;

public class ProxyInstance implements JSONObject {
    public String id;
    public String node;
    public IPPort target;
    public int listen;

    public ServerSock serverSock;

    public static final Rule<ProxyInstance> rule = new ObjectRule<>(ProxyInstance::new)
        .put("id", (o, it) -> o.id = it, StringRule.get())
        .put("node", (o, it) -> o.node = it, StringRule.get())
        .put("target", (o, it) -> o.target = new IPPort(it), StringRule.get())
        .put("listen", (o, it) -> o.listen = it, IntRule.get());

    public ProxyInstance() {
    }

    @Override
    public JSON.Object toJson() {
        return new ObjectBuilder()
            .put("id", id)
            .put("node", node)
            .put("target", target.formatToIPPortString())
            .put("listen", listen)
            .build();
    }

    @Override
    public String toString() {
        return toJson().stringify();
    }

    public void close() {
        var serverSock = this.serverSock;
        this.serverSock = null;
        if (serverSock != null) {
            serverSock.close();
        }
    }
}
