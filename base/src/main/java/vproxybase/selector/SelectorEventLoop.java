package vproxybase.selector;

import vfd.*;
import vproxybase.Config;
import vproxybase.GlobalInspection;
import vproxybase.selector.wrap.FDInspection;
import vproxybase.selector.wrap.WrappedSelector;
import vproxybase.util.*;
import vproxybase.util.promise.Promise;
import vproxybase.util.time.TimeQueue;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

public class SelectorEventLoop {
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

    private SelectorEventLoop(FDs fds) throws IOException {
        this.selector = new WrappedSelector(fds.openSelector());
        this.fds = fds;
        if (VFDConfig.useFStack) {
            CLOSE_LOCK = Lock.createMock();
        } else {
            CLOSE_LOCK = Lock.create();
        }
    }

    private static volatile SelectorEventLoop theLoop = null; // this field is used when using fstack

    public static SelectorEventLoop open() throws IOException {
        if (VFDConfig.useFStack) {
            // we use only one event loop if it's using f-stack
            // considering the program code base, it will take too much time
            // modifying code everywhere,
            // so we let all the components share the same event loop
            // and loop is handled at the entrance of the program
            if (theLoop == null) {
                synchronized (SelectorEventLoop.class) {
                    if (theLoop == null) {
                        theLoop = new SelectorEventLoop(FDProvider.get().getProvided());
                    }
                }
            }
            return theLoop;
            // no need to consider whether the loop would be closed
            // when the loop closes, the program will exit
        }
        return new SelectorEventLoop(FDProvider.get().getProvided());
    }

    public static SelectorEventLoop open(FDs fds) throws IOException {
        if (VFDConfig.useFStack) {
            if (FDProvider.get().getProvided() == fds) {
                throw new IllegalArgumentException("should not call SelectorEventLoop.open(fds) with the default fds impl");
            }
        }
        return new SelectorEventLoop(fds);
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
    }

    @Blocking // will block until the loop actually starts
    public void loop(Function<Runnable, ? extends VProxyThread> constructThread) {
        if (VFDConfig.useFStack && fds == FDProvider.get().getProvided()) {
            // f-stack programs should have only one thread and let ff_loop run the callback instead of running loop ourselves
            return;
        }
        constructThread.apply(this::loop).start();
        while (runningThread == null) {
            try {
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

        final Collection<SelectedEntry> selected;
        try {
            if (VFDConfig.useFStack && fds == FDProvider.get().getProvided()) { // f-stack main loop does not wait
                selected = selector.selectNow();
            } else if (timeQueue.isEmpty() && runOnLoopEvents.isEmpty()) {
                selected = selector.select(); // let it sleep
            } else if (!runOnLoopEvents.isEmpty()) {
                selected = selector.selectNow(); // immediately return when tasks registered into the loop
            } else if (!channelsToBeRegisteredStep1.isEmpty() || !channelsToBeRegisteredStep2.isEmpty()) {
                selected = selector.selectNow(); // immediately return when channels are going to be registered
            } else {
                int time = timeQueue.nextTime(Config.currentTimestamp);
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
        if (VFDConfig.useFStack && fds == FDProvider.get().getProvided()) {
            // when using f-stack, the MAIN loop is started by ff_loop (non-main loops can still run)
            // we should not start any loop inside ff_loop
            // but considering the base code, this method is used everywhere
            // so we simply block the thread and never exit

            // there is no need to consider to break the loop when selector closes
            // echo f-stack program only have one main thread, and that means one loop only

            //noinspection InfiniteLoopStatement
            while (true) {
                try {
                    Thread.sleep(365L * 24 * 60 * 60 * 1000 /*very long time, 1 year*/);
                } catch (Throwable ignore) {
                }
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

    private void doModify(FD fd, EventSet ops) {
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
        // we don not need to wakeup manually, the selector.close does this for us

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
}
