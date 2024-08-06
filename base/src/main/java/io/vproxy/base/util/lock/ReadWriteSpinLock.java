package io.vproxy.base.util.lock;

import java.util.concurrent.atomic.AtomicInteger;

public class ReadWriteSpinLock {
    private static final int WRITE_LOCKED = 0x80_00_00_00;
    // 32 31 ------ 0
    // W  RRRR...RRRR
    private final AtomicInteger lock = new AtomicInteger(0);
    private final AtomicInteger wLockPending = new AtomicInteger(0);
    private final int spinTimes;

    public ReadWriteSpinLock() {
        this(20);
    }

    public ReadWriteSpinLock(int spinTimes) {
        this.spinTimes = spinTimes;
    }

    public void readLock() {
        while (true) {
            if (wLockPending.get() != 0) {
                spinWait();
                continue;
            }
            if (lock.incrementAndGet() < 0) {
                continue;
            }
            break;
        }
    }

    public void readUnlock() {
        lock.decrementAndGet();
    }

    public void writeLock() {
        wLockPending.incrementAndGet();
        while (!lock.compareAndSet(0, WRITE_LOCKED)) {
            spinWait();
        }
    }

    public void writeUnlock() {
        lock.set(0);
        wLockPending.decrementAndGet();
    }

    private void spinWait() {
        for (int i = 0; i < spinTimes; ++i) {
            Thread.onSpinWait();
        }
    }
}
