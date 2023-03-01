package io.vproxy.base.util.log;

import io.vproxy.base.util.coll.CopyOnWriteHolder;
import io.vproxy.base.util.coll.WeakHashSet;

public class LogDispatcher {
    private final CopyOnWriteHolder<WeakHashSet<LogHandler>> callbacks = new CopyOnWriteHolder<>(new WeakHashSet<>(), WeakHashSet::new);

    public LogDispatcher() {
    }

    public synchronized void addLogHandler(LogHandler listener) {
        callbacks.update(set -> set.add(listener));
    }

    public synchronized void removeLogHandler(LogHandler listener) {
        callbacks.update(set -> set.remove(listener));
    }

    public void publish(LogRecord record) {
        for (var s : callbacks.get()) {
            s.publish(record);
        }
    }
}
