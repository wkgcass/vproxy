package vproxy.selector.wrap.arqudp;

import vfd.*;
import vproxy.selector.Handler;
import vproxy.selector.HandlerContext;
import vproxy.selector.SelectorEventLoop;
import vproxy.selector.wrap.VirtualFD;
import vproxy.selector.wrap.WrappedSelector;
import vproxy.selector.wrap.udp.ServerDatagramFD;
import vproxy.util.Logger;
import vproxy.util.nio.ByteArrayChannel;

import java.io.IOException;
import java.net.SocketOption;
import java.util.function.Consumer;
import java.util.function.Function;

public class ArqUDPServerSocketFD implements ServerSocketFD, VirtualFD {
    private final ServerDatagramFD fd;
    private final SelectorEventLoop loop;
    private final WrappedSelector selector;
    private final Function<SocketFD, Function<Consumer<ByteArrayChannel>, ArqUDPHandler>> handlerConstructorProvider;

    protected ArqUDPServerSocketFD(ServerDatagramFD fd, SelectorEventLoop loop,
                                   Function<SocketFD, Function<Consumer<ByteArrayChannel>, ArqUDPHandler>> handlerConstructorProvider) {
        this.fd = fd;
        this.loop = loop;
        this.handlerConstructorProvider = handlerConstructorProvider;
        this.selector = loop.selector;
    }

    @Override
    public IPPort getLocalAddress() throws IOException {
        return fd.getLocalAddress();
    }

    @Override
    public ArqUDPSocketFD accept() throws IOException {
        ServerDatagramFD.VirtualDatagramFD accepted = fd.accept();
        if (accepted == null) {
            selector.removeVirtualReadable(this);
            return null;
        }
        return new ArqUDPSocketFD(true, accepted, loop, handlerConstructorProvider.apply(accepted));
    }

    @Override
    public void bind(IPPort l4addr) throws IOException {
        fd.bind(l4addr);
    }

    @Override
    public void onRegister() {
        try {
            loop.add(fd, EventSet.read(), null, new Handler<>() {
                @Override
                public void accept(HandlerContext<ServerDatagramFD> ctx) {
                    selector.registerVirtualReadable(ArqUDPServerSocketFD.this);
                }

                @Override
                public void connected(HandlerContext<ServerDatagramFD> ctx) {
                    // will not fire
                }

                @Override
                public void readable(HandlerContext<ServerDatagramFD> ctx) {
                    // will not fire
                }

                @Override
                public void writable(HandlerContext<ServerDatagramFD> ctx) {
                    // will not fire
                }

                @Override
                public void removed(HandlerContext<ServerDatagramFD> ctx) {
                    // will not fire
                }
            });
        } catch (IOException e) {
            Logger.shouldNotHappen("onRegister callback failed when adding fd " + fd + " to loop", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onRemove() {
        loop.remove(fd);
    }

    @Override
    public void configureBlocking(boolean b) throws IOException {
        fd.configureBlocking(b);
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) throws IOException {
        fd.setOption(name, value);
    }

    @Override
    public FD real() {
        return fd.real();
    }

    @Override
    public boolean isOpen() {
        return fd.isOpen();
    }

    @Override
    public void close() throws IOException {
        fd.close();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(" + fd + ")";
    }
}
