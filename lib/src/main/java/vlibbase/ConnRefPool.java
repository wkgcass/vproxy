package vlibbase;

import vlibbase.impl.ConnRefPoolImpl;

import java.util.Optional;

public interface ConnRefPool extends ConnectionAware<Void> {
    static ConnRefPool create(int maxCount) {
        return new ConnRefPoolImpl(maxCount);
    }

    int count();

    Optional<ConnRef> get();

    boolean isClosed();

    void close();
}
