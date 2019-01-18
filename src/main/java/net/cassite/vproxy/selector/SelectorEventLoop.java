package net.cassite.vproxy.selector;

import net.cassite.vproxy.util.Logger;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SelectorEventLoop {
    static class RegisterData {
        Handler handler;
        Object att;
    }

    final Selector selector;
    private final ConcurrentMap<SelectableChannel, RegisterData> registered = new ConcurrentHashMap<>();
    private final HandlerContext ctx = new HandlerContext(this); // always reuse the ctx object

    private SelectorEventLoop() throws IOException {
        this.selector = Selector.open();
    }

    public static SelectorEventLoop open() throws IOException {
        return new SelectorEventLoop();
    }

    private int calculateWaitTimeout() {
        return 1000;
    }

    private void doBeforeHandlingEvents() {
        // TODO timer
    }

    private void doAfterHandlingEvents() {
        // TODO timer
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
                Logger.stderr("channel is closed but still firing");
            } else {
                int readyOps = key.readyOps();
                if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
                    handler.connected(ctx);
                } else if ((readyOps & SelectionKey.OP_ACCEPT) != 0) {
                    handler.accept(ctx);
                } else if ((readyOps & SelectionKey.OP_READ) != 0) {
                    handler.readable(ctx);
                } else {
                    assert (readyOps & SelectionKey.OP_WRITE) != 0;
                    handler.writable(ctx);
                }
            }
        }
    }

    public void loop() {
        while (selector.isOpen()) {
            final int selectedSize;
            try {
                selectedSize = selector.select(calculateWaitTimeout());
            } catch (IOException e) {
                // let's ignore this exception and continue
                continue;
            }
            doBeforeHandlingEvents();
            if (selectedSize > 0) {
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                doHandling(keys);
            }
            doAfterHandlingEvents();
        }
        // remove all still registered keys
        Iterator<Map.Entry<SelectableChannel, RegisterData>> ite = registered.entrySet().iterator();
        while (ite.hasNext()) {
            Map.Entry<SelectableChannel, RegisterData> entry = ite.next();
            ite.remove();

            triggerRemovedCallback(entry.getKey(), entry.getValue());
        }
    }

    @SuppressWarnings("DuplicateThrows")
    public <CHANNEL extends SelectableChannel> void add(CHANNEL channel, int ops, Object attachment, Handler<CHANNEL> handler) throws ClosedChannelException, IOException {
        channel.configureBlocking(false);
        RegisterData registerData = new RegisterData();
        registerData.att = attachment;
        registerData.handler = handler;
        channel.register(selector, ops, registerData);
        registered.put(channel, registerData);
    }

    public void modify(SelectableChannel channel, int ops) {
        SelectionKey key = channel.keyFor(selector);
        if (key == null)
            throw new IllegalArgumentException("channel is not registered with this selector");
        key.interestOps(ops);
    }

    public void addOps(SelectableChannel channel, int ops) {
        SelectionKey key = channel.keyFor(selector);
        if (key == null)
            throw new IllegalArgumentException("channel is not registered with this selector");
        key.interestOps(key.interestOps() | ops);
    }

    public void rmOps(SelectableChannel channel, int ops) {
        SelectionKey key = channel.keyFor(selector);
        if (key == null)
            throw new IllegalArgumentException("channel is not registered with this selector");
        key.interestOps(key.interestOps() & ~ops);
    }

    public void remove(SelectableChannel channel) {
        channel.keyFor(selector).cancel();
        triggerRemovedCallback(channel, registered.remove(channel));
    }

    @SuppressWarnings("unchecked")
    private void triggerRemovedCallback(SelectableChannel channel, RegisterData registerData) {
        assert registerData != null;
        ctx.channel = channel;
        ctx.attachment = registerData.att;
        registerData.handler.removed(ctx);
    }

    public boolean isClosed() {
        return !selector.isOpen();
    }

    public void close() throws IOException {
        selector.close();
    }
}
