package io.vproxy.vproxyx.nexus.entity;

import vjson.JSON;
import vjson.JSONObject;
import vjson.deserializer.rule.ArrayRule;
import vjson.deserializer.rule.ObjectRule;
import vjson.deserializer.rule.Rule;
import vjson.deserializer.rule.StringRule;
import vjson.util.ObjectBuilder;

import java.util.ArrayList;
import java.util.List;

public class LinkReq implements JSONObject {
    public String node; // source node
    public List<String> path; // traversed paths
    public List<LinkPeer> peers; // directly linked nodes (i.e. peers)

    public static final Rule<LinkReq> rule = new ObjectRule<>(LinkReq::new)
        .put("node", (o, it) -> o.node = it, StringRule.get())
        .put("path", (o, it) -> o.path = it, new ArrayRule<List<String>, String>(
            ArrayList::new, List::add, StringRule.get()))
        .put("peers", (o, it) -> o.peers = it, new ArrayRule<List<LinkPeer>, LinkPeer>(
            ArrayList::new, List::add, LinkPeer.rule));

    public LinkReq() {
    }

    public String validate() {
        if (node == null)
            return "missing node";
        if (peers == null)
            return "missing peers";
        for (int i = 0; i < peers.size(); i++) {
            var peer = peers.get(i);
            var err = peer.validate("peers[" + i + "]");
            if (err != null)
                return err;
        }
        return null;
    }

    @Override
    public JSON.Object toJson() {
        return new ObjectBuilder()
            .put("node", node)
            .putArray("path", ab -> path.forEach(ab::add))
            .putArray("peers", ab -> peers.forEach(e -> ab.addInst(e.toJson())))
            .build();
    }

    @Override
    public String toString() {
        return toJson().stringify();
    }
}
