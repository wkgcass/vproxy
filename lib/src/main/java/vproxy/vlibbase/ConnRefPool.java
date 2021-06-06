package vproxy.vlibbase;

import vproxy.base.connection.NetEventLoop;
import vproxy.vlibbase.impl.ConnRefPoolImpl;
import vproxy.vlibbase.impl.EmptyConnRefPool;

import java.util.Optional;

public interface ConnRefPool extends ConnectionAware<Void> {
    static ConnRefPool create(int maxCount) {
        return create(new Options().setMaxCount(maxCount));
    }

    static ConnRefPool create(Options opts) {
        if (opts.maxCount <= 0) {
            return new EmptyConnRefPool(opts.loop);
        }
        return new ConnRefPoolImpl(opts);
    }

    int count();

    Optional<ConnRef> get();

    boolean isClosed();

    void close();

    NetEventLoop getLoop();

    class Options {
        public int maxCount = 10;
        public int idleTimeout = 10_000;
        public NetEventLoop loop = null;

        public Options() {
        }

        public Options(Options that) {
            this.maxCount = that.maxCount;
            this.idleTimeout = that.idleTimeout;
            this.loop = that.loop;
        }

        public Options setMaxCount(int maxCount) {
            this.maxCount = maxCount;
            return this;
        }

        public Options setIdleTimeout(int idleTimeout) {
            this.idleTimeout = idleTimeout;
            return this;
        }

        public Options setLoop(NetEventLoop loop) {
            this.loop = loop;
            return this;
        }
    }
}
