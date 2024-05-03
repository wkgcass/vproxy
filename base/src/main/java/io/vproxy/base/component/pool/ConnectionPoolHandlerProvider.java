package io.vproxy.base.component.pool;

public interface ConnectionPoolHandlerProvider {
    record ProvideParams(PoolCallback poolCallback) {
    }

    ConnectionPoolHandler provide(ProvideParams params);
}
