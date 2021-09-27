package io.vproxy.base.util;

import java.util.concurrent.locks.ReentrantLock;

public class Lock {
    private final java.util.concurrent.locks.Lock lock;
    private final Locked locked;

    private Lock(java.util.concurrent.locks.Lock lock) {
        this.lock = lock;
        this.locked = new Locked();
    }

    public static Lock create() {
        return new Lock(new ReentrantLock());
    }

    public static Lock createMock() {
        return new Lock(null);
    }

    public Locked lock() {
        if (lock != null) {
            lock.lock();
        }
        return locked;
    }

    public class Locked implements AutoCloseable {
        private Locked() {
        }

        @Override
        public void close() {
            if (lock != null) {
                lock.unlock();
            }
        }
    }
}
