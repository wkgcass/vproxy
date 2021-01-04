package vproxybase.selector.wrap.streamed;

import vfd.FD;
import vfd.IPPort;
import vfd.SocketFD;
import vmirror.MirrorDataFactory;
import vproxybase.selector.wrap.VirtualFD;
import vproxybase.selector.wrap.WrappedSelector;
import vproxybase.util.ByteArray;
import vproxybase.util.Consts;
import vproxybase.util.Logger;
import vproxybase.util.Utils;

import java.io.IOException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;

public class StreamedFD implements SocketFD, VirtualFD {
    public final int streamId;
    private final FD realFD;
    private final WrappedSelector selector;
    private final IPPort localAddress;
    private final IPPort remoteAddress;
    private final StreamedFDHandler handler;
    private final boolean client;
    private final Deque<ByteBuffer> readableBuffers = new LinkedList<>();
    private State state = State.none;
    private boolean rst = false;
    private boolean soLinger0 = false;

    private boolean readable = false;
    private boolean writable = false;

    final MirrorDataFactory readingMirrorDataFactory;
    final MirrorDataFactory writingMirrorDataFactory;

    public enum State {
        none(Logger.DEBUG_COLOR, false),
        syn_sent(Logger.WARN_COLOR, false),
        established(Logger.INFO_COLOR, false),
        fin_sent(Logger.WARN_COLOR, false),
        fin_recv(Logger.WARN_COLOR, true),
        dead(Logger.ERROR_COLOR, true),
        real_closed(Logger.ERROR_COLOR, true),
        ;
        public final String probeColor;
        public final boolean readReturnNegative1;

        State(String probeColor, boolean readReturnNegative1) {
            this.probeColor = probeColor;
            this.readReturnNegative1 = readReturnNegative1;
        }
    }

    public StreamedFD(int streamId,
                      FD realFD,
                      WrappedSelector selector,
                      IPPort localAddress,
                      IPPort remoteAddress,
                      StreamedFDHandler handler,
                      boolean client) {
        this.streamId = streamId;
        this.realFD = realFD;
        this.selector = selector;
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
        this.handler = handler;
        this.client = client;

        // mirror
        readingMirrorDataFactory = new MirrorDataFactory("streamed",
            d -> {
                {
                    IPPort remote = this.getRemoteAddress();
                    d.setSrc(remote);
                }
                {
                    IPPort local = this.getLocalAddress();
                    d.setDst(local);
                }
            });
        writingMirrorDataFactory = new MirrorDataFactory("streamed",
            d -> {
                {
                    IPPort local = this.getLocalAddress();
                    d.setSrc(local);
                }
                {
                    IPPort remote = this.getRemoteAddress();
                    d.setDst(remote);
                }
            });
    }

    public State getState() {
        return state;
    }

    public void setRst() {
        this.rst = true;
    }

    private void setReadable() {
        assert Logger.lowLevelDebug("set readable for " + this);
        selector.registerVirtualReadable(this);
        readable = true;
    }

    void setWritable() {
        assert Logger.lowLevelDebug("set writable for " + this);
        selector.registerVirtualWritable(this);
        writable = true;
    }

    private void cancelReadable() {
        assert Logger.lowLevelDebug("cancel readable for " + this);
        selector.removeVirtualReadable(this);
        readable = false;
    }

    void cancelWritable() {
        assert Logger.lowLevelDebug("cancel writable for " + this);
        selector.removeVirtualWritable(this);
        writable = false;
    }

    private void checkState() throws IOException {
        if (rst) {
            throw new IOException(Utils.RESET_MSG.get(0));
        }
        if (state == State.dead) {
            throw new IOException(Utils.BROKEN_PIPE_MSG);
        }
        if (state == State.real_closed) {
            throw new IOException(this + " is closed");
        }
    }

    public void setState(State newState) {
        assert Logger.lowLevelDebug("state for " + this + " changes: old=" + state + ", new=" + newState);
        if (state == State.real_closed) {
            // no need to set to other states it it's already closed
            Logger.shouldNotHappen("should not set to another state when it's real-closed: " + this + ", new=" + newState, new Throwable());
            return;
        }
        if (this.state != State.established && newState == State.established) {
            if (handler.writableLen() > 0) {
                setWritable();
            }
        } else if (newState == State.fin_recv || newState == State.dead) {
            setReadable();
        }
        this.state = newState;
    }

    @Override
    public void connect(IPPort l4addr) throws IOException {
        if (!remoteAddress.equals(l4addr)) {
            throw new IOException("cannot connect to " + l4addr + "(you could only connect to " + remoteAddress + ")");
        }
        checkState();
        handler.sendSYN(this);
        setState(State.syn_sent);
    }

    @Override
    public boolean isConnected() {
        return state == State.established;
    }

    @Override
    public void shutdownOutput() throws IOException {
        checkState();
        switch (state) {
            case none:
                setState(State.dead);
                break;
            case syn_sent:
                close();
                break;
            case established:
                handler.sendFIN(this);
                break;
            case fin_sent:
                // already sent
                break;
            case fin_recv:
                //noinspection DuplicateBranchesInSwitch
                close();
                break;
            case dead:
                // nothing to do if it's already closed
                break;
        }
    }

    @Override
    public IPPort getLocalAddress() {
        return localAddress;
    }

    @Override
    public IPPort getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public boolean finishConnect() throws IOException {
        checkState();
        return true; // always return true
    }

    private void mirrorRead(ByteBuffer dst, int posBefore) {
        int posAfter = dst.position();
        int lim = dst.limit();

        byte[] arr = new byte[posAfter - posBefore];
        if (arr.length == 0) { // nothing read
            return;
        }

        dst.limit(posAfter).position(posBefore);
        dst.get(arr);
        // set offset back
        dst.limit(lim).position(posAfter);

        handler.mirror(this, false, Consts.TCP_FLAGS_PSH, ByteArray.from(arr));
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        assert Logger.lowLevelDebug("read() called on " + this);
        checkState();
        if (readableBuffers.isEmpty()) {
            if (state.readReturnNegative1) {
                return -1;
            }
        }

        int posBefore = dst.position();
        int n = Utils.writeFromFIFOQueueToBuffer(readableBuffers, dst);

        if (readingMirrorDataFactory.isEnabled()) {
            mirrorRead(dst, posBefore);
        }

        if (readableBuffers.isEmpty()) {
            // nothing can be read anymore
            if (!state.readReturnNegative1) { // keep firing readable when fin received or stream closed
                cancelReadable();
            }
        }
        assert Logger.lowLevelDebug("read() returns " + n + " bytes");
        return n;
    }

    private void mirrorWrite(ByteBuffer src, int posBefore) {
        int posAfter = src.position();
        int lim = src.limit();

        byte[] arr = new byte[posAfter - posBefore];
        if (arr.length == 0) { // nothing read
            return;
        }

        src.limit(posAfter).position(posBefore);
        src.get(arr);
        // set offset back
        src.limit(lim).position(posAfter);

        handler.mirror(this, true, Consts.TCP_FLAGS_PSH, ByteArray.from(arr));
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        checkState();
        if (state == State.fin_sent) {
            throw new IOException("cannot write when in state " + state);
        }
        int posBefore = src.position();
        int wrote = handler.send(this, src);

        if (writingMirrorDataFactory.isEnabled()) {
            mirrorWrite(src, posBefore);
        }

        assert Logger.lowLevelDebug("streamed fd wrote " + wrote + " bytes: " + this);
        return wrote;
    }

    @Override
    public void onRegister() {
        if (readable) {
            setReadable();
        }
        if (writable) {
            setWritable();
        }
    }

    @Override
    public void onRemove() {
        // ignore
    }

    @Override
    public void configureBlocking(boolean b) {
        // ignore
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) {
        if (name == StandardSocketOptions.SO_LINGER) {
            soLinger0 = Integer.valueOf(0).equals(value);
        }
    }

    @Override
    public FD real() {
        return realFD;
    }

    @Override
    public boolean isOpen() {
        return state != State.real_closed;
    }

    @Override
    public void close() throws IOException {
        if (state == State.real_closed) {
            return;
        }
        setState(State.real_closed);
        if (state != State.dead && soLinger0) {
            handler.sendRST(this);
        } else if (state != State.fin_sent && state != State.dead) {
            handler.sendFIN(this);
        }
        handler.removeStreamedFD(this);
        release();
    }

    private void release() {
        readableBuffers.clear();
    }

    void inputData(ByteArray data) {
        assert Logger.lowLevelDebug("calling input with " + data.length() + " on " + this + ", readableBuffers.size() before adding is " + readableBuffers.size());
        assert Logger.lowLevelNetDebugPrintBytes(data.toJavaArray());
        if (state == State.real_closed || state == State.dead) {
            // connection is already closed, so ignore any data from another endpoint
            return;
        }
        readableBuffers.add(ByteBuffer.wrap(data.toJavaArray()));
        setReadable();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(local=" + localAddress + ", remote=" + remoteAddress + ", client=" + client + ", state=" + state + ", insideFD=" + realFD + ")";
    }
}
