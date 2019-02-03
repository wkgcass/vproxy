package net.cassite.vproxy.discovery;

import net.cassite.vproxy.component.elgroup.EventLoopGroup;
import net.cassite.vproxy.component.exception.*;
import net.cassite.vproxy.component.svrgroup.Method;
import net.cassite.vproxy.component.svrgroup.ServerGroup;
import net.cassite.vproxy.component.svrgroup.ServerListener;
import net.cassite.vproxy.connection.*;
import net.cassite.vproxy.discovery.protocol.NodeExistenceMsg;
import net.cassite.vproxy.discovery.protocol.NodeDataMsg;
import net.cassite.vproxy.redis.Parser;
import net.cassite.vproxy.redis.Serializer;
import net.cassite.vproxy.selector.TimerEvent;
import net.cassite.vproxy.util.*;
import sun.nio.ch.DirectBuffer;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NetworkChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
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
 * 6. make a tcp connection to the server bindAddress:tcpPort, and send the full info of vproxy nodes:
 * ******** version=1, type=nodes, list:[nodeName,address,tcpPort,status] ********
 * 7. then the remote server will send back its vproxy nodes info
 * ******** version=1, type=nodes, list:[nodeName,address,udpPort,tcpPort,status] ********
 * 8. all missing nodes will be added to the nodes list, initially down, will be up when health check succeeds
 * 9. when health check is down for 5 minutes, it will be removed from the node list
 * 10. when the node leaves, it sends the following udp packet to all known nodes
 * ******** version=1, type=leave, nodeName, udpPort, tcpPort, (empty string) ********
 * 11. when receiving the packet, they will remove the left node
 */
public class Discovery {
    class NodeExistenceServerHandler implements ServerHandler {
        @Override
        public void acceptFail(ServerHandlerContext ctx, IOException err) {
            // will not fire for udp
        }

        @Override
        public void connection(ServerHandlerContext ctx, Connection connection) {
            // ignore, will fire readable later
        }

        @Override
        public Tuple<RingBuffer, RingBuffer> getIOBuffers(NetworkChannel channel) {
            // the buffer should be large enough
            // and we use heap memory because it usually only used once then destroyed
            return new Tuple<>(RingBuffer.allocate(2048), RingBuffer.allocate(2048));
        }

        @Override
        public void removed(ServerHandlerContext ctx) {
            ctx.server.close();
        }

        @Override
        public void exception(ServerHandlerContext ctx, IOException err) {
            // only log, we do not care
            assert Logger.lowLevelDebug("got exception for udp server " + ctx.server + " " + err);
        }

        @Override
        public ConnectionHandler udpHandler(ServerHandlerContext ctx, Connection conn) {
            return nodeExistenceConnectionHandler;
        }
    }

    class NodeExistenceConnectionHandler implements ConnectionHandler {
        private void clearBuffer(ConnectionHandlerContext ctx) {
            RingBuffer rb = ctx.connection.inBuffer;
            if (rb.used() <= 0)
                return;
            byte[] tmp = new byte[rb.used()];
            ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(tmp);
            while (rb.used() > 0) {
                chnl.reset();
                try {
                    rb.writeTo(chnl);
                } catch (IOException e) {
                    // will not happen, it's memory operation
                }
            }
        }

        @Override
        public void readable(ConnectionHandlerContext ctx) {
            Parser parser = new Parser(ctx.connection.inBuffer.capacity());
            int res = parser.feed(ctx.connection.inBuffer);
            if (res == -1) {
                String msg = parser.getErrorMessage();
                if (msg == null) {
                    Logger.error(LogType.INVALID_EXTERNAL_DATA, "got incomplete data");
                } else {
                    Logger.error(LogType.INVALID_EXTERNAL_DATA, "parse error " + msg);
                }
                clearBuffer(ctx);
                return;
            }
            if (ctx.connection.inBuffer.used() > 0) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "invalid data, still have data after parsing");
                clearBuffer(ctx);
                return;
            }
            Object o = parser.getResult().getJavaObject();
            NodeExistenceMsg msg;
            try {
                msg = NodeExistenceMsg.parse(o);
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

            String remote = Utils.ipStr(ctx.connection.remote.getAddress().getAddress());

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
                        Logger.shouldNotHappen("the remote read from connection is wrong " + e);
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
                        Logger.shouldNotHappen("the remote read from connection is wrong " + e);
                        return;
                    }

                    resetSearchCount(); // got a response, so we reset the search count
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
        public void closed(ConnectionHandlerContext ctx) {
            // ignore
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            ctx.connection.close();
        }
    }

    class NodeDataHandler implements ClientConnectionHandler {
        final ByteArrayChannel chnl;
        final Parser redisParser;

        NodeDataHandler() {
            List<Object[]> nodeList = new LinkedList<>();
            for (NodeDetach n : nodes.values()) {
                Object[] e = {
                    n.node.nodeName,
                    n.node.address,
                    n.node.udpPort,
                    n.node.tcpPort,
                    n.node.healthy ? 1 : 0 // resp doesn't support boolean
                };
                nodeList.add(e);
            }
            Object[] message = {
                1 /*version*/,
                "nodes" /*type*/,
                nodeList,
            };
            byte[] bytes = Serializer.from(message);
            chnl = ByteArrayChannel.fromFull(bytes);
            redisParser = new Parser(16384);
        }

        @Override
        public void connected(ClientConnectionHandlerContext ctx) {
            try {
                ctx.connection.outBuffer.storeBytesFrom(chnl);
            } catch (IOException e) {
                // should not happen, it's memory operation
            }
        }

        protected void runOnReadDone(ConnectionHandlerContext ctx) {
            ctx.connection.close();
        }

        @Override
        public void readable(ConnectionHandlerContext ctx) {
            int res = redisParser.feed(ctx.connection.inBuffer);
            if (res == -1) {
                String msg = redisParser.getErrorMessage();
                if (msg == null) {
                    // want more data
                    return;
                }
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "invalid message, parse failed: " + msg);
                ctx.connection.close(); // close the connection on parse error
                return;
            }
            // everything received
            runOnReadDone(ctx);

            // parse done
            // do not check whether there's data left, we don't care
            Object o = redisParser.getResult().getJavaObject();
            NodeDataMsg msg;
            try {
                msg = NodeDataMsg.parse(o);
            } catch (XException e) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, e.getMessage());
                return;
            }
            if (msg.version != 1) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "invalid message, version mismatch: " + msg);
                return;
            }
            if (!msg.type.equals("nodes")) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "invalid message, type mismatch: " + msg);
                return;
            }
            Logger.info(LogType.DISCOVERY_EVENT, "receive nodes message, recording ...");

            // record the nodes
            for (Node n : msg.nodes) {
                recordNode(n);
            }
        }

        @Override
        public void writable(ConnectionHandlerContext ctx) {
            try {
                ctx.connection.outBuffer.storeBytesFrom(chnl);
            } catch (IOException e) {
                // should not happen, it's memory operation
            }
        }

        @Override
        public void exception(ConnectionHandlerContext ctx, IOException err) {
            Logger.error(LogType.UNEXPECTED, "exception occurred in connection", err);
            ctx.connection.close();
        }

        @Override
        public void closed(ConnectionHandlerContext ctx) {
            // ignore
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            // removed, so close
            ctx.connection.close();
        }
    }

    class NodeDataServerHandler implements ServerHandler {
        @Override
        public void acceptFail(ServerHandlerContext ctx, IOException err) {
            // ignore
            assert Logger.lowLevelDebug("accept failed " + err);
        }

        @Override
        public void connection(ServerHandlerContext ctx, Connection connection) {
            assert Logger.lowLevelDebug("got new connection in ResponseNode " + connection);
            try {
                loop.addConnection(connection, null, new NodeDataConnectionHandler());
            } catch (IOException e) {
                Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "add connection into loop failed", e);
            }
        }

        @Override
        public Tuple<RingBuffer, RingBuffer> getIOBuffers(NetworkChannel channel) {
            // use heap buffer because the connection will be terminated very shortly
            return new Tuple<>(RingBuffer.allocate(16384), RingBuffer.allocate(16384));
        }

        @Override
        public void removed(ServerHandlerContext ctx) {
            ctx.server.close();
        }
    }

    class NodeDataConnectionHandler extends NodeDataHandler implements ConnectionHandler {
        @Override
        protected void runOnReadDone(ConnectionHandlerContext ctx) {
            try {
                ctx.connection.outBuffer.storeBytesFrom(chnl);
            } catch (IOException e) {
                // should not happen, it's memory operation
            }
        }

        @Override
        public void connected(ClientConnectionHandlerContext ctx) {
            // will not fire
        }

        @Override
        public void closed(ConnectionHandlerContext ctx) {
            // ignore closed events in the server
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
            detachTimer = loop.getSelectorEventLoop().delay(config.timeoutConfig.detachTimeout, () -> {
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
        for (int i = arr.length - 1; i >= 0; ++i) {
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
    private final DatagramChannel udpSock;
    private final BindServer udpServer;
    private final BindServer tcpServer;

    private boolean intoInterval = false; // should go into a long interval
    private boolean isInInterval = false; // is already into the interval
    private boolean closed = false;

    private final NodeExistenceServerHandler nodeExistenceServerHandler = new NodeExistenceServerHandler();
    private final NodeExistenceConnectionHandler nodeExistenceConnectionHandler = new NodeExistenceConnectionHandler();
    private final NodeDataServerHandler nodeDataServerHandler = new NodeDataServerHandler();

    private CopyOnWriteArraySet<NodeListener> nodeListeners = new CopyOnWriteArraySet<>();

    public Discovery(String nodeName,
                     DiscoveryConfig config) throws IOException {
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
            this.hcGroup = new ServerGroup("nodes", eventLoopGroup, config.healthCheckConfig, Method.wrr);
        } catch (AlreadyExistException | ClosedException e) {
            Logger.shouldNotHappen("adding health check group failed", e);
            throw new RuntimeException(e);
        }
        this.hcGroup.addServerListener(new HealthListener());

        searchBuffer = ByteBuffer.allocateDirect(nodeName.getBytes().length + 256/*make it large enough*/);
        informBuffer = ByteBuffer.allocateDirect(nodeName.getBytes().length + 256/*make it large enough*/);
        Node n = new Node(nodeName, config.bindAddress, config.udpPort, config.tcpPort);
        n.healthy = true;
        String groupServerName = buildGroupServerName(nodeName, config.bindAddress, config.tcpPort);
        nodes.put(groupServerName, new NodeDetach(groupServerName, n, true));
        calcAll();

        resetSearchAddressBytes();

        this.udpSock = startUdpSock();
        try {
            this.udpServer = startUdpServer();
        } catch (IOException e) {
            this.udpSock.close();
            throw e;
        }
        try {
            this.tcpServer = startTcpServer();
        } catch (IOException e) {
            this.udpSock.close();
            this.udpServer.close();
            throw e;
        }

        startSearch();
    }

    private void alertNodeListeners(Consumer<NodeListener> f) {
        for (NodeListener lsn : nodeListeners) {
            f.accept(lsn);
        }
    }

    private void resetSearchAddressBytes() {
        searchNetworkByte = new byte[config.searchNetworkByte.length];
        System.arraycopy(config.searchNetworkByte, 0, searchNetworkByte, 0, searchNetworkByte.length);
    }

    private DatagramChannel startUdpSock() throws IOException {
        DatagramChannel chnl = DatagramChannel.open((config.bindInetAddress instanceof Inet6Address)
            ? StandardProtocolFamily.INET6
            : StandardProtocolFamily.INET);
        chnl.configureBlocking(false);
        chnl.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        chnl.bind(new InetSocketAddress(config.bindInetAddress, config.udpSockPort));
        return chnl;
    }

    private BindServer startUdpServer() throws IOException {
        BindServer udp = BindServer.createUDP(new InetSocketAddress(config.bindInetAddress, config.udpPort));
        loop.addServer(udp, null, nodeExistenceServerHandler);
        return udp;
    }

    private BindServer startTcpServer() throws IOException {
        BindServer tcp = BindServer.create(new InetSocketAddress(config.bindInetAddress, config.tcpPort));
        loop.addServer(tcp, null, nodeDataServerHandler);
        return tcp;
    }

    private void recordNode(Node node) {
        String groupServerName = buildGroupServerName(node);
        if (!nodes.containsKey(groupServerName)) {
            Logger.info(LogType.DISCOVERY_EVENT, "recording new node: " + node);
            try {
                hcGroup.add(groupServerName, node.address, new InetSocketAddress(node.address, node.tcpPort), config.bindInetAddress, 10);
            } catch (AlreadyExistException e) {
                Logger.shouldNotHappen("the node already exist in hcGroup " + groupServerName);
            }
            node.healthy = false; // default is false
            nodes.put(groupServerName, new NodeDetach(node));
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

    private void sendBuffer(ByteBuffer buffer, InetSocketAddress sockAddr) throws IOException {
        int pos = buffer.position();
        int lim = buffer.limit();
        int res = udpSock.send(buffer, sockAddr);
        assert Logger.lowLevelDebug("udpSock.send wrote " + res + " bytes");
        buffer.position(pos).limit(lim);
    }

    private void informNode(Node node) {
        try {
            sendBuffer(informBuffer, new InetSocketAddress(node.inetAddress, node.udpPort));
        } catch (IOException e) {
            assert Logger.lowLevelDebug("send inform message got error " + e);
        }
    }

    private void startSearch() {
        if (closed)
            return; // do nothing if it's already closed

        if (intoInterval && !isInInterval) {
            intoInterval = false; // reset the flag
            isInInterval = true;
            int delay = nodes.size() == 1 /*1 means the node itself*/
                ? config.timeoutConfig.intervalWhenNotJoined
                : config.timeoutConfig.intervalWhenJoined;
            loop.getSelectorEventLoop().delay(delay, this::startSearch);
            return;
        }
        isInInterval = false;
        intoInterval = false;

        InetSocketAddress sockAddr = nextSearch();
        if (sockAddr != null) {
            try {
                sendBuffer(searchBuffer, sockAddr);
            } catch (IOException e) {
                assert Logger.lowLevelDebug("send search message got error " + e);
            }
        }

        int delay = nodes.size() == 1 /*1 means the node itself*/
            ? config.timeoutConfig.delayWhenNotJoined
            : config.timeoutConfig.delayWhenJoined;
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
        // use heap buffer here
        // because the connection will be terminated when gets data
        // connection won't last long
        RingBuffer input = RingBuffer.allocate(16384);
        RingBuffer output = RingBuffer.allocate(16384);
        ClientConnection clientConnection;
        try {
            clientConnection = ClientConnection.create(new InetSocketAddress(target.address, target.tcpPort), config.bindInetAddress, input, output);
        } catch (IOException e) {
            Logger.error(LogType.CONN_ERROR, "create client conn failed", e);
            return;
        }
        try {
            loop.addClientConnection(clientConnection, null, new NodeDataHandler());
        } catch (IOException e) {
            Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "add client connection failed", e);
        }
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
        StringBuilder strHexString = new StringBuilder();
        for (byte aByte : bytes) {
            String hex = Integer.toHexString(Utils.positive(aByte));
            if (hex.length() == 1) {
                strHexString.append('0');
            }
            strHexString.append(hex);
        }
        return strHexString.toString();
    }

    private void calcSearchBuffer() {
        searchBuffer.position(0).limit(searchBuffer.capacity());
        // build the message
        Object[] message = {
            1 /*version*/,
            "search" /*type*/,
            nodeName,
            config.udpPort,
            config.tcpPort,
            hash,
        };
        byte[] bytes = Serializer.from(message);
        searchBuffer.put(bytes);
        searchBuffer.flip();
    }

    private void calcInformBuffer() {
        informBuffer.position(0).limit(informBuffer.capacity());
        // build the message
        Object[] message = {
            1 /*version*/,
            "inform" /*type*/,
            nodeName,
            config.udpPort,
            config.tcpPort,
            hash,
        };
        byte[] bytes = Serializer.from(message);
        informBuffer.put(bytes);
        informBuffer.flip();
    }

    public List<Node> getNodes() {
        return nodes.values().stream().map(n -> n.node).collect(Collectors.toList());
    }

    public void addNodeListener(NodeListener lsn) {
        nodeListeners.add(lsn);
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
        udpServer.close();
        // send `leave` message to all nodes
        Object[] messageToSend = {
            1 /*version*/,
            "leave" /*type*/,
            nodeName,
            config.udpPort,
            config.tcpPort,
            "",
        };
        byte[] bytesToSend = Serializer.from(messageToSend);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bytesToSend.length);
        byteBuffer.put(bytesToSend);
        byteBuffer.flip();
        leave(byteBuffer, nodes.values().iterator(), new Callback<Void, NoException>() {
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
            if (leaveMsg instanceof DirectBuffer) { // do release the direct memory
                ((DirectBuffer) leaveMsg).cleaner().clean();
            }
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
        try {
            sendBuffer(leaveMsg, new InetSocketAddress(n.node.address, n.node.udpPort));
        } catch (IOException e) {
            // ignore error
            assert Logger.lowLevelDebug("udp sock send leave message failed " + e);
        }
        loop.getSelectorEventLoop().delay(config.timeoutConfig.ppsLimitWhenNotJoined, () -> leave(leaveMsg, nodes, cb));
    }

    private void releaseAfterLeave(Callback<Void, NoException> cb) {
        tcpServer.close();
        try {
            eventLoopGroup.remove("EventLoop:" + nodeName);
        } catch (NotFoundException e) {
            Logger.shouldNotHappen("removing event loop failed", e);
            // we ignore the error because it's closing
        }
        try {
            udpSock.close();
        } catch (IOException e) {
            // ignore, we can do nothing about it
            Logger.error(LogType.UNEXPECTED, "closing the udpSock failed", e);
        }

        // then release the buffers
        if (searchBuffer instanceof DirectBuffer) {
            ((DirectBuffer) searchBuffer).cleaner().clean();
        }
        if (informBuffer instanceof DirectBuffer) {
            ((DirectBuffer) informBuffer).cleaner().clean();
        }

        // callback
        cb.succeeded(null);
    }
}
