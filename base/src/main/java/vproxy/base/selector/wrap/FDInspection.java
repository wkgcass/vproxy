package vproxy.base.selector.wrap;

import vproxy.vfd.EventSet;
import vproxy.vfd.FD;

public class FDInspection {
    public final FD fd;
    public final EventSet watchedEvents;
    public final EventSet firedEvents;

    public FDInspection(FD fd, EventSet watchedEvents, EventSet firedEvents) {
        this.fd = fd;
        this.watchedEvents = watchedEvents;
        this.firedEvents = firedEvents;
    }
}
