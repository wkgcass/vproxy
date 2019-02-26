package net.cassite.vproxy.pool;

public interface ConnectionPoolHandlerProvider {
    ConnectionPoolHandler provide(PoolCallback cb);
}
