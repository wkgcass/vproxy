package io.vproxy.base.connection;

import io.vproxy.base.Config;
import io.vproxy.vfd.FDProvider;
import io.vproxy.vfd.FDs;

public class ConnectionOpts {
    int timeout = Config.tcpTimeout;
    private FDs fds;

    public static ConnectionOpts getDefault() {
        return DefaultConnectionOpts.defaultConnectionOpts;
    }

    public ConnectionOpts() {
    }

    public ConnectionOpts setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    public ConnectionOpts setFDs(FDs fds) {
        this.fds = fds;
        return this;
    }

    public FDs getFds() {
        if (fds == null)
            return FDProvider.get().getProvided();
        return fds;
    }
}

final class DefaultConnectionOpts extends ConnectionOpts {
    static final DefaultConnectionOpts defaultConnectionOpts = new DefaultConnectionOpts();

    private DefaultConnectionOpts() {
    }

    @Override
    public ConnectionOpts setTimeout(int timeout) {
        throw new UnsupportedOperationException();
    }
}
