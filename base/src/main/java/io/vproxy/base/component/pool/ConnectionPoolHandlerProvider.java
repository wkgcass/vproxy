package io.vproxy.base.component.pool;

public interface ConnectionPoolHandlerProvider {
    ConnectionPoolHandler provide(PoolCallback cb);
}
