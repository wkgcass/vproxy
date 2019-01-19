package net.cassite.vproxy.component.proxy;

import net.cassite.vproxy.connection.Connection;

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

    @Override
    public String toString() {
        return "Session(" + active + ", " + passive + ")";
    }
}
