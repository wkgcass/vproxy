package vproxy.component.khala;

import vclient.HttpClient;
import vclient.HttpResponse;
import vclient.impl.Http1ClientImpl;
import vjson.JSON;
import vjson.simple.SimpleArray;
import vjson.util.ArrayBuilder;
import vjson.util.ObjectBuilder;
import vproxy.component.exception.XException;
import vproxy.component.khala.protocol.KhalaMsg;
import vproxy.discovery.Discovery;
import vproxy.discovery.Node;
import vproxy.discovery.NodeDataHandler;
import vproxy.discovery.NodeListener;
import vproxy.util.Callback;
import vproxy.util.LogType;
import vproxy.util.Logger;
import vproxy.util.Tuple;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

/**
 * the service network
 * protocol:
 * request types: khala.add | khala.remove | khala.local | khala.sync | khala.hash
 * -- khala.add || khala.remove:
 * ---- {
 * ------ node: { // identifier of a discovery node
 * -------- nodeName, address, udpPort, tcpPort,
 * ------ },
 * ------ khalaNode: {
 * -------- service, zone, address, port,
 * ------ }
 * ---- }
 * -- khala.local
 * ---- {
 * ------ node: { // identifier of a discovery node
 * -------- nodeName, address, udpPort, tcpPort,
 * ------ },
 * ------ khalaNodes: [ {
 * -------- service, zone, address, port,
 * ------ } ]
 * ---- }
 * -- khala.local-response, same as khala.local
 * -- khala.sync
 * ----- [ khala-local format ]
 * -- khala.sync-response
 * ---- {
 * ------ diff: [
 * -------- node: { nodeName, address, udpPort, tcpPort, }
 * ------ ]
 * ---- }
 * -- khala.hash
 * ---- { hash }
 * <p>
 * triggers:
 * 1. when a node is discovered, record the node, and send khala-local message to the discovered node
 * 2. when a node is removed, remvoe the node related kNodes
 * 3. when a kNode is added, record the kNode, and send a khala-add message:
 * -- 1) if kNode.type is nexus, should alert all nodes in the khala
 * -- 2) if kNode.type is pylon, should alert only nexus nodes
 * 4. when a kNode is removed, remove the kNode, and send a khala-remove message:
 * -- 1) if kNode.type is nexus, should alert all nodes in the khala
 * -- 2) if kNode.type is pylon, should alert only nexus nodes
 * 5. for every few minutes, the node should randomly pick a nexus node and send a khala message
 * server:
 * 1. when receiving a khala.add message,
 * -- the node should record the node, then respond with 204
 * 2. when receiving a khala.remove message,
 * -- the node should remove the node, then respond with 204
 * 3. when receiving a khala.local message,
 * -- the node should change the local cache to the message content, and respond with 204
 * 4. when receiving a khala.sync message,
 * -- the node checks each discovery node in the message and compare with local cache
 * -- when a diff is found, the node should request the differed nodes with khala.local message and store the latest config
 * -- finally respond with a khala.sync-response message, containing missing nodes differed from of the request
 * client:
 * -- when receiving khala.sync-response message, sync with diff nodes
 */
public class Khala {
    enum NodeState {
        init,
        stable,
        deleted,
    }

    static class NodeWrap {
        public final Node node;
        public NodeState state = NodeState.init;
        public NodeState targetState = NodeState.stable;

        NodeWrap(Node node) {
            this.node = node;
        }
    }

    class NodeMap {
        private final Map<Node, NodeWrap> nodes = new ConcurrentHashMap<>();

        /**
         * adding the node into `nodes`
         * Should only call this method with remote nodes
         * Local node(s) should directly register via the private fields `nodes`
         * <p>
         * When added, the node will be checked for a list of KhalaNodes,
         * those khala nodes will be added to KhalaNodes collection.
         *
         * @param node the remote node to register
         * @throws IllegalStateException throw when the node already exists
         */
        public void add(Node node) {
            NodeWrap w = nodes.put(node, new NodeWrap(node));
            if (w != null) {
                // the node should not be recorded for multiple times
                // we cannot handle the situation, it must be a bug
                Logger.shouldNotHappen("node " + node + " already exists");
                throw new IllegalStateException("node " + node + " already exists");
            }
            // should init the node

            // here, we sleep for a few millis
            // the two discovery nodes might find each other at the same time
            // and remote discovery node health check might not turned to up yet
            // wait for a few millis can solve this problem
            discovery.loop.getSelectorEventLoop().delay(100, () -> checkRemote(node, new Callback<>() {
                @Override
                protected void onSucceeded(Void value) {
                    NodeWrap wrap = nodes.get(node);
                    if (wrap == null || wrap.targetState == NodeState.deleted) {
                        // the node state is `deleted` or not exists, so remove the node
                        removeNode(node);
                        return;
                    }
                    wrap.state = NodeState.stable;
                }

                @Override
                protected void onFailed(Throwable err) {
                    // ignore err when failed
                    // we remove the node
                    removeNode(node);
                }
            }));
        }

        /**
         * removing the node from `nodes`
         * Should only call this method with remote nodes
         * Local node(s) should not be removed.
         * <p>
         * When removed, the corresponding recordings in `khalaNodes` will be removed as well.
         *
         * @param node the remote node to remove
         */
        private void removeNode(Node node) {
            nodes.remove(node);
            khalaNodes.remove(node);
        }

        public boolean containsKey(Node node) {
            return nodes.containsKey(node);
        }

        public NodeWrap get(Node node) {
            return nodes.get(node);
        }

        public Set<Node> getNodes() {
            return nodes.keySet();
        }
    }

    class KhalaNodeRecorder {
        private final Map<Node, Set<KhalaNode>> n2knsMap = new ConcurrentHashMap<>();

        /**
         * specify a remote discovery node and init khala nodes with a list.
         * <p>
         * Only call this method with remote nodes.
         * Local nodes should be handled directly via private field.
         *
         * @param node the remote node to add
         * @throws IllegalStateException throw if already exists
         */
        public void add(Node node) {
            Set<KhalaNode> set = n2knsMap.put(node, new HashSet<>());
            if (set != null) {
                // the khala node set should not be recorded multiple times
                // we cannot handle the situation, it must be a bug
                Logger.shouldNotHappen("khala nodes in " + node + " already exists");
                throw new IllegalStateException("khala nodes in " + node + " already exists");
            }
        }

        /**
         * add a khala node into the network
         *
         * @param kn the node to add
         */
        public void addLocal(KhalaNode kn) {
            if (!n2knsMap.containsKey(discovery.localNode)) {
                n2knsMap.put(discovery.localNode, new HashSet<>());
            }
            if (n2knsMap.get(discovery.localNode).add(kn)) {
                // successfully added
                // then should notify others about the added node
                notifyNetworkAddKhalaNode(kn);
                listenerNodeAdd(discovery.localNode, kn);
            }
        }

        /**
         * record the remote khala node.
         * <p>
         * Only call this method with remote nodes.
         * Local nodes should be handled with addLocal() method.
         *
         * @param node      discovery node
         * @param khalaNode khala node
         */
        public void add(Node node, KhalaNode khalaNode) {
            Set<KhalaNode> set = n2knsMap.get(node);
            if (set == null) { // init with a set if not exists
                Logger.alert("khala nodes in " + node + " should exist");
                set = new HashSet<>();
                n2knsMap.put(node, set);
            }
            if (!set.add(khalaNode)) {
                return; // already recorded
            }
            listenerNodeAdd(node, khalaNode);
        }

        /**
         * remove the remote khala node.
         * <p>
         * Only call this method with remote nodes.
         * Local nodes should be handled with removeLocal method.
         *
         * @param node      discovery node
         * @param khalaNode khala node
         */
        public void remove(Node node, KhalaNode khalaNode) {
            Set<KhalaNode> set = n2knsMap.get(node);
            if (set == null) { // init with a set if not exists
                Logger.alert("khala nodes in " + node + " should exist");
                set = new HashSet<>();
                n2knsMap.put(node, set);
            }
            if (!set.remove(khalaNode)) {
                // already removed, do nothing
                return;
            }
            listenerNodeRemove(node, khalaNode);
        }

        /**
         * remove a khala node from network
         *
         * @param kn the node to remove
         */
        public void removeLocal(KhalaNode kn) {
            if (!n2knsMap.containsKey(discovery.localNode)) {
                n2knsMap.put(discovery.localNode, new HashSet<>());
            }
            if (n2knsMap.get(discovery.localNode).remove(kn)) {
                // successfully removed
                // then should notify others about the removal
                notifyNetworkRemoveKhalaNode(kn);
                listenerNodeRemove(discovery.localNode, kn);
            }
        }

        /**
         * remove all related resources of a remote discovery node.
         * <p>
         * Only call this method with remote nodes.
         * Local discovery node(s) should not be removed.
         *
         * @param node the remote node to remove
         * @throws IllegalStateException throw if already exists
         */
        public void remove(Node node) {
            Set<KhalaNode> set = n2knsMap.remove(node);
            if (set == null) {
                // the node not recorded, ignore
                return;
            }
            for (KhalaNode kn : set) {
                listenerNodeRemove(node, kn);
            }
        }

        public Set<KhalaNode> getKhalaNodes(Node node) {
            if (!n2knsMap.containsKey(node)) {
                Logger.alert("khala node list of node " + node + " not exists");
                n2knsMap.put(node, new HashSet<>());
            }
            return n2knsMap.get(node);
        }

        // alert listeners for node adding
        private void listenerNodeAdd(Node n, KhalaNode node) {
            Logger.info(LogType.KHALA_EVENT, "node added " + node);
            for (KhalaNodeListener lsn : khalaNodeListeners) {
                lsn.add(n, node);
            }
        }

        // alert listeners for node removing
        private void listenerNodeRemove(Node n, KhalaNode node) {
            Logger.warn(LogType.KHALA_EVENT, "node removed " + node);
            for (KhalaNodeListener lsn : khalaNodeListeners) {
                lsn.remove(n, node);
            }
        }

        // make a diff between remote and local
        // the result is a tuple:
        // < in remote not in local (toAdd), in local not in remote (toRemove)>
        public Tuple<Set<KhalaNode>, Set<KhalaNode>> runDiff(Node node, List<KhalaNode> remote) {
            Set<KhalaNode> local = n2knsMap.get(node);
            if (local == null) {
                Logger.alert("khala nodes in " + node + " not exist");
                local = new HashSet<>();
                n2knsMap.put(node, local);
            }

            Set<KhalaNode> toAdd = new HashSet<>();
            Set<KhalaNode> toRemove = new HashSet<>();
            for (KhalaNode n : remote) {
                if (!local.contains(n))
                    toAdd.add(n);
            }
            for (KhalaNode n : local) {
                if (!remote.contains(n))
                    toRemove.add(n);
            }
            return new Tuple<>(toAdd, toRemove);
        }
    }

    public final Discovery discovery;
    public final KhalaConfig config;
    private final NodeMap nodes = new NodeMap();
    private final KhalaNodeRecorder khalaNodes = new KhalaNodeRecorder();
    private final Set<KhalaNodeListener> khalaNodeListeners = new CopyOnWriteArraySet<>();
    private final Random rand = new Random();

    public Khala(Discovery discovery, KhalaConfig config) {
        this.discovery = discovery;
        this.config = config;

        // init local node
        NodeWrap localNodeWrap = new NodeWrap(discovery.localNode);
        localNodeWrap.state = NodeState.stable;
        // use private field to record the node
        // because they are local nodes and should not be handled as the remote nodes
        nodes.nodes.put(discovery.localNode, localNodeWrap);
        khalaNodes.n2knsMap.put(discovery.localNode, new HashSet<>());

        // init periodic event
        discovery.loop.getSelectorEventLoop().period(config.syncPeriod, this::doSync);

        // add server handler
        discovery.addExternalHandler(new NodeDataHandler() {
            @Override
            public boolean canHandle(String type) {
                return type.equals("khala.sync")
                    || type.equals("khala.add")
                    || type.equals("khala.remove")
                    || type.equals("khala.local")
                    || type.equals("khala.hash");
            }

            @SuppressWarnings("OptionalGetWithoutIsPresent")
            @Override
            public void handle(int version, String type, JSON.Instance data, Callback<JSON.Instance, Throwable> cb) {
                if (type.equals("khala.hash")) {
                    // khala.hash format is different from others
                    // so handle it first

                    // do not check, simply respond with local hash
                    cb.succeeded(new ObjectBuilder().put("hash", calcHash()).build());
                    return;
                }

                Tuple<KhalaMsg, XException> tup = utilValidateResponse(version, type, data);
                if (tup.right != null) {
                    cb.failed(tup.right);
                    return;
                }
                KhalaMsg msg = tup.left;
                Node n;
                List<KhalaNode> nodes;
                switch (msg.type) {
                    case "khala.sync":
                        cb.succeeded(handleFullKhala(msg.nodes));
                        break;
                    case "khala.add":
                        n = msg.nodes.keySet().stream().findFirst().get();
                        nodes = msg.nodes.get(n);
                        handleAdd(n, nodes.get(0));
                        cb.succeeded(null);
                        break;
                    case "khala.remove":
                        n = msg.nodes.keySet().stream().findFirst().get();
                        nodes = msg.nodes.get(n);
                        handleRemove(n, nodes.get(0));
                        cb.succeeded(null);
                        break;
                    case "khala.local":
                        n = msg.nodes.keySet().stream().findFirst().get();
                        nodes = msg.nodes.get(n);
                        handleLocal(n, nodes);
                        cb.succeeded(buildLocalKhalaMsg());
                        break;
                    default:
                        Logger.shouldNotHappen("unknown type " + msg.type);
                        cb.failed(new XException("unknown type"));
                }
            }
        });
        // add node listener
        discovery.addNodeListener(new NodeListener() {
            @Override
            public void up(Node node) {
                // fire when the remote node is UP
                // the node should be recorded
                nodes.add(node);
            }

            @Override
            public void down(Node node) {
                // fire when the remote node is DOWN
                // the node should be removed
                nodes.removeNode(node);
            }

            @Override
            public void leave(Node node) {
                down(node); // handled the same as down()
            }
        });
    }

    private boolean utilLogResponseErr(Node n, String type, Throwable err, HttpResponse resp) {
        if (err != null) {
            Logger.error(LogType.CONN_ERROR, "failed to send " + type + " to " + n, err);
            return true;
        }
        final int expectedRespStatus = ((type.equals("khala.add") || type.equals("khala.remove")) ? 204 : 200);
        if (resp.status() != expectedRespStatus) {
            Logger.error(LogType.INVALID_EXTERNAL_DATA, "response of " + type + " from " + n + " is invalid");
            return true;
        }
        return false;
    }

    private Tuple<KhalaMsg, XException> utilValidateResponse(int version, String type, JSON.Instance data) {
        KhalaMsg msg;
        try {
            msg = KhalaMsg.parse(version, type, data);
        } catch (XException e) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, e.getMessage());
            return new Tuple<>(null, e);
        }
        if (msg.version != 1) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "version mismatch: " + msg);
            return new Tuple<>(null, new XException("version mismatch"));
        }
        return new Tuple<>(msg, null);
    }

    private boolean discoveryNodeNotExist(Node n) {
        if (!nodes.containsKey(n)) {
            Logger.error(LogType.KHALA_EVENT, "the discovery node not recorded: " + n);
            return true;
        }
        return false;
    }

    // ---------------------
    // START khala event handlings
    // ---------------------

    // handle khala-add event
    private void handleAdd(Node n, KhalaNode node) {
        if (discoveryNodeNotExist(n))
            return;
        khalaNodes.add(n, node);
    }

    // handle khala-remove event
    private void handleRemove(Node n, KhalaNode node) {
        if (discoveryNodeNotExist(n))
            return;
        khalaNodes.remove(n, node);
    }

    // handle khala-local response
    private void handleLocal(Node n, List<KhalaNode> remote) {
        if (discoveryNodeNotExist(n))
            return;

        Tuple<Set<KhalaNode>, Set<KhalaNode>> wantAddRemove = khalaNodes.runDiff(n, remote);
        // because it's directly retrieved from remote
        // so the result data is trusted without another check
        // add missing nodes and remove redundant nodes
        for (KhalaNode toAdd : wantAddRemove.left) {
            Logger.info(LogType.KHALA_EVENT, "node added via a check " + toAdd);
            khalaNodes.add(n, toAdd);
        }
        for (KhalaNode toRemove : wantAddRemove.right) {
            Logger.info(LogType.KHALA_EVENT, "node removed via a check " + toRemove);
            khalaNodes.remove(n, toRemove);
        }
    }

    // handle khala response
    private JSON.Instance handleFullKhala(Map<Node, List<KhalaNode>> remoteNodeMap) {
        // no need to check for remote if it's a new node
        boolean needToCheckRemote = remoteNodeMap.size() > 1;
        Set<Node> diff = new HashSet<>();
        for (Node n : remoteNodeMap.keySet()) {
            List<KhalaNode> remote = remoteNodeMap.get(n);
            if (discoveryNodeNotExist(n)) {
                diff.add(n);
                continue;
            }

            Tuple<Set<KhalaNode>, Set<KhalaNode>> wantAddRemove = khalaNodes.runDiff(n, remote);
            if (wantAddRemove.left.isEmpty() && wantAddRemove.right.isEmpty()) {
                // the two collections are the same
                continue;
            }
            Logger.warn(LogType.KHALA_EVENT, "khala data mismatch on node " + n);
            // not same, so we make a direct check on the remote discovery node
            if (needToCheckRemote) {
                checkRemote(n);
            }
            diff.add(n);
        }
        for (Node n : nodes.getNodes()) {
            if (remoteNodeMap.containsKey(n)) {
                continue; // already checked
            }
            // missing in remote
            diff.add(n);
        }
        return new ObjectBuilder()
            .putInst("diff", new SimpleArray(
                diff.stream().map(n -> new ObjectBuilder()
                    .put("nodeName", n.nodeName)
                    .put("address", n.address)
                    .put("udpPort", n.udpPort)
                    .put("tcpPort", n.tcpPort)
                    .build()).collect(Collectors.toList())
            ))
            .build();
    }

    // ---------------------
    // END khala event handlings
    // ---------------------

    // ---------------------
    // START khala requests
    // ---------------------

    // make a khala-local request and handle the response
    private void checkRemote(Node n) {
        checkRemote(n, new Callback<>() {
            @Override
            protected void onSucceeded(Void value) {
                // ignore
            }

            @Override
            protected void onFailed(Throwable err) {
                // ignore
            }
        });
    }

    // send a `khala-local` request and handle the response
    private void checkRemote(Node n, Callback<Void, Throwable> cb) {
        if (discoveryNodeNotExist(n)) {
            cb.failed(new XException("discovery node not exist"));
            return;
        }

        JSON.Instance msg = buildLocalKhalaMsg();
        HttpClient client = new Http1ClientImpl(new InetSocketAddress(n.inetAddress, n.tcpPort), discovery.loop, 3000);
        client.put("/discovery/api/v1/exchange/khala.local").send(msg, (err, resp) -> {
            if (utilLogResponseErr(n, "khala.local", err, resp)) {
                return;
            }
            var tup = utilValidateResponse(1, "khala.local", resp.bodyAsJson());
            if (tup.right != null) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "response of khala.local from " + n + " is invalid: " + resp);
                cb.failed(tup.right);
                return;
            }
            var respMsg = tup.left;
            if (!respMsg.nodes.containsKey(n)) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "response of khala-local from " + n + " is invalid, invalid nodes, do not contain the node " + n + ": " + resp);
                cb.failed(new XException("invalid external data"));
                return;
            }
            handleLocal(n, respMsg.nodes.get(n));
            cb.succeeded(null);
        });
    }

    // ---------------------
    // END khala requests
    // ---------------------

    // ---------------------
    // START khala msg builders
    // ---------------------

    private JSON.Object buildLocalKhalaMsg() {
        Node n = discovery.localNode;
        return buildStandardKhalaMsg(n);
    }

    private JSON.Array buildFullKhalaMsg() {
        ArrayBuilder arr = new ArrayBuilder();
        for (Node n : nodes.getNodes()) {
            arr.addInst(buildStandardKhalaMsg(n));
        }
        return arr.build();
    }

    private JSON.Object buildStandardKhalaMsg(Node n) {
        return new ObjectBuilder()
            .putObject("node", o -> o
                .put("nodeName", n.nodeName)
                .put("address", n.address)
                .put("udpPort", n.udpPort)
                .put("tcpPort", n.tcpPort)
            )
            .putArray("khalaNodes", arr -> khalaNodes.getKhalaNodes(n).forEach(kn -> arr.addObject(o -> o
                .put("service", kn.service)
                .put("zone", kn.zone)
                .put("address", kn.address)
                .put("port", kn.port)
            )))
            .build();
    }

    // ---------------------
    // END khala msg builders
    // ---------------------

    // ---------------------
    // START khala notification
    // ---------------------

    private String calcHash() {
        PriorityQueue<Node> p = new PriorityQueue<>(Node::compareTo);
        p.addAll(nodes.getNodes());
        StringBuilder sb = new StringBuilder();
        Node n;
        while ((n = p.poll()) != null) {
            sb.append(n.nodeName).append(",")
                .append(n.address).append(",")
                .append(n.udpPort).append(",")
                .append(n.tcpPort).append(",");
            PriorityQueue<KhalaNode> pp = new PriorityQueue<>(KhalaNode::compareTo);
            pp.addAll(khalaNodes.getKhalaNodes(n));
            KhalaNode kn;
            while ((kn = pp.poll()) != null) {
                sb.append(kn.service).append(",")
                    .append(kn.zone).append(",")
                    .append(kn.address).append(",")
                    .append(kn.port).append(",");
            }
        }
        byte[] data = sb.toString().getBytes();
        return Base64.getEncoder().encodeToString(data);
    }

    private void notifyNetworkKhalaHash(Node node) {
        String hash = calcHash();
        JSON.Object reqBody = new ObjectBuilder().put("hash", hash).build();
        HttpClient client = new Http1ClientImpl(new InetSocketAddress(node.inetAddress, node.tcpPort), discovery.loop, 3000);
        client.put("/discovery/api/v1/exchange/khala.hash").send(reqBody, (err, resp) -> {
            if (utilLogResponseErr(node, "khala.hash", err, resp)) {
                return;
            }
            JSON.Instance inst = resp.bodyAsJson();
            if (!(inst instanceof JSON.Object)) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "khala.hash response is not JSON.Object: " + inst);
                return;
            }
            JSON.Object body = (JSON.Object) inst;
            if (!body.containsKey("hash")) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "khala.sync response should contain key `hash`: " + body);
                return;
            }
            if (!(body.get("hash") instanceof JSON.String)) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "value of khala.sync response key `hash` should be string: " + body);
                return;
            }
            String remoteHash = body.getString("hash");
            if (remoteHash.equals(hash)) {
                assert Logger.lowLevelDebug("remote hash and local hash are the same, no need to sync");
                return;
            }
            // do sync
            notifyNetworkFullKhala(node);
        });
    }

    private void notifyNetworkFullKhala(Node node) {
        JSON.Array reqBody = buildFullKhalaMsg();
        HttpClient client = new Http1ClientImpl(new InetSocketAddress(node.inetAddress, node.tcpPort), discovery.loop, 3000);
        client.put("/discovery/api/v1/exchange/khala.sync").send(reqBody, (err, resp) -> {
            if (utilLogResponseErr(node, "khala.sync", err, resp)) {
                return;
            }
            JSON.Instance inst = resp.bodyAsJson();
            if (!(inst instanceof JSON.Object)) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "khala.sync response is not JSON.Object: " + inst);
                return;
            }
            JSON.Object body = (JSON.Object) inst;
            if (!body.containsKey("diff")) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "khala.sync response should contain key `diff`: " + body);
                return;
            }
            if (!(body.get("diff") instanceof JSON.Array)) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "value of khala.sync response key `diff` should be array: " + body);
                return;
            }
            JSON.Array diff = body.getArray("diff");
            for (int i = 0; i < diff.length(); ++i) {
                JSON.Instance ins = diff.get(i);
                if (!(ins instanceof JSON.Object)) {
                    Logger.error(LogType.INVALID_EXTERNAL_DATA, "khala.sync response `diff[" + i + "]` should be object: " + body);
                    return;
                }
                JSON.Object n = (JSON.Object) ins;
                if (!n.containsKey("nodeName")
                    || !n.containsKey("address")
                    || !n.containsKey("udpPort")
                    || !n.containsKey("tcpPort")) {
                    Logger.error(LogType.INVALID_EXTERNAL_DATA, "khala.sync response `diff[" + i + "]` missing keys: " + body);
                    return;
                }
                if (!(n.get("nodeName") instanceof JSON.String)
                    || !(n.get("address") instanceof JSON.String)
                    || !(n.get("udpPort") instanceof JSON.Integer)
                    || !(n.get("tcpPort") instanceof JSON.Integer)) {
                    Logger.error(LogType.INVALID_EXTERNAL_DATA, "wrong value type for khala.sync response `diff[" + i + "]`: " + body);
                    return;
                }
                Node extNode;
                try {
                    extNode = new Node(n.getString("nodeName"), n.getString("address"), n.getInt("udpPort"), n.getInt("tcpPort"));
                } catch (UnknownHostException e) {
                    Logger.error(LogType.INVALID_EXTERNAL_DATA, "khala.sync response `diff[" + i + "]` address is invalid: " + n.getString("address"));
                    return;
                }
                checkRemote(extNode);
            }
        });
    }

    private void notifyNetworkAddKhalaNode(KhalaNode node) {
        for (Node n : khalaNodes.n2knsMap.keySet()) {
            if (n.equals(discovery.localNode))
                continue; // don't send to local node
            notifyNetwork("khala.add", n, node);
        }
    }

    private void notifyNetworkRemoveKhalaNode(KhalaNode node) {
        for (Node n : khalaNodes.n2knsMap.keySet()) {
            if (n.equals(discovery.localNode))
                continue; // don't send to local node
            notifyNetwork("khala.remove", n, node);
        }
    }

    private void notifyNetwork(String type, Node remoteNode, KhalaNode kn) {
        Node n = discovery.localNode;
        JSON.Object reqBody = new ObjectBuilder()
            .putObject("node", o -> o
                .put("nodeName", n.nodeName)
                .put("address", n.address)
                .put("udpPort", n.udpPort)
                .put("tcpPort", n.tcpPort)
            )
            .putObject("khalaNode", o -> o
                .put("service", kn.service)
                .put("zone", kn.zone)
                .put("address", kn.address)
                .put("port", kn.port)
            )
            .build();
        HttpClient client = new Http1ClientImpl(new InetSocketAddress(remoteNode.inetAddress, remoteNode.tcpPort), discovery.loop, 3000);
        client.put("/discovery/api/v1/exchange/" + type).send(reqBody, (err, resp) ->
            utilLogResponseErr(remoteNode, type, err, resp)
        );
    }

    // ---------------------
    // END khala notification
    // ---------------------

    // ---------------------
    // START local op
    // ---------------------

    public void addLocal(KhalaNode khalaNode) {
        discovery.loop.getSelectorEventLoop().runOnLoop(() -> this.khalaNodes.addLocal(khalaNode));
    }

    public void removeLocal(KhalaNode khalaNode) {
        discovery.loop.getSelectorEventLoop().runOnLoop(() -> this.khalaNodes.removeLocal(khalaNode));
    }

    public Map<Node, Set<KhalaNode>> getNodeToKhalaNodesMap() {
        return new HashMap<>(khalaNodes.n2knsMap);
    }

    public void addKhalaNodeListener(KhalaNodeListener lsn) {
        // the listener might be alerted when adding
        // so run the process on discovery event loop
        discovery.loop.getSelectorEventLoop().runOnLoop(() -> {
            // alert add() event for all nodes
            for (Node n : khalaNodes.n2knsMap.keySet()) {
                for (KhalaNode kn : khalaNodes.getKhalaNodes(n)) {
                    lsn.add(n, kn);
                }
            }
            khalaNodeListeners.add(lsn);
        });
    }

    public void removeKhalaNodeListener(KhalaNodeListener lsn) {
        khalaNodeListeners.remove(lsn);
    }

    // ---------------------
    // END local op
    // ---------------------

    // randomly pick a nexus node and sync with that node
    private void doSync() {
        List<Node> ns = nodes.nodes.keySet()
            .stream()
            .filter(n ->
                !n.equals(discovery.localNode) && // not local node
                    khalaNodes.n2knsMap.containsKey(n))
            .collect(Collectors.toList());
        if (ns.isEmpty())
            return;
        Node n = ns.get(rand.nextInt(ns.size()));
        notifyNetworkKhalaHash(n);
    }

    public void sync() {
        discovery.loop.getSelectorEventLoop().runOnLoop(this::doSync);
    }
}
