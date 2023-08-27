package io.vproxy.base.selector;

import io.vproxy.base.Config;
import io.vproxy.base.GlobalInspection;
import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.selector.wrap.FDInspection;
import io.vproxy.base.selector.wrap.WrappedSelector;
import io.vproxy.base.util.Lock;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.anno.Blocking;
import io.vproxy.base.util.anno.ThreadSafe;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.base.util.promise.Promise;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.base.util.time.TimeQueue;
import io.vproxy.vfd.*;
import io.vproxy.vfd.posix.AEFiredExtra;
import io.vproxy.vfd.posix.AESelector;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

@SuppressWarnings("rawtypes")
public class SelectorEventLoop implements AutoCloseable {
    static class RegisterData {
        boolean connected = false;
        final Handler handler;
        final Object att;

        RegisterData(Handler handler, Object att) {
            this.handler = handler;
            this.att = att;
        }
    }

    static class AddFdData {
        final FD channel;
        final EventSet ops;
        final RegisterData registerData;
        final Callback<FD, Throwable> callback;

        AddFdData(FD channel, EventSet ops, RegisterData registerData, Callback<FD, Throwable> callback) {
            this.channel = channel;
            this.ops = ops;
            this.registerData = registerData;
            this.callback = callback;
        }
    }

    public static SelectorEventLoop current() {
        return VProxyThread.current().loop;
    }

    public final WrappedSelector selector;
    public final FDs fds;
    private final InitOptions initOptions;
    private final TimeQueue<Runnable> timeQueue = TimeQueue.create();
    private final ConcurrentLinkedQueue<Runnable> runOnLoopEvents = new ConcurrentLinkedQueue<>();

    private final Lock channelRegisteringLock = Lock.create();
    private final ConcurrentLinkedQueue<AddFdData> channelsToBeRegisteredStep1 = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<AddFdData> channelsToBeRegisteredStep2 = new ConcurrentLinkedQueue<>();

    private final HandlerContext ctxReuse0 = new HandlerContext(this); // always reuse the ctx object
    private final HandlerContext ctxReuse1 = new HandlerContext(this);
    private volatile Thread runningThread;

    // these locks are a little tricky
    // see comments in loop() and close()
    private final Lock CLOSE_LOCK;
    private List<Tuple<FD, RegisterData>> THE_KEY_SET_BEFORE_SELECTOR_CLOSE;

    private BeforePollCallback beforePollCallback = null;
    private AfterPollCallback afterPollCallback = null;
    private FinalizerCallback finalizerCallback = null;

    public static final class InitOptions {
        public static final InitOptions DEFAULT = new InitOptions();

        public boolean preferPoll = false;
        public long coreAffinity = -1;
        public int epfd = 0;

        public InitOptions() {
        }

        public InitOptions(InitOptions opts) {
            this.preferPoll = opts.preferPoll;
            this.coreAffinity = opts.coreAffinity;
            this.epfd = opts.epfd;
        }
    }

    private SelectorEventLoop(FDs fds, InitOptions opts) throws IOException {
        FDSelector fdSelector;
        if (fds instanceof FDsWithOpts) {
            fdSelector = ((FDsWithOpts) fds).openSelector(new FDsWithOpts.Options(opts.preferPoll, opts.epfd));
        } else {
            fdSelector = fds.openSelector();
        }
        this.selector = new WrappedSelector(fdSelector);
        this.fds = fds;
        CLOSE_LOCK = Lock.create();
        this.initOptions = new InitOptions(opts);
    }

    private NetEventLoop netEventLoop = null;

    public void setNetEventLoop(NetEventLoop netEventLoop) {
        if (this.netEventLoop != null) {
            throw new IllegalStateException("already assigned with a NetEventLoop");
        }
        if (netEventLoop.getSelectorEventLoop() != this) {
            throw new IllegalArgumentException("input is not using this event loop");
        }
        this.netEventLoop = netEventLoop;
    }

    public NetEventLoop getNetEventLoop() {
        return netEventLoop;
    }

    public NetEventLoop ensureNetEventLoop() {
        //noinspection ReplaceNullCheck
        if (netEventLoop == null) {
            return new NetEventLoop(this);
        } else {
            return netEventLoop;
        }
    }

    public static SelectorEventLoop open() throws IOException {
        return open(InitOptions.DEFAULT);
    }

    public static SelectorEventLoop open(InitOptions opts) throws IOException {
        return new SelectorEventLoop(FDProvider.get().getProvided(), opts);
    }

    public static SelectorEventLoop open(FDs fds) throws IOException {
        return open(fds, InitOptions.DEFAULT);
    }

    public static SelectorEventLoop open(FDs fds, InitOptions opts) throws IOException {
        return new SelectorEventLoop(fds, opts);
    }

    private void tryRunnable(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            // we cannot throw the error, just log
            Logger.error(LogType.IMPROPER_USE, "exception thrown in nextTick event ", t);
        }
    }

    private void handleNonSelectEvents() {
        handleAddingFdEvents();
        handleRunOnLoopEvents();
        handleTimeEvents();
    }

    private void handleAddingFdEvents() {
        //noinspection unused
        try (var x = channelRegisteringLock.lock()) {
            AddFdData data;
            // handle step2 first, because step1 will add elements into the step2 array
            while ((data = channelsToBeRegisteredStep2.poll()) != null) {
                assert Logger.lowLevelDebug("handling registering channel " + data.channel + " when looping");
                try {
                    selector.register(data.channel, data.ops, data.registerData);
                } catch (Throwable t) {
                    Logger.error(LogType.IMPROPER_USE, "the channel " + data.channel + " failed to be added into the event loop", t);
                    data.callback.failed(t);
                    continue;
                }
                data.callback.succeeded(data.channel);
            }

            // handle step1 then
            while ((data = channelsToBeRegisteredStep1.poll()) != null) {
                channelsToBeRegisteredStep2.add(data);
            }
        }
    }

    private void handleRunOnLoopEvents() {
        int len = runOnLoopEvents.size();
        // only run available events when entering this function
        for (int i = 0; i < len; ++i) {
            Runnable r = runOnLoopEvents.poll();
            tryRunnable(r);
        }
    }

    private void handleTimeEvents() {
        List<Runnable> toRun = new LinkedList<>();
        while (timeQueue.nextTime(Config.currentTimestamp) == 0) {
            Runnable r = timeQueue.poll();
            toRun.add(r);
        }
        for (Runnable r : toRun) {
            tryRunnable(r);
        }
    }

    @SuppressWarnings("unchecked")
    private void doHandling(Iterator<SelectedEntry> keys) {
        while (keys.hasNext()) {
            SelectedEntry key = keys.next();

            RegisterData registerData = (RegisterData) key.attachment();

            FD channel = key.fd();
            Handler handler = registerData.handler;

            ctxReuse0.channel = channel;
            ctxReuse0.attachment = registerData.att;

            if (!channel.isOpen()) {
                if (selector.isRegistered(channel)) {
                    Logger.error(LogType.CONN_ERROR, "channel is closed but still firing: fd = " + channel + ", event = " + key.ready() + ", attachment = " + ctxReuse0.attachment);
                } // else the channel is closed in another fd handler and removed from loop, this is ok and no need to report
            } else {
                EventSet readyOps = key.ready();
                // handle read first because it's most likely to happen
                if (readyOps.have(Event.READABLE)) {
                    assert Logger.lowLevelDebug("firing readable for " + channel);
                    if (channel instanceof ServerSocketFD) {
                        // OP_ACCEPT
                        try {
                            handler.accept(ctxReuse0);
                        } catch (Throwable t) {
                            Logger.error(LogType.IMPROPER_USE, "the accept callback got exception", t);
                        }
                    } else {
                        try {
                            handler.readable(ctxReuse0);
                        } catch (Throwable t) {
                            Logger.error(LogType.IMPROPER_USE, "the readable callback got exception", t);
                        }
                    }
                }
                // read and write may happen in the same loop round
                if (readyOps.have(Event.WRITABLE)) {
                    assert Logger.lowLevelDebug("firing writable for " + channel);
                    if (channel instanceof SocketFD) {
                        if (registerData.connected) {
                            try {
                                handler.writable(ctxReuse0);
                            } catch (Throwable t) {
                                Logger.error(LogType.IMPROPER_USE, "the writable callback got exception", t);
                            }
                        } else {
                            registerData.connected = true;
                            try {
                                handler.connected(ctxReuse0);
                            } catch (Throwable t) {
                                Logger.error(LogType.IMPROPER_USE, "the connected callback got exception", t);
                            }
                        }
                    }
                }
            }
        }
    }

    private void release() {
        for (Tuple<FD, RegisterData> tuple : THE_KEY_SET_BEFORE_SELECTOR_CLOSE) {
            FD channel = tuple.left;
            RegisterData att = tuple.right;
            triggerRemovedCallback(channel, att);
        }
        runFinalizer();
    }

    @Blocking // will block until the loop actually starts
    public void loop(Function<Runnable, ? extends VProxyThread> constructThread) {
        constructThread.apply(this::loop).start();
        while (runningThread == null) {
            try {
                //noinspection BusyWait
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // ignore the interruption
            }
        }
    }

    // this method is for f-stack
    // do not use it anywhere else
    public void _bindThread0() {
        runningThread = Thread.currentThread();
        VProxyThread.current().loop = this;
    }

    // return -1 for break
    // return  0 for continue
    @Blocking
    public int onePoll() {
        //noinspection unused
        try (var unused = CLOSE_LOCK.lock()) {
            // yes, we lock the whole while body (except the select part)
            // it's ok because we won't close the loop from inside the loop
            // and it's also ok to let the operator wait for some time
            // since closing the loop is considered as expansive and dangerous

            if (!selector.isOpen())
                return -1; // break if it's closed

            // handle some non select events
            Config.currentTimestamp = fds.currentTimeMillis();
            handleNonSelectEvents();
        }
        // here we do not lock select()
        // let close() have chance to run

        int maxSleepMillis = runBeforePoll();

        final Collection<SelectedEntry> selected;
        try {
            if (timeQueue.isEmpty() && runOnLoopEvents.isEmpty() && maxSleepMillis < 0) {
                selected = selector.select(); // let it sleep
            } else if (!runOnLoopEvents.isEmpty()) {
                selected = selector.selectNow(); // immediately return when tasks registered into the loop
            } else if (!channelsToBeRegisteredStep1.isEmpty() || !channelsToBeRegisteredStep2.isEmpty()) {
                selected = selector.selectNow(); // immediately return when channels are going to be registered
            } else {
                int time = timeQueue.nextTime(Config.currentTimestamp);
                if (time > maxSleepMillis && maxSleepMillis >= 0) {
                    time = maxSleepMillis;
                }
                if (time == 0) {
                    selected = selector.selectNow(); // immediately return
                } else {
                    selected = selector.select(time); // wait until the nearest timer
                }
            }
        } catch (IOException | ClosedSelectorException e) {
            // let's ignore this exception and continue
            // if it's closed, the next loop will not run
            return 0;
        }

        if (runAfterPoll()) {
            Logger.warn(LogType.ALERT, "event loop terminates because afterPoll callback returns true");
            return -1;
        }

        // here we lock again
        // because we need to handle something
        // and at this time the selector might be closed
        //noinspection unused
        try (var unused = CLOSE_LOCK.lock()) {

            if (!selector.isOpen())
                return -1; // break if it's closed

            if (!selected.isEmpty()) {
                Iterator<SelectedEntry> keys = selected.iterator();
                doHandling(keys);
            }
        }
        return 0;
    }

    @Blocking
    public void loop() {
        // try to set core affinity
        if (this.initOptions.coreAffinity != -1) {
            boolean set = false;
            if (fds instanceof FDsWithCoreAffinity) {
                try {
                    ((FDsWithCoreAffinity) fds).setCoreAffinity(initOptions.coreAffinity);
                    set = true;
                } catch (IOException e) {
                    Logger.error(LogType.SYS_ERROR, "setting core affinity to " + Long.toBinaryString(initOptions.coreAffinity) + " failed", e);
                    // just keep running without affinity
                }
            }
            if (set) {
                Logger.alert("core affinity set: " + Long.toBinaryString(initOptions.coreAffinity));
            } else {
                Logger.warn(LogType.ALERT, "core affinity is not set (" + Long.toBinaryString(initOptions.coreAffinity) + "), continue without core affinity");
            }
        }

        // set thread
        runningThread = Thread.currentThread();
        GlobalInspection.getInstance().registerSelectorEventLoop(this);
        VProxyThread.current().loop = this;
        // run
        while (selector.isOpen()) {
            if (-1 == onePoll()) {
                break;
            }
        }
        GlobalInspection.getInstance().deregisterSelectorEventLoop(this);
        runningThread = null; // it's not running now, set to null
        VProxyThread.current().loop = null; // remove from thread local
        // do the final release
        release();
    }

    private boolean needWake() {
        return runningThread != null && Thread.currentThread() != runningThread;
    }

    private void wakeup() {
        selector.wakeup();
    }

    @ThreadSafe
    public void nextTick(Runnable r) {
        runOnLoopEvents.add(r);
        if (!needWake())
            return; // we do not need to wakeup because it's not started or is already waken up
        wakeup(); // wake the selector because new event is added
    }

    @ThreadSafe
    public void doubleNextTick(Runnable r) {
        nextTick(() -> nextTick(r));
    }

    @ThreadSafe
    public void runOnLoop(Runnable r) {
        if (!needWake()) {
            tryRunnable(r); // directly run if is already on the loop thread
        } else {
            nextTick(r); // otherwise push into queue
        }
    }

    @ThreadSafe
    public TimerEvent delay(int timeout, Runnable r) {
        TimerEvent e = new TimerEvent(this);
        // timeQueue is not thread safe
        // modify it in the event loop's thread
        nextTick(() -> e.setEvent(timeQueue.add(Config.currentTimestamp, timeout, r)));
        return e;
    }

    @ThreadSafe
    public PeriodicEvent period(int timeout, Runnable r) {
        PeriodicEvent pe = new PeriodicEvent(r, this, timeout);
        pe.start();
        return pe;
    }

    // return null if it's directly added successfully
    // otherwise it will be added nextTick(() -> nextTick(() -> {...here...}))
    // the caller may need to handle this situation with the returned future object
    @ThreadSafe
    @SuppressWarnings("DuplicateThrows")
    public <CHANNEL extends FD> Promise<FD> add(CHANNEL channel, EventSet ops, Object attachment, Handler<CHANNEL> handler) throws ClosedChannelException, IOException {
        if (!channel.loopAware(this)) {
            throw new IOException("channel " + channel + " rejects to be attached to current event loop");
        }
        channel.configureBlocking(false);
        RegisterData registerData = new RegisterData(handler, attachment);
        if (channel instanceof SocketFD) {
            registerData.connected = ((SocketFD) channel).isConnected();
        }
        Promise<FD> addingPromise = add0(channel, ops, registerData);
        if (addingPromise == null) {
            if (needWake()) {
                wakeup();
            }
            return null;
        } else {
            return addingPromise;
        }
    }

    // a helper function for adding a channel into the selector
    private Promise<FD> add0(FD channel, EventSet ops, RegisterData registerData) throws IOException {
        if (initOptions.preferPoll && Thread.currentThread() != runningThread) {
            return add0PreferPoll(channel, ops, registerData);
        }

        try {
            selector.register(channel, ops, registerData);
        } catch (CancelledKeyException e) {
            // the key might still being processed
            // but is canceled
            // it will be removed after handled
            // so we re-run this in next-next tick
            // the next tick to ensure this key is removed
            // then next next tick we can register

            assert Logger.lowLevelDebug("key already canceled, we register it on next tick after keys are handled");

            var tup = Promise.<FD>todo();
            channelsToBeRegisteredStep1.add(new AddFdData(channel, ops, registerData, tup.right));
            if (needWake()) {
                wakeup();
            }
            return tup.left;
        }
        return null;
    }

    private Promise<FD> add0PreferPoll(FD channel, EventSet ops, RegisterData registerData) {
        assert Logger.lowLevelDebug("preferPoll enabled, so must wake it up to let the new fd take effect");
        return Promise.wrap(cb -> runOnLoop(() -> {
            Promise<FD> res;
            try {
                res = add0(channel, ops, registerData);
            } catch (Throwable t) {
                cb.failed(t);
                return;
            }
            if (res == null) cb.succeeded(channel);
            else res.setHandler((fd, err) -> {
                if (err != null) cb.failed(err);
                else cb.succeeded(fd);
            });
        }));
    }

    private void doModify(FD fd, EventSet ops) {
        if (initOptions.preferPoll && Thread.currentThread() != runningThread) {
            assert Logger.lowLevelDebug("preferPoll enabled, so must wake it up to update the fd");
            runOnLoop(() -> doModify(fd, ops));
            return;
        }

        if (selector.events(fd).equals(ops)) {
            return; // no need to update if they are the same
        }
        selector.modify(fd, ops);
        if (needWake()) {
            wakeup();
        }
    }

    @ThreadSafe
    public void modify(FD channel, EventSet ops) {
        doModify(channel, ops);
    }

    @ThreadSafe
    public void addOps(FD channel, EventSet ops) {
        var old = selector.events(channel);
        doModify(channel, old.combine(ops));
    }

    @ThreadSafe
    public void rmOps(FD channel, EventSet ops) {
        var old = selector.events(channel);
        doModify(channel, old.reduce(ops));
    }

    @ThreadSafe
    public void remove(FD channel) {
        if (initOptions.preferPoll && Thread.currentThread() != runningThread) {
            assert Logger.lowLevelDebug("preferPoll enabled, so must wake it up to let the fd removed");
            runOnLoop(() -> remove(channel));
            return;
        }

        //noinspection unused
        try (var x = channelRegisteringLock.lock()) {
            channelsToBeRegisteredStep1.removeIf(e -> e.channel == channel);
            channelsToBeRegisteredStep2.removeIf(e -> e.channel == channel);
        }

        RegisterData att;

        // synchronize the channel
        // to prevent it being canceled from multiple threads
        synchronized (channel) {
            if (!selector.isRegistered(channel))
                return;
            att = (RegisterData) selector.attachment(channel);
        }

        selector.remove(channel);
        if (needWake()) {
            wakeup();
        }
        triggerRemovedCallback(channel, att);
    }

    @ThreadSafe
    public EventSet getOps(FD channel) {
        return selector.events(channel);
    }

    @ThreadSafe
    public Object getAtt(FD channel) {
        return ((RegisterData) selector.attachment(channel)).att;
    }

    public Thread getRunningThread() {
        return runningThread;
    }

    @SuppressWarnings("unchecked")
    private void triggerRemovedCallback(FD channel, RegisterData registerData) {
        assert registerData != null;
        ctxReuse1.channel = channel;
        ctxReuse1.attachment = registerData.att;
        try {
            registerData.handler.removed(ctxReuse1);
        } catch (Throwable t) {
            Logger.error(LogType.IMPROPER_USE, "the removed callback got exception", t);
        }
    }

    @ThreadSafe
    public boolean isClosed() {
        return !selector.isOpen();
    }

    @Override
    @Blocking
    // wait until it's actually closed if closing on a non event loop thread
    @ThreadSafe
    public void close() throws IOException {
        Thread runningThread = this.runningThread; // get the thread, which will be joined later
        // we don't check whether the thread exists (i.e. we don't check whether the loop is running)
        // just shut down all keys and close the selector
        // the nio lib will raise error if it's already closed

        //noinspection unused
        try (var unused = CLOSE_LOCK.lock()) {
            Collection<RegisterEntry> keys = selector.entries();
            while (true) {
                THE_KEY_SET_BEFORE_SELECTOR_CLOSE = new ArrayList<>(keys.size() + channelsToBeRegisteredStep1.size() + channelsToBeRegisteredStep2.size());
                try {
                    for (RegisterEntry key : keys) {
                        THE_KEY_SET_BEFORE_SELECTOR_CLOSE.add(new Tuple<>(key.fd, (RegisterData) key.attachment));
                    }
                } catch (ConcurrentModificationException ignore) {
                    // there might be adding and removing occur when closing the selector
                    // but we do not lock them for performance concern
                    // we simply catch the exception and re-try
                    continue;
                }
                // i believe there's no need to set a `closed` flag
                // because the copy is very fast
                // and usually we don't close a event loop
                // re-try would be just fine
                break;
            }
            AddFdData add;
            while ((add = channelsToBeRegisteredStep1.poll()) != null) {
                THE_KEY_SET_BEFORE_SELECTOR_CLOSE.add(new Tuple<>(add.channel, add.registerData));
            }
            while ((add = channelsToBeRegisteredStep2.poll()) != null) {
                THE_KEY_SET_BEFORE_SELECTOR_CLOSE.add(new Tuple<>(add.channel, add.registerData));
            }
            selector.close();
        }
        // wakeup();
        // we do not need to wakeup manually, the selector.close does this for us

        if (runningThread != null && runningThread != Thread.currentThread()) {
            try {
                runningThread.join();
            } catch (InterruptedException ignore) {
                // ignore, we don't care
            }
        }
    }

    public void copyChannels(Collection<FDInspection> coll, Runnable cb) {
        runOnLoop(() -> {
            selector.copyFDEvents(coll);
            cb.run();
        });
    }


    public interface BeforePollCallback {
        /**
         * @return max sleep millis, -1 for no limit
         */
        int run();
    }

    private int runBeforePoll() {
        var beforePollCallback = this.beforePollCallback;
        if (beforePollCallback == null)
            return -1;
        return beforePollCallback.run();
    }

    public void setBeforePoll(BeforePollCallback cb) {
        beforePollCallback = cb;
    }

    public interface AfterPollCallback {
        /**
         * @return true to break, false to continue
         */
        boolean run(int num, AEFiredExtra.Array array);
    }

    private boolean runAfterPoll() {
        var afterPollCallback = this.afterPollCallback;
        if (afterPollCallback == null)
            return false;
        var selector = this.selector.getSelector();
        if (selector instanceof AESelector ae) {
            var num = ae.getFiredExtraNum();
            var arr = ae.getFiredExtra();
            return afterPollCallback.run(num, arr);
        } else {
            return afterPollCallback.run(0, null);
        }
    }

    public void setAfterPoll(AfterPollCallback cb) {
        afterPollCallback = cb;
    }

    public interface FinalizerCallback {
        void run();
    }

    private void runFinalizer() {
        var finalizerCallback = this.finalizerCallback;
        if (finalizerCallback == null)
            return;
        finalizerCallback.run();
    }

    public void setFinalizer(FinalizerCallback cb) {
        finalizerCallback = cb;
    }
}
