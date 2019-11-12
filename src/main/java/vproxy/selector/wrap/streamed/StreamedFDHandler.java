package vproxy.selector.wrap.streamed;

import vfd.EventSet;
import vfd.SocketFD;
import vproxy.selector.Handler;
import vproxy.selector.HandlerContext;
import vproxy.selector.SelectorEventLoop;
import vproxy.selector.wrap.WrappedSelector;
import vproxy.selector.wrap.arqudp.ArqUDPSocketFD;
import vproxy.util.ByteArray;
import vproxy.util.LogType;
import vproxy.util.Logger;
import vproxy.util.nio.ByteArrayChannel;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

@SuppressWarnings("UnusedReturnValue")
public abstract class StreamedFDHandler implements Handler<SocketFD> {
    private ArqUDPSocketFD fd;
    private SelectorEventLoop loop;
    private final boolean client;

    private Consumer<ArqUDPSocketFD> readyCallback;
    private Consumer<ArqUDPSocketFD> invalidCallback;
    private Predicate<StreamedFD> acceptCallback;

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
    }

    @Override
    public final void accept(HandlerContext<SocketFD> ctx) {
        Logger.shouldNotHappen("`accept` should not fire here");
    }

    abstract protected ByteArray clientHandshakeMessage();

    private void fail(IOException t) {
        Logger.error(LogType.CONN_ERROR, "the stream thrown exception", t);
        invalidCallback.accept(fd);
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

    private int write() {
        int n;
        try {
            n = fd.write(ByteBuffer.wrap(cachedMessageToWrite.getArray().toJavaArray()));
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
        loop.rmOps(fd, EventSet.write());
        loop.addOps(fd, EventSet.read());
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
            state = 2;
            readyCallback.accept(fd);
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

            // need to send handshake message
            ByteArray msg = serverHandshakeMessage();
            cachedMessageToWrite = ByteArrayChannel.fromFull(msg);
            n = write();
            if (n < 0) {
                return;
            }
            if (n == 0) {
                // need to write more data
                loop.addOps(fd, EventSet.write());
                return;
            }
            // handshake message sent
            state = 2;
            readyCallback.accept(fd);
        } else if (state == 1) {
            Logger.error(LogType.INVALID_EXTERNAL_DATA, "server should not fire readable in state = " + state);
        }
        // else will not be called
    }

    @Override
    public void readable(HandlerContext<SocketFD> ctx) {
        read();
        if (cachedReceivedMessage == null) {
            // nothing read
            return;
        }
        if (state == 0 || state == 1) {
            if (client) {
                clientReadable(ctx);
            } else {
                serverReadable(ctx);
            }
            return;
        }
        assert state == 2;

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
            loop.rmOps(fd, EventSet.write());
            loop.addOps(fd, EventSet.read());
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
            state = 2;
            // add readable and remove writable
            loop.rmOps(fd, EventSet.write());
            // no need to add op_read because it's unnecessary to be removed for servers
        }
        // else will not be called
    }

    private final Deque<ByteArray> messagesToWrite = new LinkedList<>();

    @Override
    public void writable(HandlerContext<SocketFD> ctx) {
        while (true) {
            if (cachedMessageToWrite != null) {
                int n = write();
                if (n <= 0) {
                    // failed or still got data to send
                    // if still got data to send, wait for the next writable event
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
            assert state == 2;

            ByteArray arr = messagesToWrite.poll();
            if (arr == null) {
                // nothing to write, no need to watch writable events
                loop.rmOps(fd, EventSet.write());
                return;
            }
            cachedMessageToWrite = ByteArrayChannel.fromFull(arr);
        }
    }

    @Override
    public final void removed(HandlerContext<SocketFD> ctx) {
        Logger.warn(LogType.IMPROPER_USE, "fd " + fd + " removed from loop, we have to invalid the fd");
        try {
            fd.close();
        } catch (IOException e) {
            Logger.error(LogType.CONN_ERROR, "closing fd " + fd + " failed", e);
        }
    }

    private final Map<Integer, StreamedFD> fdMap = new HashMap<>();

    @MethodForImplementation
    protected final boolean hasStream(int streamId) {
        return fdMap.containsKey(streamId);
    }

    private boolean newStream(int streamId) {
        if (hasStream(streamId)) {
            assert Logger.lowLevelDebug("trying to add existing fd to fdMap: " + fd);
            return false;
        }
        InetAddress virtualAddress;
        {
            byte b1 = (byte) ((streamId >> 24) & 0xff);
            byte b2 = (byte) ((streamId >> 16) & 0xff);
            byte b3 = (byte) ((streamId >> 8) & 0xff);
            byte b4 = (byte) (streamId & 0xff);
            try {
                virtualAddress = InetAddress.getByAddress(new byte[]{
                    b1, b2, b3, b4
                });
            } catch (UnknownHostException e) {
                fail(e);
                return false;
            }
        }
        int virtualPort;
        {
            try {
                if (client) {
                    virtualPort = ((InetSocketAddress) fd.getLocalAddress()).getPort();
                } else {
                    virtualPort = ((InetSocketAddress) fd.getRemoteAddress()).getPort();
                }
            } catch (IOException e) {
                fail(e);
                return false;
            }
        }
        SocketAddress virtual = new InetSocketAddress(virtualAddress, virtualPort);
        SocketAddress local;
        SocketAddress remote;
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
        StreamedFD sfd = new StreamedFD(streamId, fd, (WrappedSelector) loop.selector, local, remote, this, client);
        fdMap.put(streamId, sfd);
        assert Logger.lowLevelDebug("adding new fd to fdMap: " + fd);
        return true;
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
            accept(streamId);
        }
        StreamedFD sfd = fdMap.get(streamId);
        assert sfd != null;
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
        if (sfd.getState() == StreamedFD.State.none) {
            assert Logger.lowLevelDebug("fd: " + sfd + " state = " + sfd.getState() + " == " + StreamedFD.State.none);
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
        if (sfd.getState() == StreamedFD.State.dead) {
            return false; // already closed
        }
        sfd.setState(StreamedFD.State.dead);
        sfd.setRst();
        // need to send RST back
        addMessageToWrite(formatRST(streamId));
        return true;
    }

    private void watchWritable() {
        loop.addOps(fd, EventSet.write());
    }

    private void addMessageToWrite(ByteArray arr) {
        if (arr == null || arr.length() == 0) {
            return;
        }
        assert Logger.lowLevelNetDebug("addMessageToWrite");
        assert Logger.lowLevelNetDebugPrintBytes(arr.toJavaArray());
        messagesToWrite.add(arr);
        watchWritable();
    }

    private void pushMessageToWrite(ByteArray arr) {
        if (arr == null || arr.length() == 0) {
            return;
        }
        messagesToWrite.push(arr);
        watchWritable();
    }

    @MethodForImplementation
    protected final void send(ByteArray data) {
        addMessageToWrite(data);
    }

    abstract protected ByteArray formatPSH(int streamId, ByteArray data);

    @MethodForStreamedFD
    public final void send(StreamedFD fd, ByteArray data) throws IOException {
        if (!fdMap.containsValue(fd)) {
            throw new IOException("fdMap does not contain fd " + fd);
        }
        if (fd.getState() != StreamedFD.State.established) {
            throw new IOException(fd + " is not connected yet");
        }
        // append to the last of the queue
        addMessageToWrite(formatPSH(fd.streamId, data));
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
                // nothing to do
                break;
        }
    }

    abstract protected ByteArray keepaliveMessage();

    @MethodForFDs
    final void keepalive() {
        // add to the first of the queue
        pushMessageToWrite(keepaliveMessage());
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
        fd.setState(StreamedFD.State.dead);
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
            try {
                streamedFD.close();
            } catch (IOException e) {
                Logger.error(LogType.CONN_ERROR, "closing streamed fd failed: " + streamedFD, e);
            }
        }
        cachedMessageToWrite = null;
        cachedReceivedMessage = null;
        messagesToWrite.clear();
        fdMap.clear();
    }
}
