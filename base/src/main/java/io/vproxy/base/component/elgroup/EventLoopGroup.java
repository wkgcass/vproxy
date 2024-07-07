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
import io.vproxy.base.util.exception.XException;
import io.vproxy.msquic.MsQuicInitializer;
import io.vproxy.msquic.QuicRegistrationConfigEx;
import io.vproxy.msquic.wrap.ApiTables;
import io.vproxy.msquic.wrap.Registration;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.PNIRef;
import io.vproxy.pni.PooledAllocator;
import io.vproxy.pni.array.IntArray;
import io.vproxy.vfd.FDProvider;
import io.vproxy.vfd.posix.PosixFDs;
import io.vproxy.vfd.windows.WindowsFDs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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

    private final Registration msquicRegistration;

    public EventLoopGroup(String alias) {
        this(alias, new Annotations());
    }

    public EventLoopGroup(String alias, Annotations annotations) {
        this.alias = alias;
        this.annotations = annotations;

        Registration msquicRegistration = null;
        if (annotations.EventLoopGroup_UseMsQuic) {
            try {
                msquicRegistration = initQuic();
            } catch (XException e) {
                throw new RuntimeException(e);
            }
        }
        this.msquicRegistration = msquicRegistration;
    }

    public EventLoopGroup(String alias,
                          @SuppressWarnings("unused") MsQuicInitializer.IsSupported isQuicSupported,
                          Annotations annotations) throws XException {
        Objects.requireNonNull(isQuicSupported);

        this.alias = alias;
        this.annotations = annotations;
        msquicRegistration = initQuic();
    }

    private Registration initQuic() throws XException {
        if (!MsQuicInitializer.isSupported()) {
            throw new XException("msquic is not supported");
        }
        if (!(FDProvider.get().getProvided() instanceof PosixFDs) && !(FDProvider.get().getProvided() instanceof WindowsFDs)) {
            throw new XException("vfd impl (" + FDProvider.get().getProvided() + ") does not support quic, " +
                                 "please add -Dvfd=posix or -Dvfd=windows on startup");
        }

        var api = ApiTables.V2;
        var allocator = PooledAllocator.ofUnsafePooled();
        var ref = PNIRef.of(this);

        try (var tmpAlloc = Allocator.ofConfined()) {
            var conf = new QuicRegistrationConfigEx(tmpAlloc);
            conf.setContext(ref.MEMORY);
            var ret = new IntArray(tmpAlloc, 1);
            var r = api.opts.apiTableQ.openRegistration(conf, ret, allocator);
            if (r == null) {
                throw new XException("failed to create registration: " + ret.get(0));
            }
            return new Registration(new Registration.Options(api, r, allocator));
        } catch (XException e) {
            allocator.close();
            throw e;
        } finally {
            ref.close(); // it will not be used anymore
        }
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
        return add(alias, 0, annotations);
    }

    @Override
    @ThreadSafe
    public synchronized EventLoopWrapper add(String alias, int epfd, Annotations annotations) throws AlreadyExistException, IOException, ClosedException {
        if (closed) {
            throw new ClosedException();
        }
        ArrayList<EventLoopWrapper> ls = eventLoops;
        for (EventLoopWrapper w : ls) {
            if (w.alias.equals(alias))
                throw new AlreadyExistException("event-loop in event-loop-group " + this.alias, alias);
        }
        var opts = new SelectorEventLoop.InitOptions();
        if (this.annotations.EventLoopGroup_PreferPoll) {
            opts.preferPoll = true;
        }
        if (annotations.EventLoop_CoreAffinity != -1) {
            opts.coreAffinity = annotations.EventLoop_CoreAffinity;
        }
        opts.epfd = epfd;
        var selectorEventLoop = SelectorEventLoop.open(opts);
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

    public boolean isClosed() {
        return closed;
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

    public Registration getMsquicRegistration() {
        return msquicRegistration;
    }

    @Override
    public String toString() {
        return alias + " -> annotations " + annotations.toString();
    }
}
