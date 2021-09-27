package io.vproxy.base.selector.wrap;

import io.vproxy.vfd.*;

public class FDInspection {
    public final FD fd;
    public final EventSet watchedEvents;
    public final EventSet firedEvents;

    public FDInspection(FD fd, EventSet watchedEvents, EventSet firedEvents) {
        this.fd = fd;
        this.watchedEvents = watchedEvents;
        this.firedEvents = firedEvents;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String type = fd.getClass().getSimpleName();
        sb.append(type).append(" watch=").append(watchedEvents == null ? "/" : watchedEvents.toString())
            .append(" fire=").append(firedEvents == null ? "/" : firedEvents.toString());
        if (fd instanceof NetworkFD) {
            //noinspection rawtypes
            var netfd = (NetworkFD) fd;
            SockAddr local = null;
            try {
                local = netfd.getLocalAddress();
            } catch (Throwable ignore) {
            }
            SockAddr remote = null;
            try {
                remote = netfd.getRemoteAddress();
            } catch (Throwable ignore) {
            }
            sb.append(" local=");
            if (local == null) {
                sb.append("/");
            } else if (local instanceof IPPort) {
                sb.append(((IPPort) local).formatToIPPortString());
            } else {
                sb.append(local);
            }
            sb.append(" remote=");
            if (remote == null) {
                sb.append("/");
            } else if (remote instanceof IPPort) {
                sb.append(((IPPort) remote).formatToIPPortString());
            } else {
                sb.append(remote);
            }
        }
        return sb.toString();
    }
}
