package io.vproxy.base.component.elgroup;

import io.vproxy.base.component.elgroup.dummy.IEventLoopGroup;
import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.Annotations;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.anno.Blocking;
import io.vproxy.base.util.anno.ThreadSafe;
import io.vproxy.base.util.coll.ConcurrentHashSet;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.ClosedException;
import io.vproxy.base.util.exception.NotFoundException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class EventLoopGroup implements IEventLoopGroup {
    public final String alias;
    public final Annotations annotations;
    private ArrayList<EventLoopWrapper> eventLoops = new ArrayList<>(0); // use array list to make code look better,
    // it's the same if you use array
    private boolean closed = false; // if true, then all operations are disabled or return default value
    private final AtomicInteger cursor = new AtomicInteger(0); // current cursor of the eventLoops
    private final ConcurrentHashSet<EventLoopGroupAttach> attaches = new ConcurrentHashSet<>();

    public EventLoopGroup(String alias) {
        this(alias, new Annotations());
    }

    public EventLoopGroup(String alias, Annotations annotations) {
        this.alias = alias;
        this.annotations = annotations;
    }

    /*
     * ========================
     * START event loops
     * ========================
     */

    @Override
    @ThreadSafe
    public List<EventLoopWrapper> list() {
        if (closed) {
            return Collections.emptyList();
        }
        return new ArrayList<>(eventLoops);
    }

    @Override
    @ThreadSafe
    public List<String> names() {
        if (closed) {
            return Collections.emptyList();
        }
        return eventLoops.stream().map(el -> el.alias).collect(Collectors.toList());
    }

    @Override
    @ThreadSafe
    public EventLoopWrapper get(String alias) throws NotFoundException {
        if (closed) {
            throw new NotFoundException("event-loop in event-loop-group " + this.alias, alias);
        }
        ArrayList<EventLoopWrapper> ls = eventLoops;
        for (EventLoopWrapper w : ls) {
            if (w.alias.equals(alias))
                return w;
        }
        throw new NotFoundException("event-loop in event-loop-group " + this.alias, alias);
    }

    @Override
    @ThreadSafe
    public synchronized EventLoopWrapper add(String alias) throws AlreadyExistException, IOException, ClosedException {
        return add(alias, new Annotations());
    }

    @Override
    @ThreadSafe
    public synchronized EventLoopWrapper add(String alias, Annotations annotations) throws AlreadyExistException, IOException, ClosedException {
        if (closed) {
            throw new ClosedException();
        }
        ArrayList<EventLoopWrapper> ls = eventLoops;
        for (EventLoopWrapper w : ls) {
            if (w.alias.equals(alias))
                throw new AlreadyExistException("event-loop in event-loop-group " + this.alias, alias);
        }
        SelectorEventLoop selectorEventLoop;
        if (this.annotations.EventLoopGroup_PreferPoll && annotations.EventLoop_CoreAffinity != -1) {
            selectorEventLoop = SelectorEventLoop.open(new SelectorEventLoop.InitOptions(true, annotations.EventLoop_CoreAffinity));
        } else if (this.annotations.EventLoopGroup_PreferPoll) {
            selectorEventLoop = SelectorEventLoop.open(new SelectorEventLoop.InitOptions(true, -1));
        } else if (annotations.EventLoop_CoreAffinity != -1) {
            selectorEventLoop = SelectorEventLoop.open(new SelectorEventLoop.InitOptions(false, annotations.EventLoop_CoreAffinity));
        } else {
            selectorEventLoop = SelectorEventLoop.open();
        }
        EventLoopWrapper el = new EventLoopWrapper(alias, selectorEventLoop, annotations);
        ArrayList<EventLoopWrapper> newLs = new ArrayList<>(ls.size() + 1);
        newLs.addAll(ls);
        newLs.add(el);
        eventLoops = newLs;
        el.loop();

        assert Logger.lowLevelDebug("event loop added " + alias);

        invokeResourcesOnAdd();

        return el;
    }

    @Blocking
    private void tryCloseLoop(SelectorEventLoop selectorEventLoop) {
        try {
            selectorEventLoop.close();
        } catch (IOException e) {
            Logger.fatal(LogType.EVENT_LOOP_CLOSE_FAIL, "close event loop failed, err = " + e);
        }
    }

    @Override
    @Blocking
    // closing selectorEventLoop is blocking, so this is blocking as well
    @ThreadSafe
    public synchronized void remove(String alias) throws NotFoundException {
        if (closed) {
            return;
        }
        ArrayList<EventLoopWrapper> ls = eventLoops;
        ArrayList<EventLoopWrapper> newLs = new ArrayList<>(ls.size() - 1);
        boolean found = false;
        for (EventLoopWrapper w : ls) {
            if (w.alias.equals(alias)) {
                found = true;
                tryCloseLoop(w.getSelectorEventLoop());
            } else {
                newLs.add(w);
            }
        }
        if (!found)
            throw new NotFoundException("event-loop in event-loop-group " + this.alias, alias);

        eventLoops = newLs;
    }

    /*
     * ========================
     * END event loops
     * ========================
     */

    /*
     * ========================
     * START attached resources
     * ========================
     */

    @Override
    @ThreadSafe
    public void attachResource(EventLoopGroupAttach resource) throws AlreadyExistException, ClosedException {
        if (closed)
            throw new ClosedException();
        if (!attaches.add(resource)) {
            throw new AlreadyExistException(resource.getClass().getSimpleName(), resource.id());
        }
    }

    @Override
    @ThreadSafe
    public void detachResource(EventLoopGroupAttach resource) throws NotFoundException {
        if (closed)
            return;
        if (!attaches.remove(resource)) {
            throw new NotFoundException(resource.getClass().getSimpleName(), resource.id());
        }
    }

    private void invokeResourcesOnAdd() {
        for (EventLoopGroupAttach resource : attaches) {
            try {
                resource.onEventLoopAdd();
            } catch (Throwable t) {
                // ignore the error, the user code should not throw
                // only log here
                Logger.error(LogType.IMPROPER_USE, "exception when calling onEventLoopAdd on the resource, err = ", t);
            }
        }
    }

    private void removeResources() {
        for (EventLoopGroupAttach resource : attaches) {
            try {
                resource.onClose();
            } catch (Throwable t) {
                // ignore the error, the user code should not throw
                // only log here
                Logger.error(LogType.IMPROPER_USE, "exception when calling onClose on the resource, err = ", t);
            }
        }
        attaches.clear();
    }

    /*
     * ========================
     * END attached resources
     * ========================
     */

    /*
     * ========================
     * START group function
     * ========================
     */

    @Override
    @ThreadSafe
    public EventLoopWrapper next() {
        return next(null);
    }

    @Override
    @ThreadSafe
    public EventLoopWrapper next(NetEventLoop hint) {
        if (closed)
            return null;

        ArrayList<EventLoopWrapper> ls = eventLoops;
        //noinspection SuspiciousMethodCalls
        if (hint != null && ls.contains(hint)) {
            assert Logger.lowLevelDebug("caller loop is contained in the event loop group, directly return");
            return (EventLoopWrapper) hint;
        }
        assert Logger.lowLevelDebug("caller loop is not contained in the event loop group, choose one");
        return next(ls, 0);
    }

    private EventLoopWrapper next(ArrayList<EventLoopWrapper> ls, int recursion) {
        if (recursion > ls.size())
            return null;
        ++recursion;

        EventLoopWrapper result;

        int idx = cursor.getAndIncrement();
        if (ls.size() > idx) {
            result = ls.get(idx);
        } else if (ls.isEmpty()) {
            return null;
        } else {
            cursor.set(1);
            result = ls.get(0);
        }
        if (result.getSelectorEventLoop().isClosed()) {
            // maybe the list is operated in another thread
            // skip this element and return the next element
            return next(ls, recursion);
        }
        return result;
    }

    @Override
    @Blocking
    // closing selectorEventLoop is blocking, so this is blocking as well
    @ThreadSafe
    public synchronized void close() {
        if (closed)
            return;
        closed = true;
        // no modification should be made to eventLoops now
        ArrayList<EventLoopWrapper> ls = eventLoops;
        for (EventLoopWrapper w : ls) {
            tryCloseLoop(w.getSelectorEventLoop());
        }
        eventLoops.clear();
        removeResources();
    }

    /*
     * ========================
     * END group function
     * ========================
     */

    @Override
    public String toString() {
        return alias + " -> annotations " + annotations.toString();
    }
}
