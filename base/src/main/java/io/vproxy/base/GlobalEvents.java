package io.vproxy.base;

import io.vproxy.base.component.svrgroup.ServerGroup;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class GlobalEvents {
    private static final GlobalEvents instance = new GlobalEvents();

    private final Map<EventType, Set<Consumer>> listeners = new HashMap<>();

    private GlobalEvents() {
    }

    public static GlobalEvents getInstance() {
        return instance;
    }

    public <T> void register(EventType<T> event, Consumer<T> handler) {
        synchronized (listeners) {
            Set<Consumer> ls = listeners.computeIfAbsent(event, k -> new HashSet<>());
            ls.add(handler);
        }
    }

    public <T> void deregister(EventType<T> event, Consumer<T> handler) {
        synchronized (listeners) {
            Set<Consumer> ls = listeners.computeIfAbsent(event, k -> new HashSet<>());
            ls.remove(handler);
            if (ls.isEmpty()) {
                listeners.remove(event);
            }
        }
    }

    public <T> void trigger(EventType<T> event, T msg) {
        Set<Consumer> ls;
        synchronized (listeners) {
            ls = listeners.get(event);
            if (ls == null) {
                return;
            }
            ls = new HashSet<>(ls);
        }
        for (Consumer c : ls) {
            try {
                //noinspection unchecked
                c.accept(msg);
            } catch (Throwable t) {
                Logger.error(LogType.IMPROPER_USE, "trigger event " + event, t);
            }
        }
    }

    @SuppressWarnings("unused")
    public interface EventType<T> {
    }

    public static final EventType<Messages.HealthCheck> HEALTH_CHECK = new EventType<>() {
    };

    public static class Messages {
        private Messages() {
        }

        public static class HealthCheck {
            public final ServerGroup.ServerHandle server;
            public final ServerGroup serverGroup;

            public HealthCheck(ServerGroup.ServerHandle server, ServerGroup serverGroup) {
                this.server = server;
                this.serverGroup = serverGroup;
            }
        }
    }
}
