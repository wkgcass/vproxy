package vproxy.selector;

import vproxy.app.Config;
import vproxy.util.*;

import java.io.IOException;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

public class SelectorEventLoop {
    static class RegisterData {
        Handler handler;
        Object att;
    }

    private static final ThreadLocal<SelectorEventLoop> loopThreadLocal = new ThreadLocal<>();

    public static SelectorEventLoop current() {
        return loopThreadLocal.get();
    }

    private final Selector selector;
    private final TimeQueue<Runnable> timeQueue = new TimeQueue<>();
    private final ConcurrentLinkedQueue<Runnable> runOnLoopEvents = new ConcurrentLinkedQueue<>();
    private final HandlerContext ctx = new HandlerContext(this); // always reuse the ctx object
    public volatile Thread runningThread;

    // these locks are a little tricky
    // see comments in loop() and close()
    private final Object CLOSE_LOCK = new Object();
    private List<Tuple<SelectableChannel, RegisterData>> THE_KEY_SET_BEFORE_SELECTOR_CLOSE;

    private SelectorEventLoop() throws IOException {
        this.selector = Selector.open();
    }

    public static SelectorEventLoop open() throws IOException {
        return new SelectorEventLoop();
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
        handleRunOnLoopEvents();
        handleTimeEvents();
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
        while (timeQueue.nextTime() == 0) {
            Runnable r = timeQueue.pop();
            toRun.add(r);
        }
        for (Runnable r : toRun) {
            tryRunnable(r);
        }
    }

    @SuppressWarnings("unchecked")
    private void doHandling(Iterator<SelectionKey> keys) {
        while (keys.hasNext()) {
            SelectionKey key = keys.next();
            keys.remove();

            RegisterData registerData = (RegisterData) key.attachment();

            SelectableChannel channel = key.channel();
            Handler handler = registerData.handler;

            ctx.channel = channel;
            ctx.attachment = registerData.att;

            if (!key.isValid()) {
                //noinspection UnnecessaryContinue
                continue;
            } else if (!channel.isOpen()) {
                Logger.error(LogType.CONN_ERROR, "channel is closed but still firing");
            } else {
                int readyOps = key.readyOps();
                // handle read first because it's most likely to happen
                if ((readyOps & SelectionKey.OP_READ) != 0) {
                    try {
                        handler.readable(ctx);
                    } catch (Throwable t) {
                        Logger.error(LogType.IMPROPER_USE, "the readable callback got exception", t);
                    }
                } else if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
                    try {
                        handler.connected(ctx);
                    } catch (Throwable t) {
                        Logger.error(LogType.IMPROPER_USE, "the connected callback got exception", t);
                    }
                } else if ((readyOps & SelectionKey.OP_ACCEPT) != 0) {
                    try {
                        handler.accept(ctx);
                    } catch (Throwable t) {
                        Logger.error(LogType.IMPROPER_USE, "the accept callback got exception", t);
                    }
                }
                // read and write may happen in the same loop round
                if ((readyOps & SelectionKey.OP_WRITE) != 0) {
                    try {
                        handler.writable(ctx);
                    } catch (Throwable t) {
                        Logger.error(LogType.IMPROPER_USE, "the writable callback got exception", t);
                    }
                }
            }
        }
    }

    private void release() {
        for (Tuple<SelectableChannel, RegisterData> tuple : THE_KEY_SET_BEFORE_SELECTOR_CLOSE) {
            SelectableChannel channel = tuple.left;
            RegisterData att = tuple.right;
            triggerRemovedCallback(channel, att);
        }
    }

    @Blocking // will block until the loop actually starts
    public void loop(Function<Runnable, Thread> constructThread) {
        constructThread.apply(this::loop).start();
        while (runningThread == null) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // ignore the interruption
            }
        }
    }

    @Blocking
    public void loop() {
        // set thread
        runningThread = Thread.currentThread();
        loopThreadLocal.set(this);
        // run
        while (selector.isOpen()) {
            synchronized (CLOSE_LOCK) {
                // yes, we lock the whole while body (except the select part)
                // it's ok because we won't close the loop from inside the loop
                // and it's also ok to let the operator wait for some time
                // since closing the loop is considered as expansive and dangerous

                if (!selector.isOpen())
                    break; // break if it's closed

                // handle some non select events
                Config.currentTimestamp = System.currentTimeMillis();
                handleNonSelectEvents();
            }
            // here we do not lock select()
            // let close() have chance to run

            final int selectedSize;
            try {
                if (timeQueue.isEmpty() && runOnLoopEvents.isEmpty()) {
                    selectedSize = selector.select(); // let it sleep
                } else if (!runOnLoopEvents.isEmpty()) {
                    selectedSize = selector.selectNow(); // immediately return
                } else {
                    int time = timeQueue.nextTime();
                    if (time == 0) {
                        selectedSize = selector.selectNow(); // immediately return
                    } else {
                        selectedSize = selector.select(time); // wait until the nearest timer
                    }
                }
            } catch (IOException | ClosedSelectorException e) {
                // let's ignore this exception and continue
                // if it's closed, the next loop will not run
                continue;
            }

            // here we lock again
            // because we need to handle something
            // and at this time the selector might be closed
            synchronized (CLOSE_LOCK) {

                if (!selector.isOpen())
                    break; // break if it's closed

                if (selectedSize > 0) {
                    Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                    doHandling(keys);
                }
            }
            // while-loop ends here
        }
        runningThread = null; // it's not running now, set to null
        loopThreadLocal.remove(); // remove from thread local
        // do the final release
        release();
    }

    private boolean needWake() {
        return runningThread != null && Thread.currentThread() != runningThread;
    }

    @ThreadSafe
    public void nextTick(Runnable r) {
        runOnLoopEvents.add(r);
        if (runningThread == null || Thread.currentThread() == runningThread)
            return; // we do not need to wakeup because it's not started or is already waken up
        selector.wakeup(); // wake the selector because new event is added
    }

    @ThreadSafe
    public void runOnLoop(Runnable r) {
        if (runningThread == null || Thread.currentThread() == runningThread) {
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
        nextTick(() -> e.setEvent(timeQueue.push(timeout, r)));
        return e;
    }

    @ThreadSafe
    public PeriodicEvent period(int timeout, Runnable r) {
        PeriodicEvent pe = new PeriodicEvent(r, this, timeout);
        pe.start();
        return pe;
    }

    @ThreadSafe
    @SuppressWarnings("DuplicateThrows")
    public <CHANNEL extends SelectableChannel> void add(CHANNEL channel, int ops, Object attachment, Handler<CHANNEL> handler) throws ClosedChannelException, IOException {
        channel.configureBlocking(false);
        RegisterData registerData = new RegisterData();
        registerData.att = attachment;
        registerData.handler = handler;
        if (add0(channel, ops, registerData)) {
            if (needWake()) {
                selector.wakeup();
            }
        }
    }

    // a helper function for adding a channel into the selector
    private boolean add0(SelectableChannel channel, int ops, Object registerData) throws IOException {
        try {
            channel.register(selector, ops, registerData);
        } catch (CancelledKeyException e) {
            // the key might still being processed
            // but is canceled
            // it will be removed after handled
            // so we re-run this in next-next tick
            // the next tick to ensure this key is removed
            // then next next tick we can register

            assert Logger.lowLevelDebug("key already canceled, we register it on next tick after keys are handled");

            nextTick(() -> nextTick(() -> {
                try {
                    channel.register(selector, ops, registerData);
                } catch (ClosedChannelException e1) {
                    // will not happen, if the channel is closed, this statement will not run
                    throw new RuntimeException(e1);
                }
            }));
            return false;
        }
        return true;
    }

    private void doModify(SelectionKey key, int ops) {
        if (key.interestOps() == ops) {
            return;
        }
        key.interestOps(ops);
        if (needWake()) {
            selector.wakeup();
        }
    }

    @ThreadSafe
    public void modify(SelectableChannel channel, int ops) {
        SelectionKey key = getKeyCheckNull(channel);
        doModify(key, ops);
    }

    @ThreadSafe
    public void addOps(SelectableChannel channel, int ops) {
        SelectionKey key = getKeyCheckNull(channel);
        doModify(key, key.interestOps() | ops);
    }

    @ThreadSafe
    public void rmOps(SelectableChannel channel, int ops) {
        SelectionKey key = getKeyCheckNull(channel);
        doModify(key, key.interestOps() & ~ops);
    }

    @ThreadSafe
    public void remove(SelectableChannel channel) {
        SelectionKey key;
        // synchronize the channel
        // to prevent it being canceled from multiple threads
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (channel) {
            key = channel.keyFor(selector);
            if (key == null)
                return;
        }
        RegisterData att = (RegisterData) key.attachment();
        key.cancel();
        if (needWake()) {
            selector.wakeup();
        }
        triggerRemovedCallback(channel, att);
    }

    @ThreadSafe
    public int getOps(SelectableChannel channel) {
        SelectionKey key = getKeyCheckNull(channel);
        return key.interestOps();
    }

    @ThreadSafe
    public Object getAtt(SelectableChannel channel) {
        SelectionKey key = getKeyCheckNull(channel);
        return key.attachment();
    }

    private SelectionKey getKeyCheckNull(SelectableChannel channel) {
        SelectionKey key = channel.keyFor(selector);
        if (key == null)
            throw new IllegalArgumentException("channel is not registered with this selector");
        return key;
    }

    @SuppressWarnings("unchecked")
    private void triggerRemovedCallback(SelectableChannel channel, RegisterData registerData) {
        assert registerData != null;
        ctx.channel = channel;
        ctx.attachment = registerData.att;
        try {
            registerData.handler.removed(ctx);
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

        synchronized (CLOSE_LOCK) {
            Set<SelectionKey> keys = selector.keys();
            while (true) {
                THE_KEY_SET_BEFORE_SELECTOR_CLOSE = new ArrayList<>(keys.size());
                try {
                    for (SelectionKey key : keys) {
                        THE_KEY_SET_BEFORE_SELECTOR_CLOSE.add(new Tuple<>(key.channel(), (RegisterData) key.attachment()));
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
            selector.close();
        }
        // selector.wakeup();
        // we don not need to wakeup manually, the selector.close does this for us

        if (runningThread != null && runningThread != Thread.currentThread()) {
            try {
                runningThread.join();
            } catch (InterruptedException ignore) {
                // ignore, we don't care
            }
        }
    }
}
