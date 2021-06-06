package vproxy.vclient.impl;

import vproxy.base.connection.NetEventLoop;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.util.Logger;
import vproxy.base.util.thread.VProxyThread;
import vproxy.vclient.ClientContext;
import vproxy.vclient.GeneralClient;
import vproxy.vclient.GeneralClientOptions;

import java.io.IOException;

public abstract class AbstractClient implements GeneralClient {
    private final boolean noInputLoop;
    private NetEventLoop loop;
    private boolean closed = false;

    public AbstractClient(GeneralClientOptions<?> opts) {
        this.loop = opts.clientContext.loop;
        this.noInputLoop = (loop == null);
    }

    protected abstract String threadname();

    protected NetEventLoop getLoop() {
        if (closed) {
            throw new IllegalStateException("the client is closed");
        }
        if (loop != null) {
            return loop;
        }
        try {
            loop = new NetEventLoop(SelectorEventLoop.open());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        loop.getSelectorEventLoop().loop(r -> VProxyThread.create(r, threadname()));
        return loop;
    }

    private ClientContext retOfGetClientContext;

    @Override
    public ClientContext getClientContext() {
        if (retOfGetClientContext == null) {
            retOfGetClientContext = new ClientContext(getLoop());
        }
        return retOfGetClientContext;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (noInputLoop) {
            if (loop != null) {
                // should close the input loop because it's created by the lib
                loop.getSelectorEventLoop().nextTick(() -> {
                    try {
                        loop.getSelectorEventLoop().close();
                    } catch (IOException e) {
                        Logger.shouldNotHappen("got error when closing the event loop", e);
                    }
                });
            }
        }
    }
}
