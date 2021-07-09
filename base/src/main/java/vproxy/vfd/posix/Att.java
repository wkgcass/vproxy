package vproxy.vfd.posix;

import vproxy.vfd.EventSet;
import vproxy.vfd.FD;

class Att {
    public FD fd;
    public EventSet ops;
    public Object att;

    public Att set(FD fd, EventSet events, Object att) {
        this.fd = fd;
        this.ops = events;
        this.att = att;
        return this;
    }

    @Override
    public String toString() {
        return "Att{" +
            "fd=" + fd +
            ", events=" + ops +
            ", att=" + att +
            '}';
    }
}
