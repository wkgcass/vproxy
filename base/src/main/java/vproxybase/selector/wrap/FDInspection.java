package vproxybase.selector.wrap;

import vfd.EventSet;
import vfd.FD;

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
