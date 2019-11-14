package vproxy.selector.wrap.streamed;

import vfd.FD;
import vfd.SocketFD;
import vproxy.selector.wrap.VirtualFD;
import vproxy.selector.wrap.WrappedSelector;
import vproxy.util.ByteArray;
import vproxy.util.Logger;
import vproxy.util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;

public class StreamedFD implements SocketFD, VirtualFD {
    public final int streamId;
    private final FD realFD;
    private final WrappedSelector selector;
    private final SocketAddress localAddress;
    private final SocketAddress remoteAddress;
    private final StreamedFDHandler handler;
    private final boolean client;
    private final Deque<ByteBuffer> readableBuffers = new LinkedList<>();
    private State state = State.none;
    private boolean rst = false;
    private boolean soLinger0 = false;

    private boolean readable = false;
    private boolean writable = false;

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
                      SocketAddress localAddress,
                      SocketAddress remoteAddress,
                      StreamedFDHandler handler,
                      boolean client) {
        this.streamId = streamId;
        this.realFD = realFD;
        this.selector = selector;
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
        this.handler = handler;
        this.client = client;
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
            throw new IOException(Utils.RESET_MSG);
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
        if (this.state != State.established && newState == State.established) {
            setWritable();
        } else if (newState == State.fin_recv || newState == State.dead) {
            setReadable();
        }
        this.state = newState;
    }

    @Override
    public void connect(InetSocketAddress l4addr) throws IOException {
        if (!remoteAddress.equals(l4addr)) {
            throw new IOException("cannot connect to " + l4addr + "(you could only connect to " + remoteAddress + ")");
        }
        checkState();
        handler.sendSYN(this);
        state = State.syn_sent;
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
                state = State.dead;
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
    public SocketAddress getLocalAddress() {
        return localAddress;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public boolean finishConnect() throws IOException {
        checkState();
        return true; // always return true
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

        int n = Utils.writeFromFIFOQueueToBuffer(readableBuffers, dst);
        if (readableBuffers.isEmpty()) {
            // nothing can be read anymore
            if (!state.readReturnNegative1) { // keep firing readable when fin received or stream closed
                cancelReadable();
            }
        }
        assert Logger.lowLevelDebug("read() returns " + n + " bytes");
        return n;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        checkState();
        if (state == State.fin_sent) {
            throw new IOException("cannot write when in state " + state);
        }
        return handler.send(this, src);
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
        if (state != State.dead && soLinger0) {
            handler.sendRST(this);
        } else if (state != State.fin_sent && state != State.dead) {
            handler.sendFIN(this);
        }
        state = State.real_closed;
        release();
    }

    private void release() {
        readableBuffers.clear();
    }

    void inputData(ByteArray data) {
        assert Logger.lowLevelNetDebug("calling input with " + data.length());
        assert Logger.lowLevelNetDebugPrintBytes(data.toJavaArray());
        readableBuffers.add(ByteBuffer.wrap(data.toJavaArray()));
        setReadable();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(local=" + localAddress + ", remote=" + remoteAddress + ", client=" + client + ")";
    }
}
