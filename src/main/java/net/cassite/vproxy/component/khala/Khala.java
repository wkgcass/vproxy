package net.cassite.vproxy.component.khala;

import net.cassite.vproxy.component.exception.XException;
import net.cassite.vproxy.component.khala.protocol.KhalaMsg;
import net.cassite.vproxy.discovery.Discovery;
import net.cassite.vproxy.discovery.Node;
import net.cassite.vproxy.discovery.NodeDataHandler;
import net.cassite.vproxy.discovery.NodeListener;
import net.cassite.vproxy.redis.application.RESPApplicationContext;
import net.cassite.vproxy.redis.application.RESPClientUtils;
import net.cassite.vproxy.util.Callback;
import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.Tuple;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

/**
 * the service network
 * protocol:
 * data type:
 * [
 * --version
 * --type
 * --list:
 * --[
 * ----[
 * ------nodeName
 * ------address
 * ------udpPort
 * ------tcpPort
 * ------list:
 * ------[
 * --------[
 * ----------khalaNodeType
 * ----------service
 * ----------zone
 * ----------address
 * ----------port
 * --------]
 * ------]
 * ----]
 * --]
 * ]
 * type: khala-add | khala-remove | khala-local | khala
 * -- khala-add and khala-remove msg.list.size should -eq 1, msg.list[0].list.size should -eq 1
 * -- khala-local msg.list.size should -eq 1
 * -- khala message should contain all local cached nodes
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
 * 1. when receiving a khala-add message,
 * -- the node should record the node, then respond with a khala message
 * 2. when receiving a khala-remove message,
 * -- the node should remove the node, then respond with a khala message
 * 3. when receiving a khala-local message,
 * -- the node should change the local cache to the message content, and respond with a khala-local message
 * 4. when receiving a khala message,
 * -- first directly respond with a khala message
 * -- if the message doesn't contain any discovery node or have only one discovery node, then do not run a diff.
 * -- otherwise: the node checks each discovery node in the message and compare with local cache
 * -- when a diff is found, the node should request the differed nodes with khala-local message and store the latest config
 * client:
 * same handling with server, but does not respond
 */
public class Khala {
    enum NodeState {
        init,
        stable,
        deleted,
    }

    class NodeWrap {
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
            checkRemote(node, new Callback<Void, Throwable>() {
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
            });
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
        private final Map<Node, Set<KhalaNode>> khalaNodes = new ConcurrentHashMap<>();

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
            Set<KhalaNode> set = khalaNodes.put(node, new HashSet<>());
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
            if (!khalaNodes.containsKey(discovery.localNode)) {
                khalaNodes.put(discovery.localNode, new HashSet<>());
            }
            if (khalaNodes.get(discovery.localNode).add(kn)) {
                // successfully added
                // then should notify others about the added node
                notifyNetworkAddKhalaNode(kn);
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
            Set<KhalaNode> set = khalaNodes.get(node);
            if (set == null) { // init with a set if not exists
                Logger.alert("khala nodes in " + node + " should exist");
                set = new HashSet<>();
                khalaNodes.put(node, set);
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
            Set<KhalaNode> set = khalaNodes.get(node);
            if (set == null) { // init with a set if not exists
                Logger.alert("khala nodes in " + node + " should exist");
                set = new HashSet<>();
                khalaNodes.put(node, set);
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
            if (!khalaNodes.containsKey(discovery.localNode)) {
                khalaNodes.put(discovery.localNode, new HashSet<>());
            }
            if (khalaNodes.get(discovery.localNode).remove(kn)) {
                // successfully removed
                // then should notify others about the removal
                notifyNetworkRemoveKhalaNode(kn);
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
            Set<KhalaNode> set = khalaNodes.remove(node);
            if (set == null) {
                // the node not recorded, ignore
                return;
            }
            for (KhalaNode kn : set) {
                listenerNodeRemove(node, kn);
            }
        }

        public Set<KhalaNode> getKhalaNodes(Node node) {
            if (!khalaNodes.containsKey(node)) {
                Logger.alert("khala node list of node " + node + " not exists");
                khalaNodes.put(node, new HashSet<>());
            }
            return khalaNodes.get(node);
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
            Set<KhalaNode> local = khalaNodes.get(node);
            if (local == null) {
                Logger.alert("khala nodes in " + node + " not exist");
                local = new HashSet<>();
                khalaNodes.put(node, local);
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
        khalaNodes.khalaNodes.put(discovery.localNode, new HashSet<>());

        // init periodic event
        discovery.loop.getSelectorEventLoop().period(config.syncPeriod, this::doSync);

        // add server handler
        discovery.addExternalHandler(new NodeDataHandler() {
            @Override
            public boolean canHandle(String type) {
                return type.equals("khala") || type.equals("khala-add") || type.equals("khala-remove") || type.equals("khala-local");
            }

            @SuppressWarnings("OptionalGetWithoutIsPresent")
            @Override
            public void handle(Object o, RESPApplicationContext respApplicationContext, Callback<Object, Throwable> cb) {
                Tuple<KhalaMsg, XException> tup = utilValidateResponse(o, "khala", "khala-add", "khala-remove", "khala-local");
                if (tup.right != null) {
                    cb.failed(tup.right);
                    return;
                }
                KhalaMsg msg = tup.left;
                Node n;
                List<KhalaNode> nodes;
                switch (msg.type) {
                    case "khala":
                        cb.succeeded(buildFullKhalaMsg());
                        handleFullKhala(msg.nodes);
                        break;
                    case "khala-add":
                        if (msg.nodes.size() != 1 || msg.nodes.get(msg.nodes.keySet().stream().findFirst().get()).size() != 1) {
                            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "khala-add node list size is wrong: " + msg);
                            cb.failed(new XException("node list size is wrong"));
                            break;
                        }
                        n = msg.nodes.keySet().stream().findFirst().get();
                        nodes = msg.nodes.get(n);
                        handleAdd(n, nodes.get(0));
                        cb.succeeded(buildFullKhalaMsg());
                        break;
                    case "khala-remove":
                        if (msg.nodes.size() != 1 || msg.nodes.get(msg.nodes.keySet().stream().findFirst().get()).size() != 1) {
                            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "khala-add node list size is wrong: " + msg);
                            cb.failed(new XException("node list size is wrong"));
                            break;
                        }
                        n = msg.nodes.keySet().stream().findFirst().get();
                        nodes = msg.nodes.get(n);
                        handleRemove(n, nodes.get(0));
                        cb.succeeded(buildFullKhalaMsg());
                        break;
                    case "khala-local":
                        if (msg.nodes.size() != 1) {
                            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "khala-add node list size is wrong: " + msg);
                            cb.failed(new XException("node list size is wrong"));
                            break;
                        }
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

    private Tuple<KhalaMsg, XException> utilValidateResponse(Object o, String... expectedType) {
        if (!(o instanceof List)) {
            Logger.error(LogType.KHALA_EVENT, "invalid message");
            return new Tuple<>(null, new XException("invalid message"));
        }
        List rawMsg = (List) o;
        KhalaMsg msg;
        try {
            msg = KhalaMsg.parse(rawMsg);
        } catch (XException e) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, e.getMessage());
            return new Tuple<>(null, e);
        }
        if (msg.version != 1) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "version mismatch: " + msg);
            return new Tuple<>(null, new XException("version mismatch"));
        }
        if (!Arrays.asList(expectedType).contains(msg.type)) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "unexpected type: " + msg);
            return new Tuple<>(null, new XException("unexpected type"));
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
    private void handleFullKhala(Map<Node, List<KhalaNode>> remoteNodeMap) {
        if (remoteNodeMap.isEmpty()) {
            // ignore if the remote has no node data
            return;
        }
        for (Node n : remoteNodeMap.keySet()) {
            List<KhalaNode> remote = remoteNodeMap.get(n);
            if (discoveryNodeNotExist(n))
                continue;

            Tuple<Set<KhalaNode>, Set<KhalaNode>> wantAddRemove = khalaNodes.runDiff(n, remote);
            if (wantAddRemove.left.isEmpty() && wantAddRemove.right.isEmpty()) {
                // the two collections are the same
                continue;
            }
            Logger.warn(LogType.KHALA_EVENT, "khala data mismatch on node " + n);
            // not same, so we make a direct check on the remote discovery node
            checkRemote(n);
        }
    }

    // ---------------------
    // END khala event handlings
    // ---------------------

    // ---------------------
    // START khala requests
    // ---------------------

    // make a khala-local request and handle the response
    private void checkRemote(Node n) {
        checkRemote(n, new Callback<Void, Throwable>() {
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

        Object[] msg = buildLocalKhalaMsg();
        RESPClientUtils.retry(
            discovery.loop,
            new InetSocketAddress(n.inetAddress, n.tcpPort),
            msg,
            3000,
            3,
            new Callback<Object, IOException>() {
                @Override
                protected void onSucceeded(Object value) {
                    Tuple<KhalaMsg, XException> tup = utilValidateResponse(value, "khala-local");
                    if (tup.right != null) {
                        cb.failed(tup.right);
                        return;
                    }
                    KhalaMsg resp = tup.left;
                    if (!resp.nodes.containsKey(n)) {
                        Logger.error(LogType.INVALID_EXTERNAL_DATA, "request khala-local on " + n + " failed, invalid nodes, do not contain the node " + n + ": " + resp);
                        cb.failed(new XException("invalid external data"));
                        return;
                    }
                    handleLocal(n, resp.nodes.get(n));
                    cb.succeeded(null);
                }

                @Override
                protected void onFailed(IOException err) {
                    // ignore the error, but log
                    Logger.error(LogType.KHALA_EVENT, "request khala-local on " + n + " failed");
                    cb.failed(err);
                    // if the remote node is down, it will be removed by discovery lib
                }
            });
    }

    // ---------------------
    // END khala requests
    // ---------------------

    // ---------------------
    // START khala msg builders
    // ---------------------

    private Object[] buildFullKhalaMsg() {
        return buildKhalaMsgByNodes("khala", nodes.getNodes());
    }

    private Object[] buildLocalKhalaMsg() {
        return buildKhalaMsgByNodes("khala-local", Collections.singleton(discovery.localNode));
    }

    private Object[] buildKhalaMsgByNodes(String type, Collection<Node> discoveryNodes) {
        List<List<Object>> list = new LinkedList<>();
        Object[] msg = {
            1 /*version*/,
            type,
            list
        };
        for (Node n : discoveryNodes) {
            List<Object> nodeList = new ArrayList<>(5);
            list.add(nodeList);

            List<Object> khalaNodeList = new LinkedList<>();

            nodeList.add(n.nodeName);
            nodeList.add(n.address);
            nodeList.add(n.udpPort);
            nodeList.add(n.tcpPort);
            nodeList.add(khalaNodeList);

            Set<KhalaNode> kNodes = khalaNodes.getKhalaNodes(n);
            for (KhalaNode kn : kNodes) {
                List<Object> kNode = new ArrayList<>(4);
                kNode.add(kn.type.name());
                kNode.add(kn.service);
                kNode.add(kn.zone);
                kNode.add(kn.address);
                kNode.add(kn.port);
                khalaNodeList.add(kNode);
            }
        }

        return msg;
    }

    // ---------------------
    // END khala msg builders
    // ---------------------

    // ---------------------
    // START khala notification
    // ---------------------

    private void notifyNetworkFullKhala(Node node) {
        Object[] msg = buildFullKhalaMsg();
        RESPClientUtils.retry(
            discovery.loop,
            new InetSocketAddress(node.inetAddress, node.tcpPort),
            msg,
            3000,
            3,
            new Callback<Object, IOException>() {
                @Override
                protected void onSucceeded(Object value) {
                    Tuple<KhalaMsg, XException> tup = utilValidateResponse(value, "khala");
                    if (tup.right != null) {
                        return;
                    }
                    KhalaMsg msg = tup.left;
                    handleFullKhala(msg.nodes);
                }

                @Override
                protected void onFailed(IOException err) {
                    // ignore if got error
                }
            });
    }

    private void notifyNetworkAddKhalaNode(KhalaNode node) {
        boolean notifyAll = node.type == KhalaNodeType.nexus;
        for (Node n : khalaNodes.khalaNodes.keySet()) {
            if (n.equals(discovery.localNode))
                continue; // don't send to local node
            if (notifyAll || khalaNodes.khalaNodes.get(n).stream().anyMatch(kn -> kn.type == KhalaNodeType.nexus)) {
                notifyNetwork("khala-add", n, node);
            }
        }
    }

    private void notifyNetworkRemoveKhalaNode(KhalaNode node) {
        boolean notifyAll = node.type == KhalaNodeType.nexus;
        for (Node n : khalaNodes.khalaNodes.keySet()) {
            if (n.equals(discovery.localNode))
                continue; // don't send to local node
            if (notifyAll || khalaNodes.khalaNodes.get(n).stream().anyMatch(kn -> kn.type == KhalaNodeType.nexus)) {
                notifyNetwork("khala-remove", n, node);
            }
        }
    }

    private void notifyNetwork(String type, Node remoteNode, KhalaNode node) {
        Object[] msg = {
            1 /*version*/,
            type,
            new Object[]{ // list of discovery node
                new Object[]{
                    discovery.localNode.nodeName,
                    discovery.localNode.address,
                    discovery.localNode.udpPort,
                    discovery.localNode.tcpPort,
                    new Object[]{ // list of khala-nodes
                        new Object[]{
                            node.type.name(),
                            node.service,
                            node.zone,
                            node.address,
                            node.port
                        }
                    }
                }
            }
        };
        RESPClientUtils.retry(discovery.loop,
            new InetSocketAddress(remoteNode.inetAddress, remoteNode.tcpPort),
            msg,
            3000,
            3,
            new Callback<Object, IOException>() {
                @Override
                protected void onSucceeded(Object value) {
                    Tuple<KhalaMsg, XException> tup = utilValidateResponse(value, "khala");
                    if (tup.right != null) {
                        return;
                    }
                    KhalaMsg msg = tup.left;
                    handleFullKhala(msg.nodes);
                }

                @Override
                protected void onFailed(IOException err) {
                    Logger.error(LogType.KHALA_EVENT, "notify " + type + " " + remoteNode + " failed", err);
                }
            });
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

    public Map<Node, Set<KhalaNode>> getKhalaNodes() {
        return new HashMap<>(khalaNodes.khalaNodes);
    }

    public void addKhalaNodeListener(KhalaNodeListener lsn) {
        // the listener might be alerted when adding
        // so run the process on discovery event loop
        discovery.loop.getSelectorEventLoop().runOnLoop(() -> {
            // alert add() event for all nodes
            for (Node n : khalaNodes.khalaNodes.keySet()) {
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
                    khalaNodes.khalaNodes.containsKey(n) && // valid
                    khalaNodes.khalaNodes.get(n).stream().anyMatch(kn -> kn.type == KhalaNodeType.nexus) /*only check nexus*/)
            .collect(Collectors.toList());
        if (ns.isEmpty())
            return;
        Node n = ns.get(rand.nextInt(ns.size()));
        notifyNetworkFullKhala(n);
    }

    public void sync() {
        discovery.loop.getSelectorEventLoop().runOnLoop(this::doSync);
    }
}
