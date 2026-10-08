package io.vproxy.test.cases;

import io.vproxy.base.selector.wrap.VirtualFD;
import io.vproxy.base.selector.wrap.WritableAware;
import io.vproxy.base.selector.wrap.WrappedSelector;
import io.vproxy.vfd.*;
import org.junit.Test;

import java.net.SocketOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class TestVirtualReadySelection {
    private static class Input implements VirtualFD {
        final int id;
        int hashes;
        Input(int id) { this.id = id; }
        public int hashCode() { ++hashes; return id; }
        public boolean isOpen() { return true; }
        public void close() { }
        public void configureBlocking(boolean blocking) { }
        public <T> void setOption(SocketOption<T> name, T value) { }
        public FD real() { return this; }
        public boolean contains(FD fd) { return fd == this; }
        public void onRegister() { }
        public void onRemove() { }
    }

    private static class Hybrid implements FD {
        final Input input;
        Hybrid(Input input) { this.input = input; }
        public int hashCode() { return input.hashCode() + 10000; }
        public boolean isOpen() { return true; }
        public void close() { }
        public void configureBlocking(boolean blocking) { }
        public <T> void setOption(SocketOption<T> name, T value) { }
        public FD real() { return input; }
        public boolean contains(FD fd) { return fd == this || input.contains(fd); }
    }

    private static class Aware extends Input implements WritableAware {
        int callbacks;
        Runnable action = () -> { };
        Aware(int id) { super(id); }
        public void writable() { ++callbacks; action.run(); }
    }

    private static class EmptySelector implements FDSelector {
        boolean open = true;
        public boolean isOpen() { return open; }
        public Collection<SelectedEntry> select() { return List.of(); }
        public Collection<SelectedEntry> selectNow() { return List.of(); }
        public Collection<SelectedEntry> select(long millis) { return List.of(); }
        public void wakeup() { }
        public boolean isRegistered(FD fd) { return false; }
        public void register(FD fd, EventSet ops, Object data) { throw new AssertionError(); }
        public void remove(FD fd) { throw new AssertionError(); }
        public void modify(FD fd, EventSet ops) { throw new AssertionError(); }
        public EventSet events(FD fd) { throw new AssertionError(); }
        public Object attachment(FD fd) { throw new AssertionError(); }
        public Collection<RegisterEntry> entries() { return List.of(); }
        public void close() { open = false; }
    }

    // Count observable FD hash work, independent of the selector's container.
    // One read-ready FD needs a registration lookup and a write-ready lookup;
    // idle registrations must not add work, nor a temporary union add hashes.
    @Test
    public void oneReadableDoesNotHashIdleFdsOrBuildAnotherSet() throws Exception {
        hashWork(false, 2);
    }

    @Test
    public void combinedReadWriteNeedsOneEntryAndNoUnionHashing() throws Exception {
        hashWork(true, 3);
    }

    private void hashWork(boolean both, int maxHashesPerPoll) throws Exception {
        for (boolean hybrid : new boolean[]{false, true}) {
            for (int count : new int[]{1, 100, 1000}) {
                try (var selector = new WrappedSelector(new EmptySelector())) {
                    Input[] inputs = new Input[count];
                    FD[] registered = new FD[count];
                    for (int i = 0; i < count; ++i) {
                        inputs[i] = new Input(i + 1);
                        registered[i] = hybrid ? new Hybrid(inputs[i]) : inputs[i];
                        selector.register(registered[i], EventSet.readwrite(), registered[i]);
                    }
                    selector.registerVirtualReadable(inputs[0]);
                    if (both) selector.registerVirtualWritable(inputs[0]);
                    for (Input input : inputs) input.hashes = 0;
                    for (int poll = 0; poll < 100; ++poll) {
                        var entries = selector.selectNow();
                        assertEquals(1, entries.size());
                        var entry = entries.iterator().next();
                        assertSame(registered[0], entry.fd());
                        assertSame(registered[0], entry.attachment());
                        assertEquals(both ? EventSet.readwrite() : EventSet.read(), entry.ready());
                    }
                    assertTrue("unnecessary hash work: " + inputs[0].hashes,
                        inputs[0].hashes <= 100 * maxHashesPerPoll);
                    for (int i = 1; i < count; ++i) assertEquals(0, inputs[i].hashes);
                }
            }
        }
    }

    @Test
    public void saturatedMixedMasksRemainLevelReadyAndRemoveCleanly() throws Exception {
        for (boolean hybrid : new boolean[]{false, true}) {
            try (var selector = new WrappedSelector(new EmptySelector())) {
                Input[] inputs = new Input[1000];
                FD[] registered = new FD[1000];
                int expected = 0;
                for (int i = 0; i < inputs.length; ++i) {
                    inputs[i] = new Input(i + 1);
                    registered[i] = hybrid ? new Hybrid(inputs[i]) : inputs[i];
                    selector.register(registered[i], i % 5 == 0 ? EventSet.none() : EventSet.readwrite(), registered[i]);
                    if (i % 2 == 0) selector.registerVirtualReadable(inputs[i]);
                    if (i % 3 == 0) selector.registerVirtualWritable(inputs[i]);
                    if (i % 5 != 0 && (i % 2 == 0 || i % 3 == 0)) ++expected;
                }
                for (int poll = 0; poll < 3; ++poll) {
                    var entries = selector.selectNow();
                    assertEquals(expected, entries.size());
                    var seen = new HashSet<FD>();
                    for (var entry : entries) {
                        assertTrue(seen.add(entry.fd()));
                        Input input = hybrid ? ((Hybrid) entry.fd()).input : (Input) entry.fd();
                        int index = input.id - 1;
                        assertSame(registered[index], entry.fd());
                        assertSame(registered[index], entry.attachment());
                        assertEquals(index % 2 == 0, entry.ready().have(Event.READABLE));
                        assertEquals(index % 3 == 0, entry.ready().have(Event.WRITABLE));
                    }
                }
                for (int i = 0; i < inputs.length; ++i) {
                    selector.remove(registered[i]);
                }
                assertTrue(selector.selectNow().isEmpty());
                assertTrue(selector.entries().isEmpty());
            }
        }
    }

    @Test
    public void writableCallbacksCanChangeRegistrationAfterSnapshot() throws Exception {
        try (var selector = new WrappedSelector(new EmptySelector())) {
            var first = new Aware(1);
            var second = new Aware(2);
            var reader = new Input(3);
            var added = new Aware(4);
            for (Input input : new Input[]{first, second, reader}) {
                selector.register(input, EventSet.readwrite(), input);
            }
            selector.registerVirtualReadable(first);
            selector.registerVirtualWritable(first);
            selector.registerVirtualWritable(second);
            selector.registerVirtualReadable(reader);
            var changed = new AtomicBoolean();
            first.action = () -> {
                if (!changed.compareAndSet(false, true)) return;
                selector.remove(second);
                try {
                    selector.register(added, EventSet.write(), added);
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                selector.registerVirtualWritable(added);
            };
            assertEquals(3, selector.selectNow().size());
            assertEquals(1, first.callbacks);
            assertEquals(1, second.callbacks);
            assertEquals(0, added.callbacks);
            var next = selector.selectNow();
            assertEquals(3, next.size());
            assertFalse(next.stream().anyMatch(e -> e.fd() == second));
            assertTrue(next.stream().anyMatch(e -> e.fd() == added));
            assertEquals(2, first.callbacks);
            assertEquals(1, second.callbacks);
            assertEquals(1, added.callbacks);
        }
    }

    @Test(timeout = 10000)
    public void readinessAndMasksCanChangeAcrossThreads() throws Exception {
        try (var selector = new WrappedSelector(new EmptySelector())) {
            Input[] inputs = new Input[100];
            for (int i = 0; i < inputs.length; ++i) {
                inputs[i] = new Input(i + 1);
                selector.register(inputs[i], EventSet.readwrite(), inputs[i]);
            }
            var failure = new AtomicReference<Throwable>();
            Thread worker = new Thread(() -> {
                try {
                    for (int i = 0; i < 10000; ++i) {
                        var input = inputs[i % inputs.length];
                        selector.registerVirtualReadable(input);
                        selector.registerVirtualWritable(input);
                        selector.modify(input, (i & 1) == 0 ? EventSet.read() : EventSet.write());
                        selector.removeVirtualReadable(input);
                        selector.removeVirtualWritable(input);
                    }
                } catch (Throwable error) {
                    failure.set(error);
                }
            });
            worker.start();
            try {
                for (int i = 0; i < 2000; ++i) {
                    var seen = new HashSet<FD>();
                    for (var entry : selector.selectNow()) {
                        assertTrue(seen.add(entry.fd()));
                        assertSame(entry.fd(), entry.attachment());
                        assertFalse(entry.ready().equals(EventSet.none()));
                    }
                }
            } finally {
                worker.join(5000);
            }
            assertFalse(worker.isAlive());
            assertNull(failure.get());
            assertTrue(selector.selectNow().isEmpty());
        }
    }
}
