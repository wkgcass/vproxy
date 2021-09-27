package vproxy.base.connection;

import vproxy.base.Config;

public class ConnectionOpts {
    int timeout = Config.tcpTimeout;

    public static ConnectionOpts getDefault() {
        return DefaultConnectionOpts.defaultConnectionOpts;
    }

    public ConnectionOpts() {
    }

    public ConnectionOpts setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
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
