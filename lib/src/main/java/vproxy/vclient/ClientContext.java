package vproxy.vclient;

import vproxy.base.connection.NetEventLoop;

import java.util.Objects;

public class ClientContext {
    public final NetEventLoop loop;

    public ClientContext(NetEventLoop loop) {
        this.loop = loop;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientContext that = (ClientContext) o;
        return Objects.equals(loop, that.loop);
    }

    @Override
    public int hashCode() {
        return Objects.hash(loop);
    }
}
