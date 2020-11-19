package vclient.impl;

import vclient.ClientContext;
import vclient.GeneralClient;
import vclient.GeneralClientOptions;
import vproxybase.connection.NetEventLoop;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.util.Logger;

import java.io.IOException;

public abstract class AbstractClient implements GeneralClient {
    private final boolean noInputLoop;
    private NetEventLoop loop;
    private boolean closed = false;

    public AbstractClient(GeneralClientOptions<?> opts) {
        this.loop = opts.clientContext.loop;
        this.noInputLoop = (loop == null);
    }

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
        loop.getSelectorEventLoop().loop(Thread::new);
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
