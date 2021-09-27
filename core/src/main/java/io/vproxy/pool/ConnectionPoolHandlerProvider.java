package io.vproxy.pool;

public interface ConnectionPoolHandlerProvider {
    ConnectionPoolHandler provide(PoolCallback cb);
}
