package io.vproxy.adaptor.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.ChannelOutputShutdownEvent;
import io.netty.channel.socket.DuplexChannel;
import io.netty.util.ReferenceCountUtil;
import io.vproxy.base.connection.*;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.coll.RingQueue;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.base.util.nio.ByteArrayChannel;
import io.vproxy.vfd.IPPort;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.Objects;

public class VProxyConnectionChannel extends AbstractVProxyChannel implements DuplexChannel {
    private final VProxyServerSockChannel parent;
    private final Connection conn;
    private boolean isConnected;
    private ChannelPromise pendingShutdownOutput = null;
    private Config __config;

    public VProxyConnectionChannel(ConnectableConnection conn) {
        this(null, conn);
        isConnected = false;
    }

    protected VProxyConnectionChannel(VProxyServerSockChannel parent, Connection conn) {
        Objects.requireNonNull(conn);
        this.parent = parent;
        this.conn = conn;
        isConnected = true;
    }

    public static VProxyConnectionChannel connect(IPPort remote) {
        try {
            return new VProxyConnectionChannel(ConnectableConnection.create(remote));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Connection connection() {
        return conn;
    }

    @Override
    protected NetEventLoop eventLoop0() {
        return conn.getEventLoop();
    }

    @Override
    public Channel parent() {
        return parent;
    }

    @Override
    protected ChannelConfig config0() {
        return new VProxyConnectionChannelConfig(this, config1());
    }

    protected Config config1() {
        if (__config == null) {
            return __config = new Config();
        }
        return __config;
    }

    @Override
    public boolean isInputShutdown() {
        // vproxy do not support 'input shutdown'
        return conn.isClosed();
    }

    @Override
    public ChannelFuture shutdownInput() {
        return shutdownInput(newPromise());
    }

    @Override
    public ChannelFuture shutdownInput(ChannelPromise promise) {
        promise.setFailure(new UnsupportedOperationException());
        return promise;
    }

    @Override
    public boolean isOutputShutdown() {
        return conn.isWriteClosed() || conn.isClosed();
    }

    @Override
    public ChannelFuture shutdownOutput() {
        var pendingShutdownOutput = this.pendingShutdownOutput;
        if (pendingShutdownOutput != null) {
            return pendingShutdownOutput;
        }
        return shutdownOutput(newPromise());
    }

    @Override
    public ChannelFuture shutdownOutput(ChannelPromise promise) {
        if (buffers.isEmpty()) {
            doShutdownOutput(promise);
        } else {
            // assume there will be only one call to 'shutdownOutput'
            pendingShutdownOutput = promise;
        }
        return promise;
    }

    protected void doShutdownOutput(ChannelPromise promise) {
        conn.closeWrite();
        pendingShutdownOutput = null;
        promise.setSuccess();
        pipeline().fireUserEventTriggered(ChannelOutputShutdownEvent.INSTANCE);
    }

    @Override
    public boolean isShutdown() {
        return conn.isClosed();
    }

    @Override
    public ChannelFuture shutdown() {
        return shutdown(newPromise());
    }

    @Override
    public ChannelFuture shutdown(ChannelPromise promise) {
        return close(promise);
    }

    @Override
    public boolean isOpen() {
        return !conn.isClosed();
    }

    @Override
    public boolean isActive() {
        return !conn.isClosed() && isConnected;
    }

    @Override
    public boolean isWritable() {
        return !conn.isClosed() && !conn.isWriteClosed() && isConnected && conn.getOutBuffer().free() > 0;
    }

    @Override
    public long bytesBeforeUnwritable() {
        if (!isWritable()) return 0;
        return conn.getOutBuffer().capacity() - conn.getOutBuffer().free();
    }

    @Override
    public long bytesBeforeWritable() {
        if (!isWritable()) return 0;
        return conn.getOutBuffer().capacity() == conn.getOutBuffer().free() ? 1 : 0;
    }

    @Override
    protected Channel.Unsafe unsafe0() {
        return new Unsafe();
    }

    protected ConnectableConnectionHandler createConnectionHandler() {
        return new Handler();
    }

    private final RingQueue<Tuple<ByteArrayChannel, ChannelPromise>> buffers = new RingQueue<>(2);

    protected class Handler implements ConnectableConnectionHandler {
        @Override
        public void readable(ConnectionHandlerContext ctx) {
            boolean read = false;
            while (true) {
                var inBuffer = ctx.connection.getInBuffer();
                if (inBuffer.used() == 0) {
                    break;
                }
                var chnl = ByteArrayChannel.fromEmpty(inBuffer.used());
                inBuffer.writeTo(chnl);
                var buf = Unpooled.wrappedBuffer(chnl.getBytes());
                pipeline().fireChannelRead(buf);
                read = true;
            }
            if (read) {
                pipeline().fireChannelReadComplete();
            }
        }

        @Override
        public void writable(ConnectionHandlerContext ctx) {
            if (buffers.isEmpty()) {
                pipeline().fireChannelWritabilityChanged();
            } else {
                flushBuffers();
                if (buffers.isEmpty() && conn.getOutBuffer().free() > 0) {
                    pipeline().fireChannelWritabilityChanged();
                }
            }
        }

        @Override
        public void exception(ConnectionHandlerContext ctx, IOException err) {
            pipeline().fireExceptionCaught(err);
        }

        @Override
        public void remoteClosed(ConnectionHandlerContext ctx) {
            assert Logger.lowLevelDebug("connection " + conn + " remote closed");
            if (config1().isAllowHalfClosure()) {
                pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
                ctx.eventLoop.getSelectorEventLoop().nextTick(() -> {
                    if (!conn.isClosed()) {
                        pipeline().fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE);
                    }
                });
            } else {
                shutdownOutput();
            }
        }

        @Override
        public void closed(ConnectionHandlerContext ctx) {
            assert Logger.lowLevelDebug("connection " + conn + " closed");
            closeFuture0().setSuccess();
            pipeline().fireChannelInactive();
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            assert Logger.lowLevelDebug("connection " + conn + " removed from eventloop");
            ctx.eventLoop.getSelectorEventLoop().nextTick(() ->
                pipeline().fireChannelUnregistered());
        }

        @Override
        public void connected(ConnectableConnectionHandlerContext ctx) {
            isConnected = true;
            pipeline().fireChannelWritabilityChanged();
        }

        @Override
        public boolean triggerClosedCallbackOnExplicitClosing() {
            return true;
        }
    }

    protected class Unsafe extends AbstractVProxyUnsafe implements Channel.Unsafe {
        public Unsafe() {
            super(VProxyConnectionChannel.this);
        }

        @Override
        public SocketAddress localAddress() {
            return conn.getLocal().toInetSocketAddress();
        }

        @Override
        public SocketAddress remoteAddress() {
            return conn.getRemote().toInetSocketAddress();
        }

        @Override
        public void close(ChannelPromise promise) {
            if (!buffers.isEmpty()) {
                ClosedChannelException closedChannelException =
                    StacklessClosedChannelException.newInstance(VProxyConnectionChannel.class, "close(ChannelPromise)");
                Tuple<?, ChannelPromise> b;
                while ((b = buffers.poll()) != null) {
                    b.right.setFailure(closedChannelException);
                }
            }
            conn.close(config1().isReset());
            promise.setSuccess();
        }

        @Override
        public void deregister(ChannelPromise promise) {
            var el = conn.getEventLoop();
            if (el != null) {
                el.removeConnection(conn);
            }
            promise.setSuccess();
        }

        @Override
        public void write(Object msg, ChannelPromise promise) {
            if (!(msg instanceof ByteBuf)) {
                Logger.error(LogType.INVALID_INPUT_DATA, "cannot write " + msg + " because it's not ByteBuf: " + (msg == null ? "null" : msg.getClass()));
                promise.setFailure(new UnsupportedOperationException("unsupported msg type " + (msg == null ? "null" : msg.getClass())));
                ReferenceCountUtil.safeRelease(msg);
                return;
            }
            var buf = (ByteBuf) msg;
            if (buf.readableBytes() == 0) {
                if (buffers.isEmpty()) {
                    // nothing to be written
                    promise.setSuccess();
                    ReferenceCountUtil.safeRelease(buf);
                } else {
                    buffers.add(new Tuple<>(null, promise));
                }
                return;
            }

            if (pendingShutdownOutput != null || conn.isClosed() || conn.isWriteClosed()) {
                Logger.error(LogType.IMPROPER_USE, "the connection " + conn + " is shutdown or closed, but still trying to write data to it");
                promise.setFailure(new IllegalStateException());
                ReferenceCountUtil.safeRelease(buf);
                return;
            }

            ByteArrayChannel chnl;
            if (buf.hasArray()) {
                var off = buf.arrayOffset();
                chnl = ByteArrayChannel.from(buf.array(),
                    off + buf.readerIndex(),
                    off + buf.readerIndex() + buf.readableBytes(),
                    0);
            } else {
                var newBuf = Unpooled.copiedBuffer(buf);
                chnl = ByteArrayChannel.fromFull(newBuf.array());
            }
            ReferenceCountUtil.safeRelease(buf);
            if (!buffers.isEmpty()) {
                buffers.add(new Tuple<>(chnl, promise));
                return;
            }

            if (!writeBuffer(chnl, promise)) {
                // still need to write
                buffers.add(new Tuple<>(chnl, promise));
                // which means the output buf is full, so need to alert netty for that
                pipeline().fireChannelWritabilityChanged();
            }
        }
    }

    protected boolean writeBuffer(ByteArrayChannel chnl, ChannelPromise promise) {
        if (chnl == null) { // this is a placeholder to check whether data is flushed
            if (conn.getOutBuffer().free() > 0) { // so only setSuccess if there's space left
                promise.setSuccess();
                return true;
            } else {
                return false;
            }
        }
        conn.getOutBuffer().storeBytesFrom(chnl);
        if (chnl.used() == 0) {
            // everything has been written
            promise.setSuccess();
            return true;
        }
        return false;
    }

    protected void flushBuffers() {
        while (true) {
            var tup = buffers.peek();
            if (tup == null) {
                break;
            }
            if (writeBuffer(tup.left, tup.right)) {
                buffers.poll();
            } else {
                break; // still unable to write all data
            }
        }
        if (buffers.isEmpty()) {
            var pendingShutdownOutput = this.pendingShutdownOutput;
            if (pendingShutdownOutput != null) {
                doShutdownOutput(pendingShutdownOutput);
            }
        }
    }

    public static class Config {
        private boolean allowHalfClosure = false; // default false to match netty's rules
        private boolean reset = false;

        public boolean isAllowHalfClosure() {
            return allowHalfClosure;
        }

        public void setAllowHalfClosure(boolean allowHalfClosure) {
            this.allowHalfClosure = allowHalfClosure;
        }

        public boolean isReset() {
            return reset;
        }

        public void setReset(boolean reset) {
            this.reset = reset;
        }
    }
}
