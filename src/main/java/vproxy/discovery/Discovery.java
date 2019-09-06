package vproxy.discovery;

import vclient.HttpClient;
import vclient.impl.Http1ClientImpl;
import vjson.JSON;
import vjson.ex.JsonParseException;
import vjson.simple.SimpleArray;
import vjson.util.ObjectBuilder;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.exception.*;
import vproxy.component.svrgroup.Method;
import vproxy.component.svrgroup.ServerGroup;
import vproxy.component.svrgroup.ServerListener;
import vproxy.connection.ConnectionHandler;
import vproxy.connection.ConnectionHandlerContext;
import vproxy.connection.NetEventLoop;
import vproxy.discovery.protocol.NodeDataMsg;
import vproxy.discovery.protocol.NodeExistenceMsg;
import vproxy.redis.RESPParser;
import vproxy.redis.Serializer;
import vproxy.selector.SelectorEventLoop;
import vproxy.selector.TimerEvent;
import vproxy.util.*;
import vserver.HttpServer;
import vserver.Tool;
import vserver.server.Http1ServerImpl;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The discovery module.
 * <p>
 * When the instance is started:
 * 1. bind udp and tcp port on the specified nic
 * 2. start a udp sock, and try to send the following data to each ip:port in the specified subnet and port range
 * ******** version=1, type=search, nodeName, udpPort, tcpPort, SHA512(sort(nodeName+address+udpPort+tcpPort (only healthy))) ********
 * 3. the server who receives a `search` packet, it will check the SHA512 of vproxy nodes info, and if doesn't match:
 * 4. the server will send a udp packet to the sender's bindAddress:udpPort
 * ******** version=1, type=inform, nodeName, udpPort, tcpPort, SHA512(sort(nodeName+address+udpPort+tcpPort (only healthy))) ********
 * 5. when a discovery instance receives the `inform` packet, it checks the SHA512, and if doesn't match:
 * 6. make an http request to the server bindAddress:tcpPort, and send the full info of vproxy nodes:
 * ******** PUT /discovery/api/v1/exchange/node body=[nodeName,address,tcpPort,status] ********
 * 7. then the remote server will send back its vproxy nodes info
 * ******** [nodeName,address,udpPort,tcpPort,status] ********
 * 8. all missing nodes will be added to the nodes list, initially down, will be up when health check succeeds
 * 9. when health check is down for 5 minutes, it will be removed from the node list
 * 10. when the node leaves, it sends the following udp packet to all known nodes
 * ******** version=1, type=leave, nodeName, udpPort, tcpPort, "" ********
 * 11. when receiving the packet, they will remove the left node
 * NOTE: all payloads are sent in the form of JSON. For udp, the content json is serialized with RESP Bulk String
 */
public class Discovery {
    class NodeExistenceConnectionHandler implements ConnectionHandler {
        private void clearBuffer(RingBuffer rb) {
            if (rb.used() <= 0)
                return;
            byte[] tmp = new byte[rb.used()];
            ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(tmp);
            while (rb.used() > 0) {
                chnl.reset();
                rb.writeTo(chnl);
            }
        }

        @Override
        public void readable(ConnectionHandlerContext ctx) {
            handle(ctx.connection.remote.getAddress(), ctx.connection.getInBuffer());
        }

        public void readable(InetAddress remoteAddr, byte[] bytes, int len) {
            RingBuffer rb = RingBuffer.allocate(len);
            ByteArrayChannel chnl = ByteArrayChannel.from(bytes, 0, len, 0);
            rb.storeBytesFrom(chnl);
            handle(remoteAddr, rb);
        }

        void handle(InetAddress remoteAddr, RingBuffer buffer) {
            RESPParser parser = new RESPParser(buffer.capacity());
            int res = parser.feed(buffer);
            if (res == -1) {
                String msg = parser.getErrorMessage();
                if (msg == null) {
                    Logger.error(LogType.INVALID_EXTERNAL_DATA, "got incomplete data");
                } else {
                    Logger.error(LogType.INVALID_EXTERNAL_DATA, "parse error " + msg);
                }
                clearBuffer(buffer);
                return;
            }
            if (buffer.used() > 0) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "invalid data, still have data after parsing");
                clearBuffer(buffer);
                return;
            }
            Object o = parser.getResult().getJavaObject();
            if (!(o instanceof String)) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "invalid data, not in string format");
                clearBuffer(buffer);
                return;
            }
            String s = (String) o;
            assert Logger.lowLevelDebug("received external data, expected to be node existence msg: " + s);
            JSON.Object json;
            {
                JSON.Instance inst;
                try {
                    inst = JSON.parse(s);
                } catch (JsonParseException e) {
                    Logger.error(LogType.INVALID_EXTERNAL_DATA, "invalid data, not in json format: " + e.getMessage());
                    clearBuffer(buffer);
                    return;
                }
                if (!(inst instanceof JSON.Object)) {
                    Logger.error(LogType.INVALID_EXTERNAL_DATA, "invalid data, not json object: " + s);
                    clearBuffer(buffer);
                    return;
                }
                json = (JSON.Object) inst;
            }
            NodeExistenceMsg msg;
            try {
                msg = NodeExistenceMsg.parse(json);
            } catch (XException e) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, e.getMessage());
                return;
            }

            if (msg.version != 1) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "invalid message, version not match: " + msg);
                return;
            }
            if (!msg.type.equals("search") && !msg.type.equals("inform") && !msg.type.equals("leave")) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "invalid message, type not match: " + msg);
                return;
            }
            if (msg.udpPort < 1 || msg.udpPort > 65535 || msg.tcpPort < 1 || msg.tcpPort > 65535) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "invalid message, invalid content: " + msg);
                return;
            }

            String remote = Utils.ipStr(remoteAddr.getAddress());

            switch (msg.type) {
                case "search": {
                    if (msg.hash.equals(Discovery.this.hash)) {
                        assert Logger.lowLevelDebug("hash same, ignore this event " + msg);
                        return;
                    }
                    Logger.info(LogType.DISCOVERY_EVENT, "receive search message with different hash: " + msg);

                    Node node;
                    try {
                        node = new Node(msg.nodeName, remote, msg.udpPort, msg.tcpPort);
                    } catch (UnknownHostException e) {
                        Logger.shouldNotHappen("the remote endpoint read from connection is wrong " + e);
                        return;
                    }
                    informNode(node);
                    break;
                }
                case "inform": {
                    if (msg.hash.equals(Discovery.this.hash)) {
                        assert Logger.lowLevelDebug("hash same, ignore this event " + msg);
                        return;
                    }
                    Logger.info(LogType.DISCOVERY_EVENT, "receive inform message with different hash: " + msg);

                    Node node;
                    try {
                        node = new Node(msg.nodeName, remote, msg.udpPort, msg.tcpPort);
                    } catch (UnknownHostException e) {
                        Logger.shouldNotHappen("the remote endpoint read from connection is wrong " + e);
                        return;
                    }

                    if (initialSearchCount >= config.initialMinSearch) {
                        resetSearchCount(); // got a response, and already searched multiple times, so we reset the search count
                    }
                    requestForNodes(node);
                    break;
                }
                case "leave":
                    Logger.info(LogType.DISCOVERY_EVENT, "receive leave message: " + msg);

                    String groupServerName = buildGroupServerName(msg.nodeName, remote, msg.tcpPort);
                    removeNode(groupServerName);
                    break;
            }
            // there are no other types
        }

        @Override
        public void writable(ConnectionHandlerContext ctx) {
            // ignore
        }

        @Override
        public void exception(ConnectionHandlerContext ctx, IOException err) {
            assert Logger.lowLevelDebug("got exception for udp conn " + ctx.connection + " " + err);
            ctx.connection.close();
        }

        @Override
        public void remoteClosed(ConnectionHandlerContext ctx) {
            ctx.connection.close();
            closed(ctx);
        }

        @Override
        public void closed(ConnectionHandlerContext ctx) {
            // ignore
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            ctx.connection.close();
        }
    }

    class NodeDetach {
        public final String keyName;
        public final Node node;
        public TimerEvent detachTimer;

        NodeDetach(Node node) {
            this(buildGroupServerName(node), node, false);
        }

        NodeDetach(String keyName, Node node, boolean doNotDetach) {
            this.keyName = keyName;
            this.node = node;

            if (!doNotDetach /*this flag is true only when adding THIS node itself*/) {
                // the node is unhealthy by default
                // so add the detach timer when initiating
                startTimer();
            }
        }

        void startTimer() {
            if (detachTimer != null)
                return; // already started
            detachTimer = loop.getSelectorEventLoop().delay(config.timeConfig.detachTimeout, () -> {
                removeNode(keyName);
                detachTimer = null;
            });
        }

        void pause() {
            if (detachTimer == null)
                return; // it's already removed or is `self`
            detachTimer.cancel();
            detachTimer = null;
        }
    }

    class HealthListener implements ServerListener {
        @Override
        public void up(ServerGroup.ServerHandle server) {
            String key = server.alias;
            NodeDetach n = nodes.get(key);
            if (n == null)
                return; // let's ignore if not found

            Logger.info(LogType.DISCOVERY_EVENT, "node " + server.alias + " is UP");

            n.pause(); // stop the timer because it's up

            n.node.healthy = true;
            calcAll(); // recalculate hash and other related things

            // alert up
            alertNodeListeners(lsn -> lsn.up(n.node));
        }

        @Override
        public void down(ServerGroup.ServerHandle server) {
            String key = server.alias;
            NodeDetach n = nodes.get(key);
            if (n == null)
                return; // let's ignore if not found

            Logger.warn(LogType.DISCOVERY_EVENT, "node " + server.alias + " is DOWN");

            n.startTimer(); // it's down, so start the detach timer

            n.node.healthy = false;
            calcAll(); // recalculate hash and other related things

            // alert down
            alertNodeListeners(lsn -> lsn.down(n.node));
        }

        @Override
        public void start(ServerGroup.ServerHandle server) {
            // ignore
        }

        @Override
        public void stop(ServerGroup.ServerHandle server) {
            // ignore
        }
    }

    private static void utilByteArrayInc(byte[] arr) {
        for (int i = arr.length - 1; i >= 0; --i) {
            byte b = arr[i];
            arr[i] += 1;
            if (b != -1) {
                break;
            }
        }
    }

    private static String buildGroupServerName(Node n) {
        return buildGroupServerName(n.nodeName, n.address, n.tcpPort);
    }

    private static String buildGroupServerName(String nodeName, String address, int tcpPort) {
        return nodeName + "@" + address + ":" + tcpPort;
    }

    public final String nodeName; // this is not the identifier, just a hint for human to read
    public final NetEventLoop loop;
    public final Node localNode;

    public final DiscoveryConfig config;
    private long searchCount = 0;
    private long searchAddressCursor = 0;
    private int searchPortCursor = 0;
    private byte[] searchNetworkByte;

    private final Map<String /*buildGroupServerName*/, NodeDetach> nodes = new ConcurrentHashMap<>();
    private final ServerGroup hcGroup;
    private String hash;
    private final ByteBuffer searchBuffer;
    private final ByteBuffer informBuffer;

    // resources
    private final EventLoopGroup eventLoopGroup;
    private final SelectorEventLoop blockingUDPSendThread;
    private final SelectorEventLoop blockingUDPRecvThread;
    private final DatagramSocket udpBlockingSock;
    private final DatagramSocket udpBlockingServer;
    private final HttpServer httpServer;

    private boolean intoInterval = false; // should go into a long interval
    private boolean isInInterval = false; // is already into the interval
    private boolean closed = false;

    private final NodeExistenceConnectionHandler nodeExistenceConnectionHandler = new NodeExistenceConnectionHandler();

    private Set<NodeListener> nodeListeners = new HashSet<>();
    private Set<NodeDataHandler> externalHandlers = new HashSet<>();

    private int initialSearchCount = 0;

    public Discovery(String nodeName, DiscoveryConfig config) throws IOException {
        SelectorEventLoop blockingUDPSendThread = null;
        SelectorEventLoop blockingUDPRecvThread = null;
        EventLoopGroup eventLoopGroup = null;
        ServerGroup hcGroup = null;
        ByteBuffer searchBuffer = null;
        ByteBuffer informBuffer = null;
        DatagramSocket udpBlockingSock = null;
        DatagramSocket udpBlockingServer = null;
        HttpServer tcpServer = null;

        try {
            blockingUDPSendThread = SelectorEventLoop.open();
            blockingUDPSendThread.loop(r -> new Thread(r, "BlockingUDPSendThread:" + nodeName));
            blockingUDPRecvThread = SelectorEventLoop.open();
            blockingUDPRecvThread.loop(r -> new Thread(r, "BlockingUDPRecvThread:" + nodeName));
            eventLoopGroup = new EventLoopGroup("EventLoopGroup:" + nodeName);
            try {
                eventLoopGroup.add("EventLoop:" + nodeName);
            } catch (AlreadyExistException | ClosedException e) {
                Logger.shouldNotHappen("adding event loop failed", e);
                throw new RuntimeException(e);
            }

            this.nodeName = nodeName;

            this.loop = eventLoopGroup.next();
            assert this.loop != null;

            this.config = config;
            try {
                hcGroup = new ServerGroup("nodes", eventLoopGroup, config.healthCheckConfig, Method.wrr);
            } catch (AlreadyExistException | ClosedException e) {
                Logger.shouldNotHappen("adding health check group failed", e);
                throw new RuntimeException(e);
            }
            hcGroup.addServerListener(new HealthListener());

            searchBuffer = ByteBuffer.allocate(nodeName.getBytes().length + 256/*make it large enough*/);
            informBuffer = ByteBuffer.allocate(nodeName.getBytes().length + 256/*make it large enough*/);
            Node n = new Node(nodeName, config.bindAddress, config.udpPort, config.tcpPort);
            n.healthy = true;
            this.localNode = n;
            String groupServerName = buildGroupServerName(nodeName, config.bindAddress, config.tcpPort);
            nodes.put(groupServerName, new NodeDetach(groupServerName, n, true));

            udpBlockingSock = startUdpBlockingSock();
            udpBlockingServer = createUdpBlockingServer();
            tcpServer = startHttpServer();
        } catch (Throwable t) {
            // release
            if (blockingUDPSendThread != null)
                blockingUDPSendThread.close();
            if (blockingUDPRecvThread != null)
                blockingUDPRecvThread.close();
            if (eventLoopGroup != null)
                eventLoopGroup.close();
            if (hcGroup != null)
                hcGroup.clear();
            if (searchBuffer != null)
                Utils.clean(searchBuffer);
            if (informBuffer != null)
                Utils.clean(informBuffer);
            if (udpBlockingSock != null)
                udpBlockingSock.close();
            if (udpBlockingServer != null)
                udpBlockingServer.close();
            //noinspection ConstantConditions
            if (tcpServer != null)
                tcpServer.close();
            // raise
            if (t instanceof IOException) {
                throw (IOException) t;
            } else if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else {
                throw new RuntimeException(t);
            }
        }
        // assign local fields
        this.blockingUDPSendThread = blockingUDPSendThread;
        this.blockingUDPRecvThread = blockingUDPRecvThread;
        this.eventLoopGroup = eventLoopGroup;
        this.hcGroup = hcGroup;
        this.searchBuffer = searchBuffer;
        this.informBuffer = informBuffer;
        this.udpBlockingSock = udpBlockingSock;
        this.udpBlockingServer = udpBlockingServer;
        this.httpServer = tcpServer;

        // calc
        calcAll();
        resetSearchAddressBytes();

        // start
        startUdpBlockingServer();
        loop.getSelectorEventLoop().delay(config.timeConfig.delayWhenNotJoined, this::startSearch);
    }

    private void alertNodeListeners(Consumer<NodeListener> f) {
        for (NodeListener lsn : nodeListeners) {
            f.accept(lsn);
        }
    }

    private void handleReceivedNodeData(@SuppressWarnings("SameParameterValue") int version,
                                        String type,
                                        JSON.Array array,
                                        Callback<Void, XException> cb) {
        NodeDataMsg msg;
        try {
            msg = NodeDataMsg.parse(version, type, array);
        } catch (XException e) {
            Logger.error(LogType.INVALID_EXTERNAL_DATA, e.getMessage());
            cb.failed(e);
            return;
        }
        if (msg.version != 1) {
            Logger.error(LogType.INVALID_EXTERNAL_DATA, "invalid message, version mismatch: " + msg);
            cb.failed(new XException("version mismatch"));
            return;
        }
        if (!msg.type.equals("node")) {
            Logger.error(LogType.INVALID_EXTERNAL_DATA, "invalid message, type mismatch: " + msg);
            cb.failed(new XException("type mismatch"));
            return;
        }
        Logger.info(LogType.DISCOVERY_EVENT, "receive nodes message, recording ...");

        // parse done
        cb.succeeded(null);

        // record the nodes
        for (Node n : msg.nodes) {
            recordNode(n);
        }
    }

    private JSON.Array getNodeDataToSend() {
        List<JSON.Object> nodeList = new LinkedList<>();
        for (NodeDetach n : nodes.values()) {
            ObjectBuilder e = new ObjectBuilder()
                .put("nodeName", n.node.nodeName)
                .put("address", n.node.address)
                .put("udpPort", n.node.udpPort)
                .put("tcpPort", n.node.tcpPort)
                .put("healthy", n.node.healthy);
            nodeList.add(e.build());
        }
        return new SimpleArray(nodeList);
    }

    private void resetSearchAddressBytes() {
        searchNetworkByte = new byte[config.searchNetworkByte.length];
        System.arraycopy(config.searchNetworkByte, 0, searchNetworkByte, 0, searchNetworkByte.length);
    }

    private DatagramSocket startUdpBlockingSock() throws IOException {
        DatagramSocket sock = new DatagramSocket(null);
        sock.bind(new InetSocketAddress(config.bindInetAddress, config.udpSockPort));
        return sock;
    }

    private DatagramSocket createUdpBlockingServer() throws IOException {
        DatagramSocket server = new DatagramSocket(null);
        // server.setReuseAddress(true); grralvm doesn't support
        server.bind(new InetSocketAddress(config.bindInetAddress, config.udpPort));
        return server;
    }

    private void startUdpBlockingServer() {
        blockingUDPRecvThread.runOnLoop(() -> {
            byte[] buf = new byte[16384];
            DatagramPacket pkt = new DatagramPacket(buf, 0, buf.length);
            while (!udpBlockingServer.isClosed()) {
                try {
                    udpBlockingServer.receive(pkt);
                } catch (IOException e) {
                    // maybe the error is because that the socket is closed
                    if (!udpBlockingServer.isClosed()) {
                        Logger.shouldNotHappen("udp blocking server receive() failed", e);
                    }
                    continue;
                }
                nodeExistenceConnectionHandler.readable(pkt.getAddress(), buf, pkt.getLength());
            }
        });
    }

    private HttpServer startHttpServer() throws IOException {
        HttpServer server = new Http1ServerImpl(loop);
        server.all("/*", Tool.bodyJsonHandler);
        server.put("/discovery/api/v1/exchange/:type", rctx -> {
            assert Logger.lowLevelDebug("received exchange request: " + rctx.param("type") + " " + rctx.get(Tool.bodyJson));
            JSON.Instance inst = rctx.get(Tool.bodyJson);
            if (inst == null) {
                rctx.response().status(400).end("{\"code\":400,\"message\":\"request body should not be null\"}");
            }
            String type = rctx.param("type");

            if (!type.equals("node")) {
                // not `nodes` message
                // maybe an upper level message
                // so let's try to handle it in external application handler
                boolean found = false;
                for (NodeDataHandler h : externalHandlers) {
                    if (h.canHandle(type)) {
                        found = true;
                        h.handle(1, type, inst, new Callback<>() {
                            @Override
                            protected void onSucceeded(JSON.Instance value) {
                                if (value == null) {
                                    rctx.response().status(204).end();
                                } else {
                                    rctx.response().status(200).end(value);
                                }
                            }

                            @Override
                            protected void onFailed(Throwable err) {
                                rctx.response().status(400).end(new ObjectBuilder()
                                    .put("code", 400)
                                    .put("message", err.getMessage())
                                    .build());
                            }
                        });
                        break;
                    }
                }
                if (!found) {
                    rctx.response().status(400).end(new ObjectBuilder()
                        .put("code", 400)
                        .put("message", "unknown message type " + type)
                        .build());
                }
                return;
            }

            if (!(inst instanceof JSON.Array)) {
                rctx.response().status(400).end("request body is invalid, expecting JSON.Array");
                return;
            }
            JSON.Array array = (JSON.Array) inst;
            handleReceivedNodeData(1, type, array, new Callback<>() {
                @Override
                protected void onSucceeded(Void value) {
                    // return this node messages
                    rctx.response().status(200).end(getNodeDataToSend());
                }

                @Override
                protected void onFailed(XException err) {
                    // ignore, messages are logged in `handleReceivedNodeData()`
                    rctx.response().status(400).end("{\"code\":400,\"message\":\"invalid request\"}");
                }
            });
        });
        server.listen(new InetSocketAddress(config.bindInetAddress, config.tcpPort));
        return server;
    }

    private void recordNode(Node node) {
        String groupServerName = buildGroupServerName(node);
        if (!nodes.containsKey(groupServerName)) {
            try {
                hcGroup.add(groupServerName, node.address, new InetSocketAddress(node.address, node.tcpPort), 10);
            } catch (AlreadyExistException e) {
                Logger.shouldNotHappen("the node already exist in hcGroup " + groupServerName);
            }
            node.healthy = false; // default is false
            nodes.put(groupServerName, new NodeDetach(node));
            Logger.info(LogType.DISCOVERY_EVENT, "new node is recorded: " + node);
            // no need to calculate hash for now
            // calculate when the node goes UP
        }
    }

    private void removeNode(String groupServerName) {
        if (!nodes.containsKey(groupServerName)) {
            Logger.error(LogType.INVALID_EXTERNAL_DATA, "the name " + groupServerName + " not exists");
            return;
        }
        Node node = nodes.remove(groupServerName).node;
        try {
            hcGroup.remove(groupServerName);
        } catch (NotFoundException e) {
            Logger.shouldNotHappen("the node not exist in hcGroup " + groupServerName);
        }
        calcAll();

        // alert down
        alertNodeListeners(lsn -> lsn.leave(node));

        Logger.warn(LogType.DISCOVERY_EVENT, "node " + groupServerName + " is REMOVED");
    }

    private void sendBuffer(ByteBuffer buffer, InetSocketAddress sockAddr) {
        int pos = buffer.position();
        int lim = buffer.limit();
        byte[] bytes = buffer.array();
        DatagramPacket pkt = new DatagramPacket(bytes, lim);
        pkt.setAddress(sockAddr.getAddress());
        pkt.setPort(sockAddr.getPort());
        blockingUDPSendThread.runOnLoop(() -> {
            assert Logger.lowLevelDebug("run blocking udp sock to send data");
            try {
                udpBlockingSock.send(pkt);
            } catch (IOException e) {
                Logger.shouldNotHappen("send udp pkt failed", e);
            }
        });
        assert Logger.lowLevelDebug("udpSock.send wrote " + (lim - pos) + " bytes");
        buffer.position(pos).limit(lim);
    }

    private void informNode(Node node) {
        sendBuffer(informBuffer, new InetSocketAddress(node.inetAddress, node.udpPort));
    }

    private void startSearch() {
        if (closed)
            return; // do nothing if it's already closed

        if (intoInterval && !isInInterval) {
            intoInterval = false; // reset the flag
            isInInterval = true;
            int delay = nodes.size() <= 1 /*1 means the node itself*/
                ? config.timeConfig.intervalWhenNotJoined
                : config.timeConfig.intervalWhenJoined;
            loop.getSelectorEventLoop().delay(delay, this::startSearch);
            return;
        }
        isInInterval = false;
        intoInterval = false;

        InetSocketAddress sockAddr = nextSearch();
        if (sockAddr != null) {
            sendBuffer(searchBuffer, sockAddr);
        }

        int delay = nodes.size() == 1 /*1 means the node itself*/
            ? config.timeConfig.delayWhenNotJoined
            : config.timeConfig.delayWhenJoined;
        loop.getSelectorEventLoop().delay(delay, this::startSearch);
    }

    private void resetSearchCount() {
        searchCount = 0;
        intoInterval = true;
    }

    private InetSocketAddress nextSearch() {
        if (searchCount >= config.searchMaxCount) {
            // the round finishes
            resetSearchCount();
            return null;
        }
        ++searchCount;
        if (initialSearchCount <= config.initialMinSearch) {
            ++initialSearchCount;
        }

        // inc and check whether the port cursor is too big
        int curPortCursor = searchPortCursor++;
        if (curPortCursor > (config.searchMaxUDPPort - config.searchMinUDPPort)) {
            searchPortCursor = 1;
            curPortCursor = 0;

            ++searchAddressCursor;
            utilByteArrayInc(searchNetworkByte);
        }
        int port = config.searchMinUDPPort + curPortCursor;

        // inc and check whether the address cursor is too big
        if ((searchAddressCursor++) >= config.searchNetworkCursorMaxExclusive) {
            // reset port/address cursor, start from first address and first port in the network
            searchAddressCursor = 1;
            resetSearchAddressBytes();
        }

        String addrStr = Utils.ipStr(searchNetworkByte);
        if (config.bindAddress.equals(addrStr) && config.udpPort == port) {
            // same as local, return null this time
            assert Logger.lowLevelDebug("local " + addrStr + ":" + port);
            return null;
        }

        InetAddress addr;
        try {
            addr = InetAddress.getByAddress(searchNetworkByte);
        } catch (UnknownHostException e) {
            Logger.shouldNotHappen("get InetAddress from " + Utils.ipStr(searchNetworkByte) + " failed " + e);
            return null;
        }

        return new InetSocketAddress(addr, port);
    }

    private void requestForNodes(Node target) {
        assert Logger.lowLevelDebug("request for nodes, target = " + target);
        HttpClient client = new Http1ClientImpl(new InetSocketAddress(target.inetAddress, target.tcpPort), loop, 3_000);
        client.put("/discovery/api/v1/exchange/node").send(getNodeDataToSend(), (err, resp) -> {
            if (err != null) {
                Logger.error(LogType.CONN_ERROR, "failed to send node-exchange request", err);
                return;
            }
            if (resp.status() != 200) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "the node-exchange request failed");
                return;
            }
            JSON.Instance inst = resp.bodyAsJson();
            if (inst == null) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "the node-exchange response body is empty");
                return;
            }
            if (!(inst instanceof JSON.Array)) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "the node-exchange response body is not an array");
                return;
            }
            JSON.Array array = (JSON.Array) inst;
            handleReceivedNodeData(1, "node", array, new Callback<>() {
                @Override
                protected void onSucceeded(Void value) {
                    // do nothing, everything is done
                }

                @Override
                protected void onFailed(XException err) {
                    // do nothing, msg is logged in `handleReceivedNodeData()`
                }
            });
        });
    }

    private void calcAll() {
        hash = calcHash();
        calcSearchBuffer();
        calcInformBuffer();
    }

    private String calcHash() {
        PriorityQueue<Node> p = new PriorityQueue<>(Node::compareTo);
        for (NodeDetach n : nodes.values()) {
            if (n.node.healthy) {
                p.add(n.node);
            } // ignore unhealthy nodes
        }
        StringBuilder sb = new StringBuilder();
        while (!p.isEmpty()) {
            Node n = p.poll();
            sb.append(n.nodeName).append(",")
                .append(n.address).append(",")
                .append(n.udpPort).append(",")
                .append(n.tcpPort).append(",");
        }
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            Logger.shouldNotHappen("SHA-512 not found");
            throw new RuntimeException(e);
        }
        md.update(sb.toString().getBytes());
        byte[] bytes = md.digest();
        return Base64.getEncoder().encodeToString(bytes);
    }

    private void calcSearchBuffer() {
        searchBuffer.position(0).limit(searchBuffer.capacity());
        // build the message
        String message = new ObjectBuilder()
            .put("version", 1)
            .put("type", "search")
            .put("nodeName", nodeName)
            .put("udpPort", config.udpPort)
            .put("tcpPort", config.tcpPort)
            .put("hash", hash)
            .build().stringify();
        byte[] bytes = Serializer.from(message);
        searchBuffer.put(bytes);
        searchBuffer.flip();
    }

    private void calcInformBuffer() {
        informBuffer.position(0).limit(informBuffer.capacity());
        // build the message
        String message = new ObjectBuilder()
            .put("version", 1)
            .put("type", "inform")
            .put("nodeName", nodeName)
            .put("udpPort", config.udpPort)
            .put("tcpPort", config.tcpPort)
            .put("hash", hash)
            .build().stringify();
        byte[] bytes = Serializer.from(message);
        informBuffer.put(bytes);
        informBuffer.flip();
    }

    public List<Node> getNodes() {
        return nodes.values().stream().map(n -> n.node).collect(Collectors.toList());
    }

    public void addNodeListener(NodeListener lsn) {
        loop.getSelectorEventLoop().runOnLoop(() -> {
            // alert `up()` for all healthy nodes
            for (NodeDetach n : nodes.values()) {
                if (n.node.healthy && (!n.node.address.equals(config.bindAddress) || n.node.tcpPort != config.tcpPort)) {
                    lsn.up(n.node);
                }
            }
            nodeListeners.add(lsn);
        });
    }

    public void addExternalHandler(NodeDataHandler h) {
        loop.getSelectorEventLoop().runOnLoop(() ->
            externalHandlers.add(h)
        );
    }

    public boolean isClosed() {
        return closed;
    }

    public void close(Callback<Void, /*will not fire*/NoException> cb) {
        if (closed) {
            cb.succeeded(null);
            return;
        }
        closed = true;

        // close the udp server to stop receiving packets
        udpBlockingServer.close();
        // send `leave` message to all nodes
        String messageToSend = new ObjectBuilder()
            .put("version", 1)
            .put("type", "leave")
            .put("nodeName", nodeName)
            .put("udpPort", config.udpPort)
            .put("tcpPort", config.tcpPort)
            .put("hash", "")
            .build().stringify();
        byte[] bytesToSend = Serializer.from(messageToSend);
        ByteBuffer byteBuffer = ByteBuffer.allocate(bytesToSend.length);
        byteBuffer.put(bytesToSend);
        byteBuffer.flip();
        leave(byteBuffer, nodes.values().iterator(), new Callback<>() {
            @Override
            protected void onSucceeded(Void value) {
                // then release
                releaseAfterLeave(cb);
            }

            @Override
            protected void onFailed(NoException err) {
                // will not fire
            }
        });
    }

    private void leave(ByteBuffer leaveMsg, Iterator<NodeDetach> nodes, Callback<Void, NoException> cb) {
        if (!nodes.hasNext()) {
            Utils.clean(leaveMsg); // do release if it's direct memory
            cb.succeeded(null);
            return;
        }
        NodeDetach n;
        try {
            n = nodes.next();
        } catch (NoSuchElementException e) {
            leave(leaveMsg, nodes, cb);
            return;
        }
        if (n.node.address.equals(config.bindAddress) && n.node.udpPort == config.udpPort) {
            // self node, ignore
            leave(leaveMsg, nodes, cb);
            return;
        }
        sendBuffer(leaveMsg, new InetSocketAddress(n.node.address, n.node.udpPort));
        loop.getSelectorEventLoop().delay(config.timeConfig.ppsLimitWhenNotJoined, () -> leave(leaveMsg, nodes, cb));
    }

    private void releaseAfterLeave(Callback<Void, NoException> cb) {
        httpServer.close();
        try {
            eventLoopGroup.remove("EventLoop:" + nodeName);
        } catch (NotFoundException e) {
            Logger.shouldNotHappen("removing event loop failed", e);
            // we ignore the error because it's closing
        }
        udpBlockingSock.close();

        // then release the buffers
        Utils.clean(searchBuffer);
        Utils.clean(informBuffer);

        // close blocking threads
        try {
            blockingUDPRecvThread.close();
        } catch (IOException ignore) {
        }
        try {
            blockingUDPSendThread.close();
        } catch (IOException ignore) {
        }

        // callback
        cb.succeeded(null);
    }
}
