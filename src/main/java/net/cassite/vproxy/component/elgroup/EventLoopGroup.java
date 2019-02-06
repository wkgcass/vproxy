package net.cassite.vproxy.component.elgroup;

import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.ClosedException;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.util.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class EventLoopGroup {
    public final String alias;
    private ArrayList<EventLoopWrapper> eventLoops = new ArrayList<>(0); // use array list to make code look better,
    // it's the same if you use array
    private boolean preClose = false; // if true, then all MODIFY operations are disabled or return default value
    private boolean closed = false; // if true, then all operations are disabled or return default value
    private final AtomicInteger cursor = new AtomicInteger(0); // current cursor of the eventLoops
    private final ConcurrentHashSet<EventLoopGroupAttach> attaches = new ConcurrentHashSet<>();

    public EventLoopGroup(String alias) {
        this.alias = alias;
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
        SelectorEventLoop selectorEventLoop = SelectorEventLoop.open();
        EventLoopWrapper el = new EventLoopWrapper(alias, selectorEventLoop);
        ArrayList<EventLoopWrapper> newLs = new ArrayList<>(ls.size() + 1);
        newLs.addAll(ls);
        newLs.add(el);
        eventLoops = newLs;
        el.loop();

        assert Logger.lowLevelDebug("event loop added " + alias);

        invokeResourcesOnAdd();
    }

    @Blocking
    private void tryCloseLoop(SelectorEventLoop selectorEventLoop) {
        try {
            selectorEventLoop.close();
        } catch (IOException e) {
            Logger.fatal(LogType.EVENT_LOOP_CLOSE_FAIL, "close event loop failed, err = " + e);
        }
    }

    @Blocking
    // closing selectorEventLoop is blocking, so this is blocking as well
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
                tryCloseLoop(w.getSelectorEventLoop());
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
        return attaches.stream().map(EventLoopGroupAttach::id).collect(Collectors.toList());
    }

    @ThreadSafe
    public void attachResource(EventLoopGroupAttach resource) throws AlreadyExistException, ClosedException {
        if (preClose)
            throw new ClosedException();
        if (!attaches.add(resource)) {
            throw new AlreadyExistException();
        }
    }

    @ThreadSafe
    public void detachResource(EventLoopGroupAttach resource) throws NotFoundException {
        if (preClose)
            return;
        if (!attaches.remove(resource)) {
            throw new NotFoundException();
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

    public synchronized void setPreClose(boolean preClose) {
        this.preClose = preClose;
    }

    @ThreadSafe
    public EventLoopWrapper next() {
        if (preClose)
            return null;

        ArrayList<EventLoopWrapper> ls = eventLoops;
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

    @Blocking
    // closing selectorEventLoop is blocking, so this is blocking as well
    @ThreadSafe
    public synchronized void close() {
        if (closed)
            return;
        preClose = true;
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
}
