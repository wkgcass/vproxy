package net.cassite.vproxy.component.elgroup;

import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.ClosedException;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.ThreadSafe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class EventLoopGroup {
    private ArrayList<EventLoopWrapper> eventLoops = new ArrayList<>(0); // use array list to make code look better,
    // it's the same if you use array
    private boolean preClose = false; // if true, then all MODIFY operations are disabled or return default value
    private boolean closed = false; // if true, then all operations are disabled or return default value
    private final AtomicInteger cursor = new AtomicInteger(0); // current cursor of the eventLoops
    private final ConcurrentMap<String, EventLoopGroupAttach> attaches = new ConcurrentHashMap<>();

    public EventLoopGroup() {
    }

    /*
     * ========================
     * START event loops
     * ========================
     */

    @ThreadSafe
    public List<String> names() {
        if (closed) {
            return Collections.emptyList();
        }
        return eventLoops.stream().map(el -> el.alias).collect(Collectors.toList());
    }

    @ThreadSafe
    public EventLoopWrapper get(String alias) throws NotFoundException {
        if (closed) {
            throw new NotFoundException();
        }
        ArrayList<EventLoopWrapper> ls = eventLoops;
        for (EventLoopWrapper w : ls) {
            if (w.alias.equals(alias))
                return w;
        }
        throw new NotFoundException();
    }

    @ThreadSafe
    public synchronized void add(String alias) throws AlreadyExistException, IOException, ClosedException {
        if (preClose) {
            throw new ClosedException();
        }
        ArrayList<EventLoopWrapper> ls = eventLoops;
        for (EventLoopWrapper w : ls) {
            if (w.alias.equals(alias))
                throw new AlreadyExistException();
        }
        EventLoopWrapper el = new EventLoopWrapper(alias, SelectorEventLoop.open());
        ArrayList<EventLoopWrapper> newLs = new ArrayList<>(ls.size() + 1);
        newLs.addAll(ls);
        newLs.add(el);
        eventLoops = newLs;
        el.loop();

        assert Logger.lowLevelDebug("event loop added " + alias);

        invokeResourcesOnAdd();
    }

    private void tryCloseLoop(EventLoopWrapper wrapper) {
        try {
            wrapper.selectorEventLoop.close();
        } catch (IOException e) {
            Logger.fatal(LogType.EVENT_LOOP_CLOSE_FAIL, "close event loop failed, err = " + e);
        }
    }

    @ThreadSafe
    public synchronized void remove(String alias) throws NotFoundException {
        if (preClose) {
            return;
        }
        ArrayList<EventLoopWrapper> ls = eventLoops;
        ArrayList<EventLoopWrapper> newLs = new ArrayList<>(ls.size() - 1);
        boolean found = false;
        for (EventLoopWrapper w : ls) {
            if (w.alias.equals(alias)) {
                found = true;
                tryCloseLoop(w);
            } else {
                newLs.add(w);
            }
        }
        if (!found)
            throw new NotFoundException();

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

    @ThreadSafe
    public List<String> attachedNames() {
        if (closed) {
            return Collections.emptyList();
        }
        return new ArrayList<>(attaches.keySet());
    }

    @ThreadSafe
    public void attachResource(EventLoopGroupAttach resource) throws AlreadyExistException, ClosedException {
        if (preClose)
            throw new ClosedException();
        if (attaches.putIfAbsent(resource.id(), resource) != null) {
            throw new AlreadyExistException();
        }
    }

    @ThreadSafe
    public void detachResource(EventLoopGroupAttach resource) throws NotFoundException {
        if (preClose)
            return;
        if (attaches.remove(resource.id()) == null) {
            throw new NotFoundException();
        }
    }

    private void invokeResourcesOnAdd() {
        for (EventLoopGroupAttach resource : attaches.values()) {
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
        for (EventLoopGroupAttach resource : attaches.values()) {
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

    public synchronized void setPreClose(boolean preClose) {
        this.preClose = preClose;
    }

    @ThreadSafe
    public EventLoopWrapper next() {
        if (preClose)
            return null;

        ArrayList<EventLoopWrapper> ls = eventLoops;
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
        if (result.selectorEventLoop.isClosed()) {
            // maybe the map is operated in another thread
            // skip this element and return the next element
            return next();
        }
        return result;
    }

    @ThreadSafe
    public synchronized void close() {
        if (closed)
            return;
        preClose = true;
        closed = true;
        // no modification should be made to eventLoops now
        ArrayList<EventLoopWrapper> ls = eventLoops;
        for (EventLoopWrapper w : ls) {
            tryCloseLoop(w);
        }
        eventLoops.clear();
        removeResources();
    }

    /*
     * ========================
     * END group function
     * ========================
     */
}
