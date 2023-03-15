package io.vproxy.adaptor.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.selector.HandlerContext;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.vfd.DatagramFD;
import io.vproxy.vfd.FDProvider;
import io.vproxy.vfd.IPPort;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;

public class VProxyDatagramFDChannel extends AbstractVProxyChannel implements Channel {
    private final DatagramFD fd;
    private NetEventLoop el;
    private Config __config;

    public VProxyDatagramFDChannel() throws IOException {
        this(FDProvider.get().openDatagramFD());
    }

    public VProxyDatagramFDChannel(DatagramFD fd) {
        Objects.requireNonNull(fd);
        this.fd = fd;
    }

    public DatagramFD datagramFD() {
        return fd;
    }

    protected void setEventLoop(NetEventLoop el) {
        this.el = el;
    }

    @Override
    protected NetEventLoop eventLoop0() {
        return el;
    }

    @Override
    protected Channel.Unsafe unsafe0() {
        return new Unsafe();
    }

    @Override
    public Channel parent() {
        return null;
    }

    @Override
    protected ChannelConfig config0() {
        return new VProxyDatagramFDChannelConfig(this, config1());
    }

    protected Config config1() {
        if (__config == null) {
            return __config = new Config();
        }
        return __config;
    }

    @Override
    public boolean isOpen() {
        return fd.isOpen();
    }

    @Override
    public boolean isActive() {
        return fd.isOpen();
    }

    @Override
    public boolean isWritable() {
        return true;
    }

    @Override
    public long bytesBeforeUnwritable() {
        return Integer.MAX_VALUE;
    }

    @Override
    public long bytesBeforeWritable() {
        return 0;
    }

    @Override
    protected ByteBufAllocator alloc0() {
        return new PooledByteBufAllocator(true);
    }

    protected int readPacketBufferSize() {
        return 2048;
    }

    public io.vproxy.base.selector.Handler<DatagramFD> createFDHandler() {
        return new Handler();
    }

    protected class Handler implements io.vproxy.base.selector.Handler<DatagramFD> {
        @Override
        public void accept(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void connected(HandlerContext<DatagramFD> ctx) {
            // will not fire
        }

        @Override
        public void readable(HandlerContext<DatagramFD> ctx) {
            boolean read = false;
            while (true) {
                var buf = alloc().directBuffer(readPacketBufferSize());
                var nioBuf = buf.internalNioBuffer(buf.writerIndex(), buf.writableBytes());
                int pos = nioBuf.position();
                IPPort ipport;
                try {
                    ipport = ctx.getChannel().receive(nioBuf);
                } catch (IOException e) {
                    Logger.error(LogType.SOCKET_ERROR, "failed receiving from " + fd, e);
                    pipeline().fireExceptionCaught(e);
                    ReferenceCountUtil.safeRelease(buf);
                    break;
                }
                if (ipport == null) {
                    ReferenceCountUtil.safeRelease(buf);
                    break;
                }
                buf.readerIndex(pos);
                buf.writerIndex(nioBuf.position());

                var inetLocalAddress = (InetSocketAddress) localAddress();
                pipeline().fireChannelRead(new DatagramPacket(buf, inetLocalAddress, ipport.toInetSocketAddress()));
                read = true;
            }
            if (read) {
                pipeline().fireChannelReadComplete();
            }
        }

        @Override
        public void writable(HandlerContext<DatagramFD> ctx) {
            // will not fire because writable event is not watched
        }

        @Override
        public void removed(HandlerContext<DatagramFD> ctx) {
            assert Logger.lowLevelDebug("datagram " + fd + " removed from eventloop");
            ctx.getEventLoop().nextTick(() ->
                pipeline().fireChannelUnregistered());
        }
    }

    protected class Unsafe extends AbstractVProxyUnsafe {
        private SocketAddress localAddressCache = null;

        public Unsafe() {
            super(VProxyDatagramFDChannel.this);
        }

        @Override
        public SocketAddress localAddress() {
            if (localAddressCache != null) {
                return localAddressCache;
            }
            try {
                return localAddressCache = fd.getLocalAddress().toInetSocketAddress();
            } catch (IOException ignore) {
                return null;
            }
        }

        @Override
        public SocketAddress remoteAddress() {
            try {
                return fd.getRemoteAddress().toInetSocketAddress();
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public void close(ChannelPromise promise) {
            if (el != null) {
                el.getSelectorEventLoop().remove(fd);
            }
            el = null;
            try {
                fd.close();
            } catch (IOException e) {
                Logger.error(LogType.SOCKET_ERROR, "failed closing fd " + fd, e);
                // fallthrough, ignore exception for closing
            }
            promise.setSuccess();
            closeFuture0().setSuccess();
        }

        @Override
        public void deregister(ChannelPromise promise) {
            var el = VProxyDatagramFDChannel.this.el;
            VProxyDatagramFDChannel.this.el = null;
            if (el == null) {
                return;
            }
            el.getSelectorEventLoop().remove(fd);
        }

        @Override
        public void write(Object msg, ChannelPromise promise) {
            ByteBuf buf;
            SocketAddress remoteAddress;

            if (msg instanceof AddressedEnvelope) {
                @SuppressWarnings("unchecked")
                var envelope = (AddressedEnvelope<ByteBuf, SocketAddress>) msg;
                try {
                    remoteAddress = envelope.recipient();
                    buf = envelope.content();
                } catch (ClassCastException e) {
                    Logger.error(LogType.IMPROPER_USE, "expecting AddressedEnvelope<ByteBuf, SocketAddress> but got " + msg);
                    promise.setFailure(new UnsupportedOperationException("unsupported msg type"));
                    ReferenceCountUtil.safeRelease(msg);
                    return;
                }
            } else if (msg instanceof ByteBuf) {
                remoteAddress = null;
                buf = (ByteBuf) msg;
            } else {
                Logger.error(LogType.INVALID_INPUT_DATA, "cannot write " + msg + " because it's not ByteBuf: " + (msg == null ? "null" : msg.getClass()));
                promise.setFailure(new UnsupportedOperationException("unsupported msg type " + (msg == null ? "null" : msg.getClass())));
                ReferenceCountUtil.safeRelease(msg);
                return;
            }

            ByteBuffer nioBuf = buf.nioBufferCount() == 1
                ? buf.internalNioBuffer(buf.readerIndex(), buf.readableBytes())
                : buf.nioBuffer(buf.readerIndex(), buf.readableBytes());
            try {
                if (remoteAddress == null) {
                    fd.write(nioBuf);
                } else {
                    fd.send(nioBuf, IPPort.from(remoteAddress));
                }
            } catch (ClassCastException e) {
                Logger.error(LogType.IMPROPER_USE, "unexpected address: " + remoteAddress);
            } catch (IOException e) {
                assert Logger.lowLevelDebug("sending data failed");
                assert Logger.printStackTrace(e);
            } finally {
                ReferenceCountUtil.safeRelease(msg);
            }
        }

        @Override
        public void bind(SocketAddress localAddress, ChannelPromise promise) {
            IPPort ipport;
            try {
                ipport = IPPort.from(localAddress);
            } catch (Throwable t) {
                promise.setFailure(t);
                return;
            }
            try {
                fd.bind(ipport);
            } catch (IOException e) {
                promise.setFailure(e);
                return;
            }
            promise.setSuccess();
        }

        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            var prom = newPromise();
            if (localAddress != null) {
                bind(localAddress, prom);
            } else {
                prom.setSuccess();
            }

            prom.addListener(f -> {
                if (f.cause() != null) {
                    promise.setFailure(f.cause());
                    return;
                }
                IPPort ipport;
                try {
                    ipport = IPPort.from(remoteAddress);
                } catch (Throwable t) {
                    promise.setFailure(t);
                    return;
                }
                try {
                    fd.connect(ipport);
                } catch (IOException e) {
                    promise.setFailure(e);
                    return;
                }
                promise.setSuccess();
            });
        }
    }

    public static class Config {
    }
}
