package io.vproxy.vproxyx.nexus.entity;

import vjson.JSON;
import vjson.JSONObject;
import vjson.deserializer.rule.LongRule;
import vjson.deserializer.rule.ObjectRule;
import vjson.deserializer.rule.Rule;
import vjson.deserializer.rule.StringRule;
import vjson.util.ObjectBuilder;

public class LinkPeer implements JSONObject {
    public String node;
    public long cost;

    public static final Rule<LinkPeer> rule = new ObjectRule<>(LinkPeer::new)
        .put("node", (o, it) -> o.node = it, StringRule.get())
        .put("cost", (o, it) -> o.cost = it, LongRule.get());

    public LinkPeer() {
    }

    public LinkPeer(String node, long cost) {
        this.node = node;
        this.cost = cost;
    }

    public String validate(String path) {
        path += ".";
        if (node == null)
            return "missing " + path + "node";
        if (cost <= 0)
            return "invalid " + path + "distance, value = " + cost + " < 0";
        return null;
    }

    @Override
    public JSON.Object toJson() {
        return new ObjectBuilder()
            .put("node", node)
            .put("cost", cost)
            .build();
    }
}
