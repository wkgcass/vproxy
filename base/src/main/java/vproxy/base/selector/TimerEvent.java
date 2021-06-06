package vproxy.base.selector;

import vproxy.base.util.ThreadSafe;
import vproxy.base.util.time.TimeElem;

public class TimerEvent {
    private TimeElem event;
    private final SelectorEventLoop eventLoop;
    private boolean canceled = false;

    TimerEvent(SelectorEventLoop eventLoop) {
        this.eventLoop = eventLoop;
    }

    synchronized void setEvent(TimeElem event) {
        if (canceled) {
            event.removeSelf(); // this is invoked on event loop, so it's safe
            return;
        }
        this.event = event;
    }

    @ThreadSafe
    public synchronized void cancel() {
        if (canceled)
            return;
        canceled = true;
        if (event == null)
            return;
        eventLoop.nextTick(event::removeSelf);
    }
}
