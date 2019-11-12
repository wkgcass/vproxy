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
        none,
        syn_sent,
        established,
        fin_sent,
        fin_recv,
        dead,
        real_closed,
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

    private void setWritable() {
        assert Logger.lowLevelDebug("set writable for " + this);
        selector.registerVirtualWritable(this);
        writable = true;
    }

    private void cancelReadable() {
        assert Logger.lowLevelDebug("cancel readable for " + this);
        selector.removeVirtualReadable(this);
        readable = false;
    }

    private void checkState() throws IOException {
        if (rst) {
            throw new IOException(Utils.RESET_MSG);
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
    public SocketAddress getLocalAddress() throws IOException {
        checkState();
        return localAddress;
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        checkState();
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
            if (state == State.fin_recv || state == State.dead) {
                return -1;
            }
        }

        int n = Utils.writeFromFIFOQueueToBuffer(readableBuffers, dst);
        if (readableBuffers.isEmpty()) {
            // nothing can be read anymore
            cancelReadable();
        }
        assert Logger.lowLevelDebug("read() returns " + n + " bytes");
        return n;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        checkState();
        byte[] bytes = new byte[src.limit() - src.position()];
        src.get(bytes);
        handler.send(this, ByteArray.from(bytes));
        return bytes.length;
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
            if (Integer.valueOf(0).equals(value)) {
                soLinger0 = true;
            } else {
                soLinger0 = false;
            }
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
