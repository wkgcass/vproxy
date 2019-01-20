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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class EventLoopGroup {
    public final String alias;
    private ArrayList<Tuple<EventLoopWrapper, SelectorEventLoop>> eventLoops = new ArrayList<>(0); // use array list to make code look better,
    // it's the same if you use array
    private boolean preClose = false; // if true, then all MODIFY operations are disabled or return default value
    private boolean closed = false; // if true, then all operations are disabled or return default value
    private final AtomicInteger cursor = new AtomicInteger(0); // current cursor of the eventLoops
    private final ConcurrentMap<String, EventLoopGroupAttach> attaches = new ConcurrentHashMap<>();

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
        return eventLoops.stream().map(el -> el.a.alias).collect(Collectors.toList());
    }

    @ThreadSafe
    public Tuple<EventLoopWrapper, SelectorEventLoop> get(String alias) throws NotFoundException {
        if (closed) {
            throw new NotFoundException();
        }
        ArrayList<Tuple<EventLoopWrapper, SelectorEventLoop>> ls = eventLoops;
        for (Tuple<EventLoopWrapper, SelectorEventLoop> t : ls) {
            if (t.a.alias.equals(alias))
                return t;
        }
        throw new NotFoundException();
    }

    @ThreadSafe
    public synchronized void add(String alias) throws AlreadyExistException, IOException, ClosedException {
        if (preClose) {
            throw new ClosedException();
        }
        ArrayList<Tuple<EventLoopWrapper, SelectorEventLoop>> ls = eventLoops;
        for (Tuple<EventLoopWrapper, SelectorEventLoop> t : ls) {
            if (t.a.alias.equals(alias))
                throw new AlreadyExistException();
        }
        SelectorEventLoop selectorEventLoop = SelectorEventLoop.open();
        EventLoopWrapper el = new EventLoopWrapper(alias, selectorEventLoop);
        ArrayList<Tuple<EventLoopWrapper, SelectorEventLoop>> newLs = new ArrayList<>(ls.size() + 1);
        newLs.addAll(ls);
        newLs.add(new Tuple<>(el, selectorEventLoop));
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
        ArrayList<Tuple<EventLoopWrapper, SelectorEventLoop>> ls = eventLoops;
        ArrayList<Tuple<EventLoopWrapper, SelectorEventLoop>> newLs = new ArrayList<>(ls.size() - 1);
        boolean found = false;
        for (Tuple<EventLoopWrapper, SelectorEventLoop> t : ls) {
            if (t.a.alias.equals(alias)) {
                found = true;
                tryCloseLoop(t.b);
            } else {
                newLs.add(t);
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
    public Tuple<EventLoopWrapper, SelectorEventLoop> next() {
        if (preClose)
            return null;

        ArrayList<Tuple<EventLoopWrapper, SelectorEventLoop>> ls = eventLoops;
        return next(ls, 0);
    }

    private Tuple<EventLoopWrapper, SelectorEventLoop> next(ArrayList<Tuple<EventLoopWrapper, SelectorEventLoop>> ls, int recursion) {
        if (recursion > ls.size())
            return null;
        ++recursion;

        Tuple<EventLoopWrapper, SelectorEventLoop> result;

        int idx = cursor.getAndIncrement();
        if (ls.size() > idx) {
            result = ls.get(idx);
        } else if (ls.isEmpty()) {
            return null;
        } else {
            cursor.set(1);
            result = ls.get(0);
        }
        if (result.b.isClosed()) {
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
        ArrayList<Tuple<EventLoopWrapper, SelectorEventLoop>> ls = eventLoops;
        for (Tuple<EventLoopWrapper, SelectorEventLoop> t : ls) {
            tryCloseLoop(t.b);
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
