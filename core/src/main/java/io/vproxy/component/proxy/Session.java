package vproxy.component.proxy;

import vproxy.base.connection.Connection;

public class Session {
    public final Connection active;
    public final Connection passive;

    public Session(Connection active, Connection passive) {
        this.active = active;
        this.passive = passive;
    }

    public boolean isClosed() {
        return active.isClosed() && passive.isClosed();
    }

    public void close() {
        active.close();
        passive.close();
    }

    public String id() {
        return active.id() + "->" + passive.id();
    }

    @Override
    public String toString() {
        return "Session(" + active + ", " + passive + ")";
    }
}
