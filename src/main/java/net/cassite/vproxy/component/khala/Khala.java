package net.cassite.vproxy.component.khala;

import net.cassite.vproxy.component.exception.XException;
import net.cassite.vproxy.component.khala.protocol.KhalaMsg;
import net.cassite.vproxy.discovery.Discovery;
import net.cassite.vproxy.discovery.Node;
import net.cassite.vproxy.discovery.NodeDataHandler;
import net.cassite.vproxy.discovery.NodeListener;
import net.cassite.vproxy.redis.application.RESPApplicationContext;
import net.cassite.vproxy.redis.application.RESPClientUtils;
import net.cassite.vproxy.util.*;

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

    public final Discovery discovery;
    public final KhalaConfig config;
    private final Map<Node, NodeWrap> nodes = new ConcurrentHashMap<>();
    private final Map<Node, Set<KhalaNode>> khalaNodes = new ConcurrentHashMap<>();
    private final Set<KhalaNode> localKhalaNodes = new ConcurrentHashSet<>();
    private final Set<KhalaNodeListener> khalaNodeListeners = new CopyOnWriteArraySet<>();
    private final Random rand = new Random();

    public Khala(Discovery discovery, KhalaConfig config) {
        this.discovery = discovery;
        this.config = config;

        // init local node
        NodeWrap localNodeWrap = new NodeWrap(discovery.localNode);
        localNodeWrap.state = NodeState.stable;
        nodes.put(discovery.localNode, localNodeWrap);
        khalaNodes.put(discovery.localNode, new HashSet<>());

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
                Tuple<KhalaMsg, XException> tup = validateResponse(o, "khala", "khala-add", "khala-remove", "khala-local");
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
                discovery.loop.getSelectorEventLoop().runOnLoop(() -> {
                    NodeWrap wrap = nodes.get(node);
                    if (wrap == null) {
                        wrap = new NodeWrap(node);
                        nodes.put(node, wrap);
                        if (!khalaNodes.containsKey(node))
                            khalaNodes.put(node, new HashSet<>());
                        initNode(node);
                        return;
                    }
                    wrap.targetState = NodeState.stable;
                });
            }

            @Override
            public void down(Node node) {
                discovery.loop.getSelectorEventLoop().runOnLoop(() -> {
                    NodeWrap wrap = nodes.get(node);
                    if (wrap == null)
                        return; // not exist
                    wrap.targetState = NodeState.deleted;
                    if (wrap.state == NodeState.stable) {
                        removeNode(node);
                    }
                });
            }

            @Override
            public void leave(Node node) {
                down(node);
            }
        });
    }

    private void alertNodeAdd(Node n, KhalaNode node) {
        for (KhalaNodeListener lsn : khalaNodeListeners) {
            lsn.add(n, node);
        }
    }

    private void alertNodeRemove(Node n, KhalaNode node) {
        for (KhalaNodeListener lsn : khalaNodeListeners) {
            lsn.remove(n, node);
        }
    }

    private Tuple<KhalaMsg, XException> validateResponse(Object o, String... expectedType) {
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

    // handle khala-add event
    private void handleAdd(Node n, KhalaNode node) {
        if (discoveryNodeNotExist(n))
            return;

        Set<KhalaNode> nodes = khalaNodes.get(n);
        if (nodes.contains(node))
            return; // already exist, ignore the event
        Logger.info(LogType.KHALA_EVENT, "node added " + node);
        nodes.add(node); // record it
        alertNodeAdd(n, node);
    }

    // handle khala-remove event
    private void handleRemove(Node n, KhalaNode node) {
        if (discoveryNodeNotExist(n))
            return;

        Set<KhalaNode> nodes = khalaNodes.get(n);
        if (!nodes.contains(node))
            return;
        Logger.warn(LogType.KHALA_EVENT, "node removed " + node);
        nodes.remove(node);
        alertNodeRemove(n, node);
    }

    // handle khala-local response
    private void handleLocal(Node n, List<KhalaNode> remote) {
        if (discoveryNodeNotExist(n))
            return;

        Set<KhalaNode> local = khalaNodes.get(n);
        Tuple<Set<KhalaNode>, Set<KhalaNode>> wantAddRemove = runDiff(remote, local);
        // because it's directly retrieved from remote
        // so the result data is trusted without another check
        // add missing nodes and remove redundant nodes
        for (KhalaNode toAdd : wantAddRemove.left) {
            Logger.info(LogType.KHALA_EVENT, "node added via a check " + toAdd);
            local.add(toAdd);
            alertNodeAdd(n, toAdd);
        }
        for (KhalaNode toRemove : wantAddRemove.right) {
            Logger.info(LogType.KHALA_EVENT, "node removed via a check " + toRemove);
            local.remove(toRemove);
            alertNodeRemove(n, toRemove);
        }
    }

    // handle khala response
    private void handleFullKhala(Map<Node, List<KhalaNode>> remoteNodeMap) {
        if (remoteNodeMap.size() <= 1) {
            // ignore if the remote has no node data or only contain a self node
            return;
        }
        for (Node n : remoteNodeMap.keySet()) {
            List<KhalaNode> remote = remoteNodeMap.get(n);
            if (discoveryNodeNotExist(n))
                continue;

            Set<KhalaNode> local = khalaNodes.get(n);
            Tuple<Set<KhalaNode>, Set<KhalaNode>> wantAddRemove = runDiff(remote, local);
            if (wantAddRemove.left.isEmpty() && wantAddRemove.right.isEmpty()) {
                // the two collections are the same
                continue;
            }
            Logger.warn(LogType.KHALA_EVENT, "khala data mismatch on node " + n);
            // not same, so we make a direct check on the remote discovery node
            checkRemote(n);
        }
    }

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

    private void checkRemote(Node n, Callback<Void, Throwable> cb) {
        if (discoveryNodeNotExist(n)) {
            cb.failed(new XException("discovery node not exist"));
            return;
        }

        Object[] msg = buildLocalKhalaMsg();
        RESPClientUtils.retry(
            discovery.loop,
            new InetSocketAddress(n.inetAddress, n.tcpPort),
            discovery.config.bindInetAddress,
            msg,
            3000,
            3,
            new Callback<Object, IOException>() {
                @Override
                protected void onSucceeded(Object value) {
                    Tuple<KhalaMsg, XException> tup = validateResponse(value, "khala-local");
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

    // make a diff between remote and local
    // the result is a tuple:
    // < in remote not in local (toAdd), in local not in remote (toRemove)>
    private Tuple</*want_add*/Set<KhalaNode>, /*want_remove*/Set<KhalaNode>>
    runDiff(List<KhalaNode> remote, Set<KhalaNode> local) {
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

    // ---------------------
    // START khala msg
    // ---------------------
    private Object[] buildFullKhalaMsg() {
        return buildKhalaMsgByNodes("khala", nodes.keySet());
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

            Set<KhalaNode> kNodes = khalaNodes.computeIfAbsent(n, x -> new HashSet<>());
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
    // END khala msg
    // ---------------------

    // ---------------------
    // START discovery node operations
    // ---------------------
    private void initNode(Node node) {
        // send full khala info to the node
        // and ignore the result
        checkRemote(node, new Callback<Void, Throwable>() {
            @Override
            protected void onSucceeded(Void value) {
                NodeWrap wrap = nodes.get(node);
                if (wrap == null || wrap.targetState == NodeState.deleted) {
                    Set<KhalaNode> nodes = khalaNodes.remove(node);
                    for (KhalaNode kn : nodes) {
                        alertNodeRemove(node, kn);
                    }
                    Khala.this.nodes.remove(node);
                    return;
                }
                wrap.state = NodeState.stable;
            }

            @Override
            protected void onFailed(Throwable err) {
                // ignore err when failed
                // we remove the node
                nodes.remove(node);
                Set<KhalaNode> kNodes = khalaNodes.remove(node);
                if (kNodes == null)
                    return; // it not exists or already removed
                for (KhalaNode kn : kNodes) {
                    alertNodeRemove(node, kn);
                }
            }
        });
    }

    private void removeNode(Node node) {
        nodes.remove(node);
        Set<KhalaNode> kNodes = khalaNodes.remove(node);
        if (kNodes == null)
            return; // ignore if it not exists
        for (KhalaNode kn : kNodes) {
            alertNodeRemove(node, kn);
            Logger.warn(LogType.KHALA_EVENT, "node removed " + kn);
        }
    }
    // ---------------------
    // END discovery node operations
    // ---------------------

    // ---------------------
    // START khala notification
    // ---------------------
    private void notifyFullKhala(Node node) {
        Object[] msg = buildFullKhalaMsg();
        RESPClientUtils.retry(
            discovery.loop,
            new InetSocketAddress(node.inetAddress, node.tcpPort),
            discovery.config.bindInetAddress,
            msg,
            3000,
            3,
            new Callback<Object, IOException>() {
                @Override
                protected void onSucceeded(Object value) {
                    Tuple<KhalaMsg, XException> tup = validateResponse(value, "khala");
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

    private void notifyAddKhalaNode(KhalaNode node) {
        boolean notifyAll = node.type == KhalaNodeType.nexus;
        for (Node n : khalaNodes.keySet()) {
            if (n.equals(discovery.localNode))
                continue; // don't send to local node
            if (notifyAll || khalaNodes.get(n).stream().anyMatch(kn -> kn.type == KhalaNodeType.nexus)) {
                notify("khala-add", n, node);
            }
        }
    }

    private void notifyRemoveKhalaNode(KhalaNode node) {
        boolean notifyAll = node.type == KhalaNodeType.nexus;
        for (Node n : khalaNodes.keySet()) {
            if (n.equals(discovery.localNode))
                continue; // don't send to local node
            if (notifyAll || khalaNodes.get(n).stream().anyMatch(kn -> kn.type == KhalaNodeType.nexus)) {
                notify("khala-remove", n, node);
            }
        }
    }

    private void notify(String type, Node remoteNode, KhalaNode node) {
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
            discovery.localNode.inetAddress,
            msg,
            3000,
            3,
            new Callback<Object, IOException>() {
                @Override
                protected void onSucceeded(Object value) {
                    Tuple<KhalaMsg, XException> tup = validateResponse(value, "khala");
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

    public void addLocal(KhalaNode khalaNode) {
        discovery.loop.getSelectorEventLoop().runOnLoop(() -> {
            if (!this.localKhalaNodes.add(khalaNode)) {
                // already added
                return;
            }
            Set<KhalaNode> set = this.khalaNodes.computeIfAbsent(discovery.localNode, k -> new HashSet<>());
            set.add(khalaNode);
            notifyAddKhalaNode(khalaNode);
        });
    }

    public void removeLocal(KhalaNode khalaNode) {
        discovery.loop.getSelectorEventLoop().runOnLoop(() -> {
            if (!this.localKhalaNodes.remove(khalaNode)) {
                // not found
                return;
            }
            Set<KhalaNode> set = this.khalaNodes.get(discovery.localNode);
            if (set == null)
                return; // already removed
            set.remove(khalaNode);
            notifyRemoveKhalaNode(khalaNode);
        });
    }

    public Set<KhalaNode> getLocalKhalaNodes() {
        return new HashSet<>(localKhalaNodes);
    }

    public Map<Node, Set<KhalaNode>> getKhalaNodes() {
        return new HashMap<>(khalaNodes);
    }

    public void addKhalaNodeListener(KhalaNodeListener lsn) {
        discovery.loop.getSelectorEventLoop().runOnLoop(() -> {
            // alert add() event for all nodes
            for (Node n : khalaNodes.keySet()) {
                for (KhalaNode kn : khalaNodes.get(n)) {
                    lsn.add(n, kn);
                }
            }
            khalaNodeListeners.add(lsn);
        });
    }

    public void removeKhalaNodeListener(KhalaNodeListener lsn) {
        khalaNodeListeners.remove(lsn);
    }

    private void doSync() {
        List<Node> ns = nodes.keySet()
            .stream()
            .filter(n ->
                !n.equals(discovery.localNode) && // not local node
                    khalaNodes.containsKey(n) && // valid
                    khalaNodes.get(n).stream().anyMatch(kn -> kn.type == KhalaNodeType.nexus) /*only check nexus*/)
            .collect(Collectors.toList());
        if (ns.isEmpty())
            return;
        Node n = ns.get(rand.nextInt(ns.size()));
        notifyFullKhala(n);
    }

    public void sync() {
        discovery.loop.getSelectorEventLoop().runOnLoop(this::doSync);
    }
}
