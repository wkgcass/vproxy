package vproxybase.selector.wrap.streamed;

import vfd.EventSet;
import vfd.IP;
import vfd.IPPort;
import vfd.SocketFD;
import vmirror.MirrorDataFactory;
import vproxybase.Config;
import vproxybase.GlobalInspection;
import vproxybase.prometheus.GaugeF;
import vproxybase.selector.Handler;
import vproxybase.selector.HandlerContext;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.selector.TimerEvent;
import vproxybase.selector.wrap.arqudp.ArqUDPSocketFD;
import vproxybase.util.ByteArray;
import vproxybase.util.Consts;
import vproxybase.util.LogType;
import vproxybase.util.Logger;
import vproxybase.util.nio.ByteArrayChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

@SuppressWarnings("UnusedReturnValue")
public abstract class StreamedFDHandler implements Handler<SocketFD> {
    private static final String streamed_fd_handler_fd_map_count_current = "streamed_fd_handler_fd_map_count_current";

    static {
        GlobalInspection.getInstance().registerHelpMessage(streamed_fd_handler_fd_map_count_current,
            "The current count of fd map in streamed fd handler");
    }

    private ArqUDPSocketFD fd;
    private SelectorEventLoop loop;
    private final boolean client;

    private Consumer<ArqUDPSocketFD> readyCallback;
    private Consumer<ArqUDPSocketFD> invalidCallback;
    private Predicate<StreamedFD> acceptCallback;

    private TimerEvent handshakeTimeout = null;
    private final Map<Integer, StreamedFD> fdMap = new HashMap<>();

    private GaugeF statisticsFdMapCount;

    protected StreamedFDHandler(boolean client) {
        this.client = client;
    }

    @SuppressWarnings("ReplaceNullCheck")
    @MethodForFDs
    final void init(ArqUDPSocketFD fd,
                    SelectorEventLoop loop,
                    Consumer<ArqUDPSocketFD> readyCallback,
                    Consumer<ArqUDPSocketFD> invalidCallback,
                    Predicate<StreamedFD> acceptCallback) {
        this.fd = fd;
        this.loop = loop;
        if (readyCallback != null) {
            this.readyCallback = readyCallback;
        } else {
            this.readyCallback = x -> {
            };
        }
        if (invalidCallback != null) {
            this.invalidCallback = invalidCallback;
        } else {
            this.invalidCallback = x -> {
            };
        }
        if (acceptCallback != null) {
            this.acceptCallback = acceptCallback;
        } else {
            this.acceptCallback = x -> false;
        }
        try {
            this.statisticsFdMapCount = GlobalInspection.getInstance().addMetric(streamed_fd_handler_fd_map_count_current,
                Map.of("base_remote", fd.getRemoteAddress().formatToIPPortString(),
                    "base_local", fd.getLocalAddress().formatToIPPortString()),
                (m, l) -> new GaugeF(m, l, () -> (long) fdMap.size()));
        } catch (IOException e) {
            Logger.shouldNotHappen("get remove address or local address from arq udp socket fd failed", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public final void accept(HandlerContext<SocketFD> ctx) {
        Logger.shouldNotHappen("`accept` should not fire here");
    }

    abstract protected ByteArray errorMessage(IOException err);

    abstract protected ByteArray clientHandshakeMessage();

    private void fail(IOException t) {
        fail(t, true);
    }

    private boolean isFailed = false;

    private void fail(IOException t, boolean sendRst) {
        if (isFailed) {
            return;
        }
        isFailed = true;
        fdMap.values().forEach(fd -> fd.setState(StreamedFD.State.dead));
        Logger.error(LogType.CONN_ERROR, "the stream thrown exception", t);
        if (sendRst) {
            ByteArray err = errorMessage(t);
            // add to the most front of the sending queue
            state = -1; // update state to invalid state
            pushMessageToWrite(err);
            // wait for 1 second before alerting the upper level code
            loop.delay(1_000, () -> invalidCallback.accept(fd));
        } else {
            invalidCallback.accept(fd);
        }
    }

    @MethodForImplementation
    protected final void errorReceived(IOException err) {
        fail(err, false);
    }

    private int state = 0;
    // for client
    // 0: initial, handshake not sent (W)
    // 1: handshake sent, no received (R)
    // 2: handshake received and connected (N)
    // for server
    // 0: initial, handshake not received (R)
    // 1: handshake received, not sent (W)
    // 2: handshake sent and connected (R)

    private ByteArrayChannel cachedMessageToWrite;
    private byte[] cachedMessageArrayToWrite;

    private int write() {
        if (cachedMessageArrayToWrite == null) {
            cachedMessageArrayToWrite = cachedMessageToWrite.getArray().toJavaArray();
        }
        int n;
        try {
            n = fd.write(ByteBuffer.wrap(cachedMessageArrayToWrite, cachedMessageToWrite.getReadOff(), cachedMessageToWrite.used()));
        } catch (IOException e) {
            fail(e);
            return -1;
        }
        if (n < 0) {
            fail(new IOException(fd + ".write returns " + n));
            return -1;
        }
        if (n == 0) {
            // nothing wrote
            return 0;
        }
        if (n == cachedMessageToWrite.used()) {
            // everything wrote
            cachedMessageToWrite = null;
            cachedMessageArrayToWrite = null;
            return 1;
        }
        // wrote some
        cachedMessageToWrite.skip(n);
        return 0;
    }

    @Override
    public void connected(HandlerContext<SocketFD> ctx) {
        if (!client) {
            Logger.shouldNotHappen("server should not fire `connected` event");
            return;
        }
        // set a timer for handshaking (client)
        handshakeTimeout = loop.delay(5_000, () -> fail(new IOException("handshake timed out")));

        // need to send handshake message
        ByteArray msg = clientHandshakeMessage();
        cachedMessageToWrite = ByteArrayChannel.fromFull(msg);
        int n = write();
        if (n < 0) {
            return;
        }
        if (n == 0) {
            // want to write more data
            // it should already be watching writable event
            // so nothing to be done here
            return;
        }
        // everything wrote
        state = 1;
        // want to receive handshake, so unwatch writable and watch readable
        unwatchWritable("connected");
        watchReadable();
    }

    private final ByteBuffer readBuf = ByteBuffer.allocate(1024);

    private ByteArray read0() {
        ByteArray array = null;
        while (true) {
            int n;
            try {
                n = fd.read(readBuf);
            } catch (IOException e) {
                if (array == null) {
                    fail(e);
                    return null;
                } else {
                    return array;
                }
            }
            if (n < 0) {
                if (array == null) {
                    fail(new IOException(fd + ".read returns -1"));
                    return null;
                } else {
                    return array;
                }
            }
            if (n == 0) {
                // everything read
                break;
            }
            ByteArray a = ByteArray.from(readBuf.array()).sub(0, readBuf.position()).copy();
            readBuf.limit(readBuf.capacity()).position(0);
            if (array == null) {
                array = a;
            } else {
                array = array.concat(a);
            }
        }
        return array;
    }

    private ByteArray cachedReceivedMessage = null;

    private void read() {
        ByteArray arr = read0();
        if (arr == null) {
            return;
        }
        if (cachedReceivedMessage == null) {
            cachedReceivedMessage = arr;
        } else {
            cachedReceivedMessage = cachedReceivedMessage.concat(arr);
        }
    }

    private void reduceReceivedMessage(int consumedBytes) {
        if (consumedBytes == cachedReceivedMessage.length()) {
            // everything fed
            cachedReceivedMessage = null;
        } else {
            cachedReceivedMessage = cachedReceivedMessage.sub(consumedBytes, cachedReceivedMessage.length() - consumedBytes);
        }
    }

    abstract protected /*unsigned*/ int clientReceiveHandshakeMessage(ByteArray array) throws IOException;

    abstract protected /*unsigned*/ int serverReceiveHandshakeMessage(ByteArray array) throws IOException;

    abstract protected /*unsigned*/ int clientFeed(ByteArray array) throws IOException;

    abstract protected /*unsigned*/ int serverFeed(ByteArray array) throws IOException;

    abstract protected ByteArray serverHandshakeMessage();

    private void handshakeDone() {
        handshakeTimeout.cancel();
        state = 2;
        readyCallback.accept(fd);
    }

    private void clientReadable(@SuppressWarnings("unused") HandlerContext<SocketFD> ctx) {
        if (state == 0) {
            Logger.shouldNotHappen("client readable should not see state == 0");
        } else if (state == 1) {
            int n;
            try {
                n = clientReceiveHandshakeMessage(cachedReceivedMessage);
            } catch (IOException e) {
                fail(e);
                return;
            }
            if (n == 0) {
                // message not complete
                return;
            }
            reduceReceivedMessage(n);
            // connection done
            handshakeDone();
        }
        // else will not be called
    }

    private void serverReadable(@SuppressWarnings("unused") HandlerContext<SocketFD> ctx) {
        if (state == 0) {
            int n;
            try {
                n = serverReceiveHandshakeMessage(cachedReceivedMessage);
            } catch (IOException e) {
                fail(e);
                return;
            }
            if (n == 0) {
                // message not complete
                return;
            }
            reduceReceivedMessage(n);
            // handshake read
            state = 1;

            // set a timer for handshaking (server)
            handshakeTimeout = loop.delay(5_000, () -> fail(new IOException("handshake timed out")));

            // need to send handshake message
            ByteArray msg = serverHandshakeMessage();
            cachedMessageToWrite = ByteArrayChannel.fromFull(msg);
            n = write();
            if (n < 0) {
                return;
            }
            if (n == 0) {
                // need to write more data
                watchWritable("serverReadable");
                return;
            }
            // handshake message sent
            handshakeDone();
        } else if (state == 1) {
            Logger.error(LogType.INVALID_EXTERNAL_DATA, "server should not fire readable in state = " + state);
        }
        // else will not be called
    }

    private long lastReadableTimestamp = 0L;

    @Override
    public void readable(HandlerContext<SocketFD> ctx) {
        read();
        if (cachedReceivedMessage == null) {
            // nothing read
            return;
        }
        lastReadableTimestamp = Config.currentTimestamp;
        if (state == 0 || state == 1) {
            if (client) {
                clientReadable(ctx);
            } else {
                serverReadable(ctx);
            }
            return;
        }
        assert state == 2 || state == -1;

        while (true) {
            int n;
            try {
                if (client) {
                    n = clientFeed(cachedReceivedMessage);
                } else {
                    n = serverFeed(cachedReceivedMessage);
                }
            } catch (IOException e) {
                fail(e);
                return;
            }
            if (n == 0) {
                // nothing fed
                return;
            }
            if (cachedReceivedMessage == null) {
                // it may be set to null if called `fail(...)`
                return;
            }
            reduceReceivedMessage(n);
            if (cachedReceivedMessage == null) {
                return;
            }
        }
    }

    private void clientWritable(@SuppressWarnings("unused") HandlerContext<SocketFD> ctx) {
        if (state == 0) {
            // everything wrote, which means handshake sent
            // change state
            state = 1;
            // want to receive handshake, so unwatch writable and watch readable
            unwatchWritable("clientWritable");
            watchReadable();
        } else if (state == 1) {
            Logger.shouldNotHappen("client should not fire writable in state = " + state);
        }
        // else will not be called
    }

    private void serverWritable(@SuppressWarnings("unused") HandlerContext<SocketFD> ctx) {
        if (state == 0) {
            Logger.shouldNotHappen("server should not fire writable in state = " + state);
        } else if (state == 1) {
            // everything wrote
            // update state
            handshakeDone();
            // add readable and remove writable
            unwatchWritable("serverWritable");
            // no need to add op_read because it's unnecessary to be removed for servers
        }
        // else will not be called
    }

    private final Deque<ByteArray> messagesToWrite = new LinkedList<>();

    int writableLen() {
        int hold = 0;
        if (cachedMessageToWrite != null) {
            hold += cachedMessageToWrite.used();
        }
        for (ByteArray b : messagesToWrite) {
            hold += b.length();
        }

        int canWrite = fd.writableLen();
        if (canWrite > hold) {
            return canWrite - hold;
        } else {
            return 0;
        }
    }

    private void checkAndCancelWritable() {
        if (writableLen() <= 0) {
            fdMap.values().forEach(StreamedFD::cancelWritable);
            // also we should watch when we can write
            watchWritable("checkAndCancelWritable");
        }
    }

    private void checkAndSetWritableForEstablished() {
        if (writableLen() > 0) {
            fdMap.values().forEach(fd -> {
                if (fd.getState() == StreamedFD.State.established) {
                    fd.setWritable();
                }
            });
            // no need to watch inside writable because this writable is set
            unwatchWritable("writable");
        }
    }

    @Override
    public void writable(HandlerContext<SocketFD> ctx) {
        while (true) {
            if (cachedMessageToWrite != null) {
                int n = write();
                if (n < 0) {
                    // failed
                    return;
                } else if (n == 0) {
                    // still got data to send
                    // so the inside fd is not writable, we cancel writable event for the streamedFD
                    // wait for the next writable event
                    checkAndCancelWritable();
                    return;
                }
                // fall through
            }
            if (state == 0 || state == 1) {
                if (client) {
                    clientWritable(ctx);
                } else {
                    serverWritable(ctx);
                }
                return;
            }
            assert state == 2 || state == -1;

            // something wrote, so check whether writable and
            // set writable for all fds that's established
            checkAndSetWritableForEstablished();
            checkAndCancelWritable();
            // note: after writing a packet into arq-udp, the writableLen might become smaller
            // the free space we use to calculate is based on mtu and packet overhead
            // but the fed packet is usually smaller than mtu, so free space is consumed,
            // writableLen will be smaller

            ByteArray arr = messagesToWrite.poll();
            if (arr == null) {
                return;
            }
            cachedMessageToWrite = ByteArrayChannel.fromFull(arr);
        }
    }

    @Override
    public final void removed(HandlerContext<SocketFD> ctx) {
        Logger.warn(LogType.IMPROPER_USE, "fd " + fd + " removed from loop, we have to invalid the fd");
        fail(new IOException("arq udp socket removed from loop: " + fd));
    }

    @MethodForImplementation
    protected final boolean hasStream(int streamId) {
        return fdMap.containsKey(streamId);
    }

    private boolean newStream(int streamId) {
        if (hasStream(streamId)) {
            assert Logger.lowLevelDebug("trying to add existing fd to fdMap: " + fd);
            return false;
        }
        IP virtualAddress;
        {
            byte b1 = (byte) ((streamId >> 24) & 0xff);
            byte b2 = (byte) ((streamId >> 16) & 0xff);
            byte b3 = (byte) ((streamId >> 8) & 0xff);
            byte b4 = (byte) (streamId & 0xff);
            virtualAddress = IP.from(new byte[]{b1, b2, b3, b4});
        }
        int virtualPort;
        {
            try {
                if (client) {
                    virtualPort = fd.getLocalAddress().getPort();
                } else {
                    virtualPort = fd.getRemoteAddress().getPort();
                }
            } catch (IOException e) {
                fail(e);
                return false;
            }
        }
        IPPort virtual = new IPPort(virtualAddress, virtualPort);
        IPPort local;
        IPPort remote;
        if (client) {
            local = virtual;
            try {
                remote = fd.getRemoteAddress();
            } catch (IOException e) {
                fail(e);
                return false;
            }
        } else {
            try {
                local = fd.getLocalAddress();
            } catch (IOException e) {
                fail(e);
                return false;
            }
            remote = virtual;
        }
        StreamedFD sfd = new StreamedFD(streamId, fd, loop.selector, local, remote, this, client);
        fdMap.put(streamId, sfd);
        assert Logger.lowLevelDebug("adding new fd to fdMap: " + fd);
        return true;
    }

    final void removeStreamedFD(StreamedFD fd) {
        fdMap.values().remove(fd);
    }

    @MethodForImplementation
    protected final boolean removeStream(int streamId) {
        StreamedFD sfd = fdMap.remove(streamId);
        if (sfd == null) {
            assert Logger.lowLevelDebug("trying to remove non-exist stream from fdMap: " + streamId);
            return false;
        } else {
            assert Logger.lowLevelDebug("removing fd from fdMap: " + streamId);
            return true;
        }
    }

    @MethodForImplementation
    protected final boolean dataForStream(int streamId, ByteArray data) {
        if (!hasStream(streamId)) {
            assert Logger.lowLevelDebug("calling dataForStream on non-existing stram: " + streamId);
            return false;
        }
        StreamedFD sfd = fdMap.get(streamId);
        assert sfd != null;
        sfd.inputData(data);
        return true;
    }

    abstract protected ByteArray formatSYNACK(int streamId);

    @MethodForImplementation
    protected final boolean synReceived(int streamId) {
        if (client) {
            if (!hasStream(streamId)) {
                assert Logger.lowLevelDebug("client: calling synReceived on non-existing stream: " + streamId);
                return false;
            }
        } else {
            if (hasStream(streamId)) {
                assert Logger.lowLevelDebug("server: calling synReceived on existing stream: " + streamId);
                return false;
            }
            if (!accept(streamId)) {
                String err = "accepting " + streamId + " failed in arq udp socket " + fd;
                Logger.error(LogType.IMPROPER_USE, err);
                fail(new IOException(err));
                return false;
            }
        }
        StreamedFD sfd = fdMap.get(streamId);
        assert sfd != null;

        if (sfd.readingMirrorDataFactory.isEnabled()) {
            mirror(sfd, false, Consts.TCP_FLAGS_SYN, ByteArray.allocate(0));
        }

        if (client) {
            if (sfd.getState() != StreamedFD.State.syn_sent) {
                assert Logger.lowLevelDebug("fd: " + sfd + " state = " + sfd.getState() + " != " + StreamedFD.State.syn_sent);
                return false;
            }
        }
        sfd.setState(StreamedFD.State.established);
        if (!client) {
            // need to send syn-ack
            addMessageToWrite(formatSYNACK(streamId));
        }
        return true;
    }

    @MethodForImplementation
    protected final boolean finReceived(int streamId) {
        if (!hasStream(streamId)) {
            assert Logger.lowLevelDebug("calling finReceived on non-existing stream: " + streamId);
            return false;
        }
        StreamedFD sfd = fdMap.get(streamId);
        assert sfd != null;

        if (sfd.readingMirrorDataFactory.isEnabled()) {
            mirror(sfd, false, Consts.TCP_FLAGS_FIN, ByteArray.allocate(0));
        }

        if (sfd.getState() == StreamedFD.State.none || sfd.getState() == StreamedFD.State.real_closed) {
            assert Logger.lowLevelDebug("fd: " + sfd + " state = " + sfd.getState());
            return false;
        }
        if (sfd.getState() == StreamedFD.State.dead) {
            Logger.shouldNotHappen("closed streams should be removed from fdMap");
            return false; // already closed
        }
        if (sfd.getState() == StreamedFD.State.established) {
            sfd.setState(StreamedFD.State.fin_recv);
        } else {
            sfd.setState(StreamedFD.State.dead);
            removeStream(streamId);
        }
        return true;
    }

    @MethodForImplementation
    protected final boolean rstReceived(int streamId) {
        if (!hasStream(streamId)) {
            assert Logger.lowLevelDebug("calling rstReceived on non-existing stream: " + streamId);
            return false;
        }
        StreamedFD sfd = fdMap.get(streamId);
        assert sfd != null;

        if (sfd.readingMirrorDataFactory.isEnabled()) {
            mirror(sfd, false, Consts.TCP_FLAGS_RST, ByteArray.allocate(0));
        }

        if (sfd.getState() == StreamedFD.State.dead || sfd.getState() == StreamedFD.State.real_closed) {
            return false; // already closed
        }
        sfd.setState(StreamedFD.State.dead);
        sfd.setRst();
        // need to send RST back
        addMessageToWrite(formatRST(streamId));
        return true;
    }

    private void watchReadable() {
        assert Logger.lowLevelDebug("watch readable on fd " + fd + " for streamed fds");
        loop.addOps(fd, EventSet.read());
    }

    private void watchWritable(String reason) {
        assert Logger.lowLevelDebug("watch writable on fd " + fd + " for streamed fds, " + reason);
        loop.addOps(fd, EventSet.write());
    }

    private void unwatchWritable(String reason) {
        assert Logger.lowLevelDebug("unwatch writable on fd " + fd + " for streamed fds, " + reason);
        loop.rmOps(fd, EventSet.write());
    }

    private void addMessageToWrite(ByteArray arr) {
        if (arr == null || arr.length() == 0) {
            return;
        }
        assert Logger.lowLevelNetDebug("addMessageToWrite");
        assert Logger.lowLevelNetDebugPrintBytes(arr.toJavaArray());
        messagesToWrite.add(arr);
        watchWritable("addMessageToWrite");
        checkAndCancelWritable();
    }

    private void pushMessageToWrite(ByteArray arr) {
        if (arr == null || arr.length() == 0) {
            return;
        }
        messagesToWrite.push(arr);
        watchWritable("pushMessageToWrite");
        checkAndCancelWritable();
    }

    abstract protected ByteArray formatPSH(int streamId, ByteArray data);

    @MethodForStreamedFD
    public final int send(StreamedFD fd, ByteBuffer src) throws IOException {
        if (!fdMap.containsValue(fd)) {
            throw new IOException("fdMap does not contain fd " + fd);
        }
        if (fd.getState() != StreamedFD.State.syn_sent
            && fd.getState() != StreamedFD.State.established
            && fd.getState() != StreamedFD.State.fin_recv) {
            throw new IOException(fd + " is not connected: " + fd.getState());
        }
        if (src.limit() == src.position()) {
            // nothing to be sent
            assert Logger.lowLevelDebug("nothing to be sent, return 0");
            return 0;
        }
        int len = Math.min(writableLen(), src.limit() - src.position());
        if (len <= 0) {
            // cannot write
            assert Logger.lowLevelDebug("cannot write, return 0");
            checkAndCancelWritable();
            return 0;
        }
        byte[] data = new byte[len];
        src.get(data);
        // append to the last of the queue
        addMessageToWrite(formatPSH(fd.streamId, ByteArray.from(data)));
        checkAndCancelWritable();
        return len;
    }

    abstract protected ByteArray formatFIN(int streamId);

    @MethodForStreamedFD
    public final void sendFIN(StreamedFD fd) throws IOException {
        if (!fdMap.containsValue(fd)) {
            throw new IOException("fdMap does not contain fd " + fd);
        }
        if (fd.getState() == StreamedFD.State.dead) {
            throw new IOException(fd + " is already closed");
        }
        // append to the last of the queue
        addMessageToWrite(formatFIN(fd.streamId));

        switch (fd.getState()) {
            case none:
            case syn_sent:
            case fin_recv:
                fd.setState(StreamedFD.State.dead);
                removeStream(fd.streamId);
                break;
            case established:
                fd.setState(StreamedFD.State.fin_sent);
                break;
            case fin_sent:
            case real_closed:
                // nothing to do
                break;
        }

        if (fd.writingMirrorDataFactory.isEnabled()) {
            mirror(fd, true, Consts.TCP_FLAGS_FIN, ByteArray.allocate(0));
        }
    }

    private final Map<Long, TimerEvent> keepaliveTimeouts = new HashMap<>();
    private long nextKeepaliveId = 0L;

    abstract protected ByteArray keepaliveMessage(long keepaliveId, boolean isAck);

    @MethodForImplementation
    protected final void keepaliveReceived(long kId, boolean isAck) {
        if (isAck) {
            // cancel the timer
            TimerEvent te = keepaliveTimeouts.remove(kId);
            if (te == null) {
                Logger.warn(LogType.ALERT, "the timer is already canceled or missing 0x" + Long.toHexString(kId) + " in " + fd);
                return;
            }
            if (Config.probe.contains("streamed-arq-udp-event")) {
                Logger.probe("receiving keepalive ack message 0x" + Long.toHexString(kId) + " on arq udp socket " + fd);
            }
            ++keepaliveSuccessCount;
            if (keepaliveSuccessCount > KEEPALIVE_MAX_SUCCESS_COUNT) {
                keepaliveSuccessCount = KEEPALIVE_MAX_SUCCESS_COUNT;
            }
            te.cancel();
        } else {
            // it's keepalive request, do respond
            // add at the most front of the queue
            if (Config.probe.contains("streamed-arq-udp-event")) {
                Logger.probe("receiving remote keepalive message 0x" + Long.toHexString(kId) + " on arq udp socket " + fd);
            }
            pushMessageToWrite(keepaliveMessage(kId, true));
        }
    }

    private int keepaliveSuccessCount = 0;
    private static final int KEEPALIVE_MAX_SUCCESS_COUNT = 2;

    @MethodForFDs
    final void keepalive() {
        // check whether it's connected
        if (state != 2) {
            Logger.warn(LogType.ALERT, "handshake is not done while keepalive event is triggered");
            fail(new IOException("handshaking timeout"), false);
            return;
        }
        // only send keepalive message if it's in idle
        if (cachedMessageToWrite == null && messagesToWrite.isEmpty() && (Config.currentTimestamp - lastReadableTimestamp) > 5_000) {
            // send keepalive message
            long kId = ++nextKeepaliveId;
            // record with a timeout
            keepaliveTimeouts.put(kId, loop.delay(5_000, () -> {
                if (keepaliveSuccessCount <= 0) {
                    fail(new IOException("keepalive response timeout"));
                }
                --keepaliveSuccessCount;
                keepaliveTimeouts.remove(kId);
            }));
            // add to the first of the queue
            pushMessageToWrite(keepaliveMessage(kId, false));
            if (Config.probe.contains("streamed-arq-udp-event")) {
                Logger.probe("keepalive message 0x" + Long.toHexString(kId) + " sent on arq udp socket " + fd);
            }
        }
        // do probe
        // check recorded connections
        if (Config.probe.contains("streamed-arq-udp-record")) {
            try {
                Logger.probe("isClient=" + client + ", state=" + state);
                IPPort arqSockLocal = fd.getLocalAddress();
                String arqSockLocalStr = arqSockLocal.formatToIPPortString();
                for (Map.Entry<Integer, StreamedFD> entry : fdMap.entrySet()) {
                    int streamId = entry.getKey();
                    StreamedFD sfd = entry.getValue();
                    String local = sfd.getLocalAddress().formatToIPPortString();
                    String remote = sfd.getRemoteAddress().formatToIPPortString();

                    Logger.probe(
                        "record: " + arqSockLocalStr
                            + " -> " + streamId
                            + " -> " + local
                            + " -> " + remote
                            + " [" + sfd.getState().probeColor + sfd.getState().toString().toUpperCase() + Logger.RESET_COLOR + "]"
                    );
                }
            } catch (Throwable t) {
                Logger.shouldNotHappen("got exception when probing", t);
            }
        }
    }

    abstract protected int nextStreamId();

    @MethodForFDs
    final StreamedFD clientOpen() throws IOException {
        if (!client) {
            throw new UnsupportedOperationException();
        }
        if (state != 2) {
            throw new IOException("not ready");
        }
        int streamId = nextStreamId();
        if (!newStream(streamId)) {
            Logger.error(LogType.IMPROPER_USE, "streamId " + streamId + " already exists");
            throw new IOException("streamId " + streamId + " already exists");
        }
        return fdMap.get(streamId);
    }

    abstract protected ByteArray formatSYN(int streamId);

    @MethodForStreamedFD
    public final void sendSYN(StreamedFD fd) throws IOException {
        if (!fdMap.containsValue(fd)) {
            throw new IOException("fdMap does not contain fd " + fd);
        }
        if (fd.getState() != StreamedFD.State.none) {
            throw new IOException("syn of " + fd + " is already sent");
        }
        // append syn to the last of the queue
        addMessageToWrite(formatSYN(fd.streamId));
        fd.setState(StreamedFD.State.syn_sent);

        if (fd.writingMirrorDataFactory.isEnabled()) {
            mirror(fd, true, Consts.TCP_FLAGS_SYN, ByteArray.allocate(0));
        }
    }

    abstract protected ByteArray formatRST(int streamId);

    @MethodForStreamedFD
    public final void sendRST(StreamedFD fd) throws IOException {
        if (!fdMap.containsValue(fd)) {
            throw new IOException("fdMap does not contain fd " + fd);
        }
        if (fd.getState() == StreamedFD.State.dead) {
            throw new IOException(fd + " is already closed");
        }
        // append rst to the last of the queue
        addMessageToWrite(formatRST(fd.streamId));
        if (fd.getState() != StreamedFD.State.real_closed) {
            fd.setState(StreamedFD.State.dead);
        }
        fdMap.values().remove(fd);

        if (fd.writingMirrorDataFactory.isEnabled()) {
            mirror(fd, true, Consts.TCP_FLAGS_RST, ByteArray.allocate(0));
        }
    }

    private boolean accept(int streamId) {
        if (client) {
            throw new UnsupportedOperationException();
        }
        boolean r = newStream(streamId);
        if (!r) {
            return false;
        }
        var fd = fdMap.get(streamId);
        assert fd != null;
        r = acceptCallback.test(fd);
        if (!r) {
            Logger.warn(LogType.IMPROPER_USE, "acceptCallback(" + fd + ") returns false");
            return false;
        }
        return true;
    }

    @MethodForFDs
    final void clear() {
        for (StreamedFD streamedFD : fdMap.values()) {
            streamedFD.setState(StreamedFD.State.dead);
        }
        for (TimerEvent e : keepaliveTimeouts.values()) {
            e.cancel();
        }
        cachedMessageToWrite = null;
        cachedReceivedMessage = null;
        messagesToWrite.clear();
        fdMap.clear();
        keepaliveTimeouts.clear();
        if (statisticsFdMapCount != null) {
            GlobalInspection.getInstance().removeMetric(statisticsFdMapCount);
            statisticsFdMapCount = null;
        }
    }

    @MethodForStreamedFD
    public void mirror(StreamedFD fd, boolean isSend, byte flags, ByteArray data) {
        MirrorDataFactory factory;
        if (isSend) {
            factory = fd.writingMirrorDataFactory;
        } else {
            factory = fd.readingMirrorDataFactory;
        }

        if (!factory.isEnabled()) {
            return;
        }

        // build meta
        String meta = "c=" + (client ? "1" : "0") +
            ";" +
            "s=" + fd.getState().name() +
            ";" +
            "wl=" + writableLen() +
            ";";

        factory.build()
            .setMeta(meta)
            .setFlags(flags)
            .setData(data)
            .setTransportLayerProtocol("UDP")
            .mirror();
    }
}
